"""Generate Tier B (conversational) Romanian card drafts via the Gemini API.

Reads a JSONL seed file, calls Gemini with the conversational prompt, and
writes one draft JSON per topic into ``drafts/conversational/``.

Each seed line must include at least:
    {"domain": "campfire_basics", "topic": "Aprins focul cu lemne ude",
     "topic_slug": "fire_wet_wood"}

Optional fields:
    "card_family": "SCENARIO" | "DEFINITION" | "CONSTRAINT"  (default: SCENARIO)
    "country_scope": "ro" | "global"  (default: "ro")
    "safety_tags": ["tip"]
    "priority": 0..100

Usage:
    # Dry run — writes deterministic stubs without an API call
    python tools/card_generator/conversational_gen.py --dry-run \
        --seeds tools/card_generator/seeds/conversational_topics_ro.jsonl

    # Real run — requires GEMINI_API_KEY (or GOOGLE_API_KEY)
    python tools/card_generator/conversational_gen.py \
        --seeds tools/card_generator/seeds/conversational_topics_ro.jsonl \
        --model gemini-2.5-flash \
        --batch-size 20

The script is resumable: any seed whose ``chunk_id`` already has a draft on
disk is skipped unless ``--force`` is set.

Install the SDK once: pip install google-genai
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any

from common import (
    DRAFTS_DIR,
    PROMPTS_DIR,
    SEEDS_DIR,
    chunk_id_for,
    ensure_directory,
    iter_jsonl,
    today_iso_date,
    write_json,
)
from schema import (
    TIER_B,
    TIER_B_DOMAINS,
    TIER_B_GENERATED_PUBLISHER,
    TIER_B_GENERATED_SOURCE_TITLE,
    TIER_B_TRUST,
)

DEFAULT_SYSTEM_PROMPT = (PROMPTS_DIR / "conversational_system_ro.txt").read_text(encoding="utf-8") \
    if (PROMPTS_DIR / "conversational_system_ro.txt").exists() else ""

# Default to the fast/cheap Gemini model; --model lets you swap to gemini-2.5-pro
# for harder topics or when you want a higher-quality first pass.
DEFAULT_MODEL = "gemini-2.5-flash"


def _load_gemini():
    """Import the google-genai SDK. Errors out cleanly if it isn't installed."""
    try:
        from google import genai  # type: ignore
        from google.genai import types  # type: ignore
    except ImportError as exc:
        raise SystemExit(
            "google-genai package not installed. Run: pip install google-genai\n"
            "Or use --dry-run to generate template stubs without an API call."
        ) from exc
    return genai, types


def _stub_payload(seed: dict[str, Any]) -> dict[str, Any]:
    """Deterministic stub used by --dry-run. Body is intentionally short — the
    validator will flag it. The point is to exercise file I/O, not produce
    shippable content.
    """
    topic = seed["topic"]
    return {
        "title": f"[STUB] {topic}",
        "body": (
            f"Acesta este un schelet generat în mod determinist pentru subiectul "
            f"'{topic}'. Înlocuiește conținutul cu sfatul real al unui drumeț. "
            f"Vorbește la persoana întâi plural, dă un exemplu concret, evită "
            f"generalitățile. Este suficient pentru a testa pipeline-ul, dar "
            f"validatorul ar trebui să refuze un astfel de stub într-un build real "
            f"din cauza tonului și a conținutului placeholder."
        ),
        "keywords": [seed.get("topic_slug", "stub"), "schelet", "test"],
        "card_family": seed.get("card_family", "SCENARIO"),
        "lead": f"Schelet de test pentru: {topic}",
    }


def _build_user_message(seed: dict[str, Any]) -> str:
    family_hint = seed.get("card_family", "SCENARIO")
    return (
        f"Subiect: {seed['topic']}\n"
        f"Domeniu: {seed['domain']}\n"
        f"card_family țintă: {family_hint}\n"
        f"Tonul: drumeț experimentat, prietenos, concret.\n\n"
        "Returnează JSON conform schemei descrise în system."
    )


def _extract_retry_delay(error: Exception) -> float | None:
    """Pick the server-suggested retry delay out of a 429 error message, if present."""
    text = str(error)
    match = re.search(r"retryDelay['\"]?\s*:\s*['\"]?(\d+(?:\.\d+)?)s", text)
    if match:
        return float(match.group(1))
    match = re.search(r"Please retry in (\d+(?:\.\d+)?)s", text)
    if match:
        return float(match.group(1))
    return None


def _describe_empty_response(response) -> str:
    """Extract whatever debug context we can when response.text is empty."""
    parts: list[str] = []
    finish_reason = None
    candidates = getattr(response, "candidates", None) or []
    if candidates:
        finish_reason = getattr(candidates[0], "finish_reason", None)
        if finish_reason is not None:
            parts.append(f"finish_reason={finish_reason}")
        safety = getattr(candidates[0], "safety_ratings", None)
        if safety:
            blocked = [s for s in safety if getattr(s, "blocked", False)]
            if blocked:
                parts.append(f"safety_blocked={[getattr(s, 'category', '?') for s in blocked]}")
    pf = getattr(response, "prompt_feedback", None)
    if pf is not None:
        block_reason = getattr(pf, "block_reason", None)
        if block_reason:
            parts.append(f"prompt_block_reason={block_reason}")
    usage = getattr(response, "usage_metadata", None)
    if usage is not None:
        thoughts = getattr(usage, "thoughts_token_count", None)
        output = getattr(usage, "candidates_token_count", None)
        parts.append(f"thoughts_tokens={thoughts} output_tokens={output}")
    return "; ".join(parts) if parts else "no diagnostic info"


def _call_llm(
    client,
    types,
    *,
    model: str,
    system_prompt: str,
    user_message: str,
    max_tokens: int,
    temperature: float,
    thinking_budget: int | None = None,
    max_retries: int = 5,
) -> dict[str, Any]:
    """Call Gemini with JSON response mode forced.

    Retries on 429 RESOURCE_EXHAUSTED with the server-suggested delay (or
    exponential backoff if not provided). Other errors propagate immediately.

    For thinking-enabled models (gemini-2.5-pro, -flash with thinking on),
    pass ``thinking_budget`` to cap how many tokens the model spends thinking
    before producing the JSON. Pro requires a non-zero budget; Flash accepts 0.
    """
    config_kwargs: dict[str, Any] = dict(
        system_instruction=system_prompt,
        temperature=temperature,
        max_output_tokens=max_tokens,
        response_mime_type="application/json",
    )
    if thinking_budget is not None:
        config_kwargs["thinking_config"] = types.ThinkingConfig(
            thinking_budget=thinking_budget,
        )
    config = types.GenerateContentConfig(**config_kwargs)

    last_error: Exception | None = None
    for attempt in range(max_retries):
        try:
            response = client.models.generate_content(
                model=model,
                contents=user_message,
                config=config,
            )
            raw = (response.text or "").strip()
            if not raw:
                diag = _describe_empty_response(response)
                raise RuntimeError(f"Gemini returned empty content ({diag})")
            try:
                return json.loads(raw)
            except json.JSONDecodeError as error:
                raise RuntimeError(
                    f"Gemini did not return valid JSON: {error.msg}\n---\n{raw[:600]}"
                ) from error
        except Exception as error:  # noqa: BLE001
            last_error = error
            text = str(error).lower()
            is_rate_limit = "429" in text or "resource_exhausted" in text or "quota" in text
            is_server_error = any(
                marker in text
                for marker in ("503", "500", "502", "504", "unavailable", "internal", "deadline")
            )
            retryable = is_rate_limit or is_server_error
            if not retryable or attempt == max_retries - 1:
                raise

            if is_rate_limit:
                delay = _extract_retry_delay(error) or (2 ** attempt) * 5
                delay = min(delay + 1.0, 90.0)
                label = "rate-limited"
            else:
                # Server-side hiccup. Exponential backoff with jitter.
                delay = min(3 * (2 ** attempt), 60.0)
                label = "server busy (5xx)"
            print(f"  {label}, sleeping {delay:.0f}s before retry "
                  f"(attempt {attempt + 1}/{max_retries})")
            time.sleep(delay)
    assert last_error is not None
    raise last_error


def _draft_from_llm_payload(seed: dict[str, Any], payload: dict[str, Any]) -> dict[str, Any] | None:
    if payload.get("escalate") == "tier_a":
        # Caller decides what to do — record but don't write a Tier B draft
        return None

    chunk_id = chunk_id_for(seed["domain"], seed["topic_slug"])
    metadata_json = {
        "safety_critical": False,
        "tone": "conversational",
        "lead": payload.get("lead"),
        "seed_topic": seed["topic"],
        "seed_topic_slug": seed["topic_slug"],
    }
    keywords = payload.get("keywords") or []
    if isinstance(keywords, list):
        keywords_value = " ".join(str(k).strip() for k in keywords if k)
    else:
        keywords_value = str(keywords)

    return {
        "chunk_id": chunk_id,
        "card_id": chunk_id,
        "domain": seed["domain"],
        "topic": seed.get("topic_slug") or seed["domain"],
        "language": "ro",
        "title": str(payload.get("title", "")).strip(),
        "body": str(payload.get("body", "")).strip(),
        "lead": payload.get("lead"),
        "keywords": keywords_value,
        "card_family": payload.get("card_family") or seed.get("card_family", "SCENARIO"),
        "priority": int(seed.get("priority", 0)),
        "tier": TIER_B,
        "source_title": TIER_B_GENERATED_SOURCE_TITLE,
        "source_url": None,
        "publisher": TIER_B_GENERATED_PUBLISHER,
        "source_language": "ro",
        "adapted_language": "ro",
        "publish_or_review_date": today_iso_date(),
        "source_trust": TIER_B_TRUST,
        "safety_tags": list(seed.get("safety_tags") or []),
        "country_scope": seed.get("country_scope", "ro"),
        "metadata_json": metadata_json,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate Tier B card drafts.")
    parser.add_argument("--seeds", type=Path,
                        default=SEEDS_DIR / "conversational_topics_ro.jsonl",
                        help="JSONL with topic seeds")
    parser.add_argument("--output", type=Path,
                        default=DRAFTS_DIR / "conversational",
                        help="Where to write draft JSONs")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="Gemini model id")
    parser.add_argument("--max-tokens", type=int, default=4000,
                        help=(
                            "Gemini max_output_tokens. Default 4000 leaves room for "
                            "the 2.5 Pro thinking phase + the actual JSON. Lower for "
                            "Flash if cost matters."
                        ))
    parser.add_argument("--temperature", type=float, default=0.55)
    parser.add_argument("--thinking-budget", type=int, default=None,
                        help=(
                            "Tokens the model is allowed to spend on hidden 'thinking' "
                            "before emitting the answer. Pro requires a non-zero value "
                            "(try 1024). Flash accepts 0 to disable thinking entirely. "
                            "Leave unset to use the model default."
                        ))
    parser.add_argument("--batch-size", type=int, default=10,
                        help="How many topics to process before pausing for status output")
    parser.add_argument("--limit", type=int, default=0,
                        help="Stop after N successful drafts (0 = no limit)")
    parser.add_argument("--force", action="store_true",
                        help="Overwrite existing drafts instead of skipping")
    parser.add_argument("--dry-run", action="store_true",
                        help="Skip the LLM call; write deterministic stubs instead")
    parser.add_argument("--escalate-log", type=Path, default=None,
                        help="Where to write topics the LLM flagged for Tier A escalation")
    parser.add_argument("--rpm", type=float, default=9.0,
                        help=(
                            "Target requests-per-minute. Default 9 stays under the "
                            "Gemini free-tier 10 RPM limit. Bump to 60+ if you have "
                            "a paid tier."
                        ))
    args = parser.parse_args(argv)

    if not args.seeds.exists():
        raise SystemExit(f"Seeds file not found: {args.seeds}")
    output_dir = ensure_directory(args.output)

    if not args.dry_run:
        genai, types = _load_gemini()
        api_key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
        if not api_key:
            raise SystemExit(
                "GEMINI_API_KEY (or GOOGLE_API_KEY) environment variable is not set.\n"
                "Get a key at https://aistudio.google.com/app/apikey\n"
                "Either export the key or rerun with --dry-run."
            )
        client = genai.Client(api_key=api_key)
    else:
        genai = None
        types = None
        client = None

    system_prompt = DEFAULT_SYSTEM_PROMPT or "Ești un drumeț român experimentat. Returnează JSON."
    written = 0
    skipped = 0
    failed = 0
    escalated: list[dict[str, Any]] = []

    for seed_index, seed in enumerate(iter_jsonl(args.seeds), start=1):
        if "domain" not in seed or "topic" not in seed or "topic_slug" not in seed:
            print(f"[seed {seed_index}] missing required fields; skipping: {seed}", file=sys.stderr)
            failed += 1
            continue
        if seed["domain"] not in TIER_B_DOMAINS:
            print(f"[seed {seed_index}] unknown Tier B domain {seed['domain']!r}; skipping",
                  file=sys.stderr)
            failed += 1
            continue

        chunk_id = chunk_id_for(seed["domain"], seed["topic_slug"])
        target = output_dir / f"{chunk_id}.json"

        if target.exists() and not args.force:
            skipped += 1
            continue

        try:
            if args.dry_run:
                payload = _stub_payload(seed)
            else:
                payload = _call_llm(
                    client,
                    types,
                    model=args.model,
                    system_prompt=system_prompt,
                    user_message=_build_user_message(seed),
                    max_tokens=args.max_tokens,
                    temperature=args.temperature,
                    thinking_budget=args.thinking_budget,
                )

            draft = _draft_from_llm_payload(seed, payload)
            if draft is None:
                escalated.append({
                    "seed": seed,
                    "reason": payload.get("reason") or "(no reason given)",
                })
                continue

            write_json(target, draft)
            written += 1
            if args.batch_size and written and written % args.batch_size == 0:
                print(f"  ...{written} drafts written")
            if args.limit and written >= args.limit:
                print(f"reached --limit={args.limit}, stopping")
                break

            if not args.dry_run:
                # Throttle to stay under the configured RPM. The Gemini free
                # tier on gemini-2.5-flash allows roughly 10 RPM; default 9 RPM
                # leaves 1 RPM of headroom so we don't hammer the limit.
                time.sleep(60.0 / max(args.rpm, 1.0))
        except Exception as error:  # noqa: BLE001
            print(f"[seed {seed_index} {seed.get('topic_slug')}] failed: {error}", file=sys.stderr)
            failed += 1

    if args.escalate_log and escalated:
        args.escalate_log.parent.mkdir(parents=True, exist_ok=True)
        args.escalate_log.write_text(
            "\n".join(json.dumps(item, ensure_ascii=False) for item in escalated) + "\n",
            encoding="utf-8",
        )

    print(
        f"\nwritten={written} skipped={skipped} failed={failed} escalated={len(escalated)}"
    )
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

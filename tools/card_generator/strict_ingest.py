"""Tier A draft ingest from authoritative sources.

Reads ``sources/strict/manifest.json`` plus the source files it references,
chunks each source into atomic claim windows, asks an LLM to rewrite each
window into a card draft *strictly tied to the source text*, and writes the
drafts to ``drafts/strict/<chunk_id>.json``. Every draft requires a human
reviewer before it can be moved to ``approved/strict/``.

The source files must already live in ``sources/strict/``. The recommended
flow is to run the canonical fetcher (``tools/knowledge_pipeline/fetch_sources.py``)
first, then symlink/copy the cached normalized text into ``sources/strict/``,
or just point the manifest at the cached files directly.

Usage:
    # Dry run — emits stubs without an API call (sanity-check the manifest)
    python tools/card_generator/strict_ingest.py --dry-run

    # Real run — requires GEMINI_API_KEY (or GOOGLE_API_KEY)
    python tools/card_generator/strict_ingest.py \
        --manifest tools/card_generator/sources/strict/manifest.json \
        --model gemini-2.5-pro

The LLM prompt is in ``prompts/strict_system_ro.txt`` and is intentionally
conservative: if the source doesn't directly support a topic, the model is
required to return ``{"insufficient_evidence": true, ...}`` and no draft
gets written for that window.

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
    SOURCES_DIR,
    chunk_id_for,
    ensure_directory,
    load_json,
    safe_slug,
    slurp_text,
    today_iso_date,
    write_json,
)
from schema import (
    TIER_A,
    TIER_A_DOMAINS,
    TIER_A_MIN_TRUST,
)

# Default to Pro for strict ingest — claims have to be source-faithful, so we
# eat the cost. Override with --model gemini-2.5-flash for cheaper passes.
DEFAULT_MODEL = "gemini-2.5-pro"
DEFAULT_SYSTEM_PROMPT = (PROMPTS_DIR / "strict_system_ro.txt").read_text(encoding="utf-8") \
    if (PROMPTS_DIR / "strict_system_ro.txt").exists() else ""

# Window size for source chunks (characters). Tuned so the LLM sees enough
# context to verify a claim without bloating the prompt.
SOURCE_WINDOW_CHARS = 4000
SOURCE_WINDOW_OVERLAP = 400


def _load_canonical_sources() -> dict[str, dict[str, Any]]:
    """Load the source registry from the existing knowledge_pipeline."""
    candidates = [
        SOURCES_DIR.parent.parent.parent / "tools" / "knowledge_pipeline" / "sources.json",
        SOURCES_DIR.parent.parent.parent.parent / "tools" / "knowledge_pipeline" / "sources.json",
    ]
    for candidate in candidates:
        if candidate.exists():
            payload = load_json(candidate)
            return {entry["id"]: entry for entry in payload.get("sources", [])}
    raise SystemExit(
        "Could not locate tools/knowledge_pipeline/sources.json. "
        "Tier A drafts must reference a known source_id from that registry."
    )


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


def _strip_html(raw: str) -> str:
    """Cheap HTML→text. We don't need perfection — the LLM tolerates noise."""
    raw = re.sub(r"<script[^<]*</script>", " ", raw, flags=re.I | re.S)
    raw = re.sub(r"<style[^<]*</style>", " ", raw, flags=re.I | re.S)
    raw = re.sub(r"<[^>]+>", " ", raw)
    raw = raw.replace("&nbsp;", " ").replace("&amp;", "&")
    return re.sub(r"\s+", " ", raw).strip()


def _read_source(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix in {".html", ".htm"}:
        return _strip_html(slurp_text(path))
    if suffix == ".pdf":
        try:
            from pypdf import PdfReader  # type: ignore
        except ImportError as exc:
            raise SystemExit(
                f"pypdf not installed but {path.name} is a PDF. "
                "pip install pypdf  (or pre-extract text with the existing pipeline)"
            ) from exc
        reader = PdfReader(str(path))
        return re.sub(r"\s+", " ", "\n".join(page.extract_text() or "" for page in reader.pages)).strip()
    return slurp_text(path)


def _windows(text: str, size: int = SOURCE_WINDOW_CHARS, overlap: int = SOURCE_WINDOW_OVERLAP) -> list[str]:
    if len(text) <= size:
        return [text]
    out: list[str] = []
    start = 0
    while start < len(text):
        out.append(text[start : start + size])
        if start + size >= len(text):
            break
        start += size - overlap
    return out


def _build_user_message(*, topic: str, domain: str, section_hint: str, source_window: str) -> str:
    return (
        f"Domeniu țintă: {domain}\n"
        f"Subiect dorit: {topic}\n"
        f"Indiciu de secțiune: {section_hint or '(niciunul)'}\n\n"
        "Folosește exclusiv informația din sursă (de mai jos). Dacă nu apare\n"
        "explicit, NU scrie cardul — returnează {\"insufficient_evidence\": true, ...}.\n"
        "Dacă apare doar parțial, semnalează prin evidence_confidence='low' și\n"
        "rămâi conservator.\n\n"
        f"--- SURSĂ ---\n{source_window}\n--- /SURSĂ ---"
    )


def _stub_payload(topic: str) -> dict[str, Any]:
    return {
        "title": f"[STUB strict] {topic[:60]}",
        "body": (
            "Acesta este un schelet generat în mod determinist pentru testarea "
            "pipeline-ului strict. Înlocuiește conținutul cu rescrierea reală a "
            "sursei autoritative. Cardul ar trebui să includă pașii principali, "
            "criteriile de gravitate și mențiunea pentru 0SALVAMONT/112 atunci "
            "când e cazul. Validatorul ar trebui să refuze acest stub din cauza "
            "lungimii și a tonului placeholder, dar îl emitem aici pentru a "
            "valida fluxul I/O al ingestului strict end-to-end fără un apel API."
        ),
        "keywords": ["test", "strict", safe_slug(topic)[:24]],
        "card_family": "SCENARIO",
        "lead": f"Schelet strict pentru: {topic}",
        "safety_tags": ["test"],
        "source_quote": "(stub — niciun citat real)",
        "evidence_confidence": "low",
    }


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
    exponential backoff if not provided) and on transient 5xx errors.
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
            if raw.startswith("```"):
                raw = raw.strip("`")
                if raw.startswith("json"):
                    raw = raw[4:].strip()
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
                delay = min(3 * (2 ** attempt), 60.0)
                label = "server busy (5xx)"
            print(f"  {label}, sleeping {delay:.0f}s before retry "
                  f"(attempt {attempt + 1}/{max_retries})")
            time.sleep(delay)
    assert last_error is not None
    raise last_error


def _draft_from_llm_payload(
    *,
    topic: str,
    domain: str,
    source_entry: dict[str, Any],
    payload: dict[str, Any],
) -> dict[str, Any] | None:
    if payload.get("insufficient_evidence"):
        return None

    topic_slug = safe_slug(topic) or "topic"
    chunk_id = chunk_id_for(domain, topic_slug)
    metadata_json = {
        "safety_critical": True,
        "tone": "strict",
        "lead": payload.get("lead"),
        "evidence_confidence": payload.get("evidence_confidence", "medium"),
        "source_quote": payload.get("source_quote"),
        "source_id": source_entry["id"],
    }
    keywords = payload.get("keywords") or []
    keywords_value = " ".join(str(k).strip() for k in keywords if k) if isinstance(keywords, list) else str(keywords)

    return {
        "chunk_id": chunk_id,
        "card_id": chunk_id,
        "domain": domain,
        "topic": topic_slug,
        "language": "ro",
        "title": str(payload.get("title", "")).strip(),
        "body": str(payload.get("body", "")).strip(),
        "lead": payload.get("lead"),
        "keywords": keywords_value,
        "card_family": payload.get("card_family") or "SCENARIO",
        "priority": 0,
        "tier": TIER_A,
        "source_title": source_entry["title"],
        "source_url": source_entry.get("url"),
        "publisher": source_entry["publisher"],
        "source_language": source_entry.get("source_language", "ro"),
        "adapted_language": "ro",
        "publish_or_review_date": today_iso_date(),
        "source_trust": max(TIER_A_MIN_TRUST, int(source_entry.get("source_trust", TIER_A_MIN_TRUST))),
        "safety_tags": list(payload.get("safety_tags") or []),
        "country_scope": source_entry.get("country_scope", "ro"),
        "metadata_json": metadata_json,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Tier A strict ingest.")
    parser.add_argument("--manifest", type=Path,
                        default=SOURCES_DIR / "strict" / "manifest.json")
    parser.add_argument("--output", type=Path,
                        default=DRAFTS_DIR / "strict")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="Gemini model id")
    parser.add_argument("--max-tokens", type=int, default=4000,
                        help=(
                            "Gemini max_output_tokens. Default 4000 leaves headroom for "
                            "the 2.5 Pro thinking phase plus the JSON body."
                        ))
    parser.add_argument("--temperature", type=float, default=0.2)
    parser.add_argument("--thinking-budget", type=int, default=None,
                        help=(
                            "Tokens the model is allowed to spend on hidden 'thinking' "
                            "before emitting the answer. Pro requires a non-zero value "
                            "(try 1024). Flash accepts 0 to disable thinking entirely. "
                            "Leave unset to use the model default."
                        ))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--limit", type=int, default=0,
                        help="Stop after N successful drafts (0 = no limit)")
    parser.add_argument("--rpm", type=float, default=4.0,
                        help=(
                            "Target requests-per-minute. Default 4 stays under the "
                            "Gemini free-tier 5 RPM limit for gemini-2.5-pro. Bump to "
                            "9 for Flash, or 60+ if you have a paid tier."
                        ))
    args = parser.parse_args(argv)

    if not args.manifest.exists():
        raise SystemExit(
            f"Manifest not found: {args.manifest}\n"
            f"Copy {SOURCES_DIR / 'strict' / 'manifest.example.json'} to that path and edit."
        )

    output_dir = ensure_directory(args.output)
    sources = _load_canonical_sources()
    manifest = load_json(args.manifest)

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

    system_prompt = DEFAULT_SYSTEM_PROMPT or "Rolul tău: editor de conținut Tier A. Returnează JSON."
    written = 0
    skipped = 0
    insufficient = 0
    failed = 0

    for entry_index, entry in enumerate(manifest.get("files", []), start=1):
        source_id = entry.get("source_id")
        if not source_id or source_id not in sources:
            print(f"[entry {entry_index}] unknown source_id {source_id!r}; skipping", file=sys.stderr)
            failed += 1
            continue
        domain = entry.get("domain")
        if domain not in TIER_A_DOMAINS:
            print(f"[entry {entry_index}] domain {domain!r} is not Tier A; skipping", file=sys.stderr)
            failed += 1
            continue

        source_path = (args.manifest.parent / entry["path"]).resolve()
        if not source_path.exists():
            print(f"[entry {entry_index}] source file missing: {source_path}", file=sys.stderr)
            failed += 1
            continue

        text = _read_source(source_path)
        windows = _windows(text)
        section_hint = entry.get("section_hint", "")
        topics = entry.get("topics") or []
        source_entry = sources[source_id]

        for topic in topics:
            topic_slug = safe_slug(topic) or "topic"
            target = output_dir / f"{chunk_id_for(domain, topic_slug)}.json"
            if target.exists() and not args.force:
                skipped += 1
                continue

            try:
                if args.dry_run:
                    payload = _stub_payload(topic)
                else:
                    # Iterate windows until one yields a draft (or all say insufficient)
                    payload = None
                    per_call_sleep = 60.0 / max(args.rpm, 1.0)
                    for window in windows:
                        candidate = _call_llm(
                            client,
                            types,
                            model=args.model,
                            system_prompt=system_prompt,
                            user_message=_build_user_message(
                                topic=topic, domain=domain,
                                section_hint=section_hint,
                                source_window=window,
                            ),
                            max_tokens=args.max_tokens,
                            temperature=args.temperature,
                            thinking_budget=args.thinking_budget,
                        )
                        if not candidate.get("insufficient_evidence"):
                            payload = candidate
                            break
                        time.sleep(per_call_sleep)
                    if payload is None:
                        insufficient += 1
                        continue

                draft = _draft_from_llm_payload(
                    topic=topic, domain=domain,
                    source_entry=source_entry, payload=payload,
                )
                if draft is None:
                    insufficient += 1
                    continue
                write_json(target, draft)
                written += 1
                if args.limit and written >= args.limit:
                    print(f"reached --limit={args.limit}, stopping")
                    break
                if not args.dry_run:
                    # Throttle to stay under the configured RPM.
                    time.sleep(60.0 / max(args.rpm, 1.0))
            except Exception as error:  # noqa: BLE001
                print(f"[entry {entry_index} '{topic}'] failed: {error}", file=sys.stderr)
                failed += 1

        if args.limit and written >= args.limit:
            break

    print(
        f"\nwritten={written} skipped={skipped} insufficient={insufficient} failed={failed}"
    )
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

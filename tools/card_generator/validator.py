"""Validate card drafts before they are approved for the build.

Usage:
    python tools/card_generator/validator.py \
        --tier B \
        --input tools/card_generator/drafts/conversational

    python tools/card_generator/validator.py \
        --tier A \
        --input tools/card_generator/drafts/strict \
        --json-report build/validation_report.json

Exit codes:
    0 — all drafts pass
    1 — one or more drafts have errors

Warnings do NOT cause non-zero exit; they're informational.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

from common import (
    list_json_files,
    load_existing_chunk_ids,
    load_existing_titles_and_bodies,
    looks_like_romanian,
    normalize_whitespace,
    sha256_text,
)
from schema import (
    ALLOWED_KEYS,
    ALL_DOMAINS,
    FORBIDDEN_PATTERNS,
    REQUIRED_KEYS,
    TIER_A,
    TIER_A_BODY_MAX_WORDS,
    TIER_A_BODY_MIN_WORDS,
    TIER_A_DOMAINS,
    TIER_A_MIN_TRUST,
    TIER_B,
    TIER_B_BODY_MAX_WORDS,
    TIER_B_BODY_MIN_WORDS,
    TIER_B_DOMAINS,
    TIER_B_FORBIDDEN_PHRASES,
    TIER_B_GENERATED_PUBLISHER,
    TIER_B_GENERATED_SOURCE_TITLE,
    TIER_B_TRUST,
    VALID_CARD_FAMILIES,
    VALID_TIERS,
    ValidationIssue,
    is_known_domain,
    normalize_metadata_json,
    word_count,
)


def _add(issues: list[ValidationIssue], chunk_id: str, severity: str, field: str, message: str) -> None:
    issues.append(ValidationIssue(chunk_id=chunk_id, severity=severity, field=field, message=message))


def validate_draft(
    draft: dict[str, Any],
    *,
    expected_tier: str | None = None,
    existing_chunk_ids: set[str] | None = None,
    existing_title_hashes: set[str] | None = None,
    existing_body_hashes: set[str] | None = None,
) -> list[ValidationIssue]:
    issues: list[ValidationIssue] = []
    chunk_id = str(draft.get("chunk_id") or "<unknown>")

    # 1. Required keys
    missing = REQUIRED_KEYS - draft.keys()
    for key in sorted(missing):
        _add(issues, chunk_id, "error", key, f"missing required key '{key}'")

    # Unknown keys are warnings, not errors — we may evolve the schema
    unexpected = set(draft.keys()) - ALLOWED_KEYS
    for key in sorted(unexpected):
        _add(issues, chunk_id, "warn", key, f"unknown key '{key}' will be ignored by the builder")

    if missing:
        return issues  # Don't run further checks against an incomplete draft

    # 2. Tier
    tier = draft.get("tier")
    if tier not in VALID_TIERS:
        _add(issues, chunk_id, "error", "tier", f"tier must be one of {sorted(VALID_TIERS)}, got {tier!r}")
        return issues
    if expected_tier and tier != expected_tier:
        _add(issues, chunk_id, "error", "tier", f"expected tier {expected_tier}, draft says {tier}")

    # 3. Domain
    domain = draft.get("domain")
    if not is_known_domain(domain):
        _add(issues, chunk_id, "error", "domain", f"unknown domain {domain!r}")
    else:
        if tier == TIER_A and domain not in TIER_A_DOMAINS:
            _add(issues, chunk_id, "error", "domain", f"domain {domain} is not authorized for Tier A")
        if tier == TIER_B and domain not in TIER_B_DOMAINS:
            _add(issues, chunk_id, "error", "domain", f"domain {domain} is not authorized for Tier B")

    # 4. Language
    language = draft.get("language")
    if language != "ro":
        _add(issues, chunk_id, "error", "language", f"language must be 'ro' for this workstream (got {language!r})")
    if draft.get("adapted_language") != "ro":
        _add(issues, chunk_id, "error", "adapted_language", "adapted_language must be 'ro'")

    # 5. Title
    title = (draft.get("title") or "").strip()
    if not title:
        _add(issues, chunk_id, "error", "title", "title is empty")
    elif len(title) >= 80:
        _add(issues, chunk_id, "error", "title", f"title must be < 80 chars (got {len(title)})")
    elif len(title) < 8:
        _add(issues, chunk_id, "warn", "title", f"title is very short ({len(title)} chars)")

    # 6. Body
    body = (draft.get("body") or "").strip()
    body_words = word_count(body)
    if not body:
        _add(issues, chunk_id, "error", "body", "body is empty")
    elif tier == TIER_A:
        if body_words < TIER_A_BODY_MIN_WORDS or body_words > TIER_A_BODY_MAX_WORDS:
            _add(
                issues, chunk_id, "error", "body",
                f"Tier A body must be {TIER_A_BODY_MIN_WORDS}-{TIER_A_BODY_MAX_WORDS} words (got {body_words})",
            )
    elif tier == TIER_B:
        if body_words < TIER_B_BODY_MIN_WORDS or body_words > TIER_B_BODY_MAX_WORDS:
            _add(
                issues, chunk_id, "error", "body",
                f"Tier B body must be {TIER_B_BODY_MIN_WORDS}-{TIER_B_BODY_MAX_WORDS} words (got {body_words})",
            )

    # 7. Romanian-language sanity
    sample = f"{title} {body}"
    if sample.strip() and not looks_like_romanian(sample):
        _add(
            issues, chunk_id, "warn", "language",
            "no Romanian diacritics or stop words detected — verify the text is actually Romanian",
        )

    # 8. Tier A source rigor
    if tier == TIER_A:
        trust = draft.get("source_trust", 0)
        if not isinstance(trust, int) or trust < TIER_A_MIN_TRUST:
            _add(issues, chunk_id, "error", "source_trust", f"Tier A requires source_trust >= {TIER_A_MIN_TRUST} (got {trust!r})")
        if not draft.get("source_url"):
            _add(issues, chunk_id, "error", "source_url", "Tier A drafts must cite source_url")
        if not draft.get("publish_or_review_date"):
            _add(issues, chunk_id, "error", "publish_or_review_date", "Tier A requires publish_or_review_date")
        if not draft.get("source_title"):
            _add(issues, chunk_id, "error", "source_title", "Tier A requires source_title")
        if not draft.get("publisher"):
            _add(issues, chunk_id, "error", "publisher", "Tier A requires publisher")
        metadata = normalize_metadata_json(draft.get("metadata_json"))
        if metadata.get("safety_critical") is not True:
            _add(
                issues, chunk_id, "error", "metadata_json",
                "Tier A drafts must have metadata_json.safety_critical == true",
            )
        if metadata.get("tone") not in (None, "strict"):
            _add(
                issues, chunk_id, "warn", "metadata_json",
                f"Tier A tone should be 'strict' (got {metadata.get('tone')!r})",
            )
        # Forbidden voice in Tier A
        body_lower = body.lower()
        for phrase in (" haide ", " hai să ", " merită să încerci", " știi cum "):
            if phrase in f" {body_lower} ":
                _add(issues, chunk_id, "warn", "body",
                     f"Tier A draft uses conversational phrase {phrase.strip()!r} — consider rewriting strict")
                break

    # 9. Tier B authoring rigor
    if tier == TIER_B:
        trust = draft.get("source_trust")
        if trust != TIER_B_TRUST:
            _add(issues, chunk_id, "error", "source_trust", f"Tier B must have source_trust == {TIER_B_TRUST} (got {trust!r})")
        if (draft.get("source_title") or "").strip().lower() != TIER_B_GENERATED_SOURCE_TITLE.lower():
            _add(
                issues, chunk_id, "error", "source_title",
                f"Tier B source_title must be {TIER_B_GENERATED_SOURCE_TITLE!r}",
            )
        if (draft.get("publisher") or "").strip() != TIER_B_GENERATED_PUBLISHER:
            _add(
                issues, chunk_id, "error", "publisher",
                f"Tier B publisher must be {TIER_B_GENERATED_PUBLISHER!r}",
            )
        metadata = normalize_metadata_json(draft.get("metadata_json"))
        if metadata.get("safety_critical") is not False:
            _add(
                issues, chunk_id, "error", "metadata_json",
                "Tier B metadata_json.safety_critical must be false",
            )
        if metadata.get("tone") != "conversational":
            _add(
                issues, chunk_id, "error", "metadata_json",
                f"Tier B metadata_json.tone must be 'conversational' (got {metadata.get('tone')!r})",
            )
        # Forbidden weight-bearing-safety phrases
        body_lower = body.lower()
        for phrase in TIER_B_FORBIDDEN_PHRASES:
            if phrase in body_lower:
                _add(
                    issues, chunk_id, "error", "body",
                    f"Tier B draft contains forbidden discouragement phrase: {phrase!r}",
                )

    # 10. Card family
    family = draft.get("card_family")
    if family not in VALID_CARD_FAMILIES:
        _add(issues, chunk_id, "error", "card_family",
             f"card_family must be one of {sorted(f or 'null' for f in VALID_CARD_FAMILIES)}")

    # 11. Safety tags must be a list (possibly empty)
    if not isinstance(draft.get("safety_tags", []), list):
        _add(issues, chunk_id, "error", "safety_tags", "safety_tags must be a list")

    # 12. Keywords must be a string OR a list (builder will normalize)
    keywords = draft.get("keywords")
    if not (isinstance(keywords, (str, list))):
        _add(issues, chunk_id, "error", "keywords", "keywords must be a string or a list of strings")

    # 13. PII / forbidden patterns
    # Strip public emergency / rescue numbers before running the PII regexes.
    # These are safe to include in card text and should not be flagged.
    EMERGENCY_NUMBERS_RE = re.compile(
        r"\b("
        r"0725826668"      # 0SALVAMONT short number digits
        r"|0SALVAMONT"
        r"|112"
        r"|113"
        r")\b",
        flags=re.IGNORECASE,
    )
    pii_haystack = EMERGENCY_NUMBERS_RE.sub(" ", f"{title}\n{body}")
    for pattern in FORBIDDEN_PATTERNS:
        if re.search(pattern, pii_haystack, flags=re.IGNORECASE):
            _add(issues, chunk_id, "error", "body", f"text matches forbidden pattern /{pattern}/ — likely PII or political")

    # 14. Country scope
    scope = draft.get("country_scope")
    if scope not in {"ro", "global"}:
        _add(issues, chunk_id, "error", "country_scope",
             f"country_scope must be 'ro' or 'global' (got {scope!r})")

    # 15. Date format (lenient: just YYYY-MM-DD prefix)
    date_value = draft.get("publish_or_review_date") or ""
    if date_value and not re.match(r"^\d{4}-\d{2}-\d{2}", date_value):
        _add(issues, chunk_id, "error", "publish_or_review_date",
             f"publish_or_review_date must start with YYYY-MM-DD (got {date_value!r})")

    # 16. Dedup against existing pack
    if existing_chunk_ids and chunk_id in existing_chunk_ids:
        _add(issues, chunk_id, "error", "chunk_id",
             f"chunk_id already exists in shipped pack — choose a different one")
    if title and existing_title_hashes:
        if sha256_text(normalize_whitespace(title.lower())) in existing_title_hashes:
            _add(issues, chunk_id, "warn", "title",
                 "exact title already exists in shipped pack — verify this is not a duplicate")
    if body and existing_body_hashes:
        if sha256_text(normalize_whitespace(body.lower())[:160]) in existing_body_hashes:
            _add(issues, chunk_id, "warn", "body",
                 "first 160 chars of body match an existing pack chunk — verify this is not a duplicate")

    return issues


def validate_directory(directory: Path, expected_tier: str | None) -> tuple[list[dict[str, Any]], int, int]:
    files = list_json_files(directory)
    existing_chunk_ids = load_existing_chunk_ids()
    existing_title_hashes, existing_body_hashes = load_existing_titles_and_bodies()
    seen_chunk_ids: set[str] = set()

    report_rows: list[dict[str, Any]] = []
    error_count = 0
    warn_count = 0

    for path in files:
        try:
            draft = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            report_rows.append({
                "file": str(path),
                "issues": [{
                    "chunk_id": path.stem,
                    "severity": "error",
                    "field": "<file>",
                    "message": f"invalid JSON: {error.msg}",
                }],
            })
            error_count += 1
            continue

        issues = validate_draft(
            draft,
            expected_tier=expected_tier,
            existing_chunk_ids=existing_chunk_ids,
            existing_title_hashes=existing_title_hashes,
            existing_body_hashes=existing_body_hashes,
        )

        # In-batch dedup
        chunk_id = draft.get("chunk_id")
        if chunk_id:
            if chunk_id in seen_chunk_ids:
                issues.append(ValidationIssue(
                    chunk_id=chunk_id, severity="error", field="chunk_id",
                    message="duplicate chunk_id within this batch",
                ))
            else:
                seen_chunk_ids.add(chunk_id)

        for issue in issues:
            if issue.severity == "error":
                error_count += 1
            else:
                warn_count += 1

        report_rows.append({
            "file": str(path),
            "issues": [issue.to_dict() for issue in issues],
        })

    return report_rows, error_count, warn_count


def render_human_report(report_rows: list[dict[str, Any]]) -> str:
    lines: list[str] = []
    for row in report_rows:
        if not row["issues"]:
            lines.append(f"OK  {row['file']}")
            continue
        lines.append(f"--- {row['file']}")
        for issue in row["issues"]:
            lines.append(f"  [{issue['severity']:>5}] {issue['field']}: {issue['message']}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate card_generator drafts.")
    parser.add_argument("--tier", choices=sorted(VALID_TIERS) + ["any"], default="any",
                        help="Expected tier for all drafts in --input")
    parser.add_argument("--input", type=Path, required=True,
                        help="Directory containing draft *.json files")
    parser.add_argument("--json-report", type=Path,
                        help="Optional path for a structured JSON report")
    parser.add_argument("--quiet", action="store_true", help="Suppress per-file OK lines")
    args = parser.parse_args(argv)

    expected_tier = None if args.tier == "any" else args.tier
    report_rows, errors, warnings = validate_directory(args.input, expected_tier)

    if args.json_report:
        args.json_report.parent.mkdir(parents=True, exist_ok=True)
        args.json_report.write_text(
            json.dumps({
                "input_directory": str(args.input),
                "expected_tier": expected_tier,
                "drafts": report_rows,
                "summary": {
                    "files": len(report_rows),
                    "errors": errors,
                    "warnings": warnings,
                },
            }, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )

    if not args.quiet:
        text = render_human_report(report_rows)
        if text:
            print(text)
    print(f"\nfiles={len(report_rows)} errors={errors} warnings={warnings}")
    return 0 if errors == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

"""Approved-draft schema for the card_generator pipeline.

Every approved draft is one JSON file (`approved/{tier}/<chunk_id>.json`) shaped
like a row that the canonical builder will INSERT into `knowledge_chunks`.
The schema deliberately mirrors `expand_curated_chunks` and
`expand_campfire_cards` in `tools/knowledge_pipeline/build_knowledge_pack.py`
so no new column logic is needed.

The builder reads each file, validates against `validate_draft`, and merges
into the build alongside curated chunks, campfire cards, and route chunks.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any

TIER_A = "A"
TIER_B = "B"

VALID_TIERS = {TIER_A, TIER_B}

# Tier A — strict, sourced, life-safety
TIER_A_DOMAINS = {
    "medical_emergency",
    "mountain_safety",
    # Subset-only — validators check safety_tags / metadata to disambiguate
    "wildlife_romania",
    "weather_and_season",
}

# Tier B — conversational, LLM-authored
TIER_B_DOMAINS = {
    "campfire_basics",
    "gear_and_preparation",
    "survival_basics",
    "wildlife_romania",
    "weather_and_season",
    "route_intelligence_romania",
    "trail_culture_ro",
    "tips_and_tricks",
    "motivation_and_morale",
}

ALL_DOMAINS = TIER_A_DOMAINS | TIER_B_DOMAINS

VALID_CARD_FAMILIES = {"SCENARIO", "DEFINITION", "CONSTRAINT", None}

REQUIRED_KEYS = {
    "chunk_id",
    "domain",
    "topic",
    "language",
    "title",
    "body",
    "source_title",
    "publisher",
    "source_language",
    "adapted_language",
    "publish_or_review_date",
    "source_trust",
    "safety_tags",
    "country_scope",
    "keywords",
    "metadata_json",
    "tier",
}

OPTIONAL_KEYS = {
    "source_url",
    "card_family",
    "priority",
    "card_id",          # for embedding rows; defaults to chunk_id when missing
    "lead",             # short summary used as query embedding bias (Tier B)
    "user_phrasings",   # list[str] — synthetic user queries for retrieval bias
    "actions_now",      # list[str] — for SCENARIO cards
    "avoid",            # list[str]
    "watch_for",        # list[str]
    "follow_up_questions",  # list[{"question": str}]
    "term",             # for DEFINITION cards
    "plain_language_definition",  # for DEFINITION cards
    "intent_group",     # list[str]
    "slot_constraints", # dict
    "related_cards",    # list[str]
    "constraint_mode",  # "support" | ...
}

ALLOWED_KEYS = REQUIRED_KEYS | OPTIONAL_KEYS

TIER_A_BODY_MIN_WORDS = 80
TIER_A_BODY_MAX_WORDS = 300
TIER_B_BODY_MIN_WORDS = 40
TIER_B_BODY_MAX_WORDS = 180

TIER_A_MIN_TRUST = 4
TIER_B_TRUST = 2

TIER_B_GENERATED_PUBLISHER = "Scouty Knowledge Team"
TIER_B_GENERATED_SOURCE_TITLE = "Generated"

# Phrases that should never appear in any draft (light PII / political filter)
FORBIDDEN_PATTERNS = (
    r"\b\d{13}\b",                     # CNP (Romanian national ID)
    r"\b\d{3}[- ]?\d{3}[- ]?\d{4}\b",  # phone numbers
    r"\bP[SD]L\b|\bUSR\b|\bAUR\b",     # major Romanian parties
    r"\b\w+@\w+\.\w+\b",               # email addresses
)

# Tier B "weight-bearing safety" phrases — Tier B cards must NOT use these.
# If a Tier B draft hits these, it should be rewritten or escalated to Tier A.
TIER_B_FORBIDDEN_PHRASES = (
    "nu suna la 112",
    "nu chema salvamont",
    "nu mai aștepta ajutor",
    "nu mai chema ajutor",
    "în loc să suni",
    "în loc să chemi",
    "te poți trata singur",
    "nu ai nevoie de doctor",
    "ignoră simptomele",
)


@dataclass
class ValidationIssue:
    chunk_id: str
    severity: str  # "error" | "warn"
    field: str
    message: str

    def to_dict(self) -> dict[str, str]:
        return {
            "chunk_id": self.chunk_id,
            "severity": self.severity,
            "field": self.field,
            "message": self.message,
        }


def word_count(text: str) -> int:
    return len([token for token in text.split() if token.strip()])


def is_known_domain(domain: str) -> bool:
    return domain in ALL_DOMAINS


def expected_tier_for_domain(domain: str) -> str | None:
    """Return the *primary* tier for a domain. Some domains exist in both."""
    if domain in TIER_A_DOMAINS and domain in TIER_B_DOMAINS:
        return None  # caller must disambiguate via safety_tags / metadata
    if domain in TIER_A_DOMAINS:
        return TIER_A
    if domain in TIER_B_DOMAINS:
        return TIER_B
    return None


def normalize_metadata_json(value: Any) -> dict[str, Any]:
    """Coerce metadata_json to a dict. Accepts dict or JSON string."""
    if value is None:
        return {}
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        import json
        try:
            parsed = json.loads(value)
            if isinstance(parsed, dict):
                return parsed
        except json.JSONDecodeError:
            pass
    return {}

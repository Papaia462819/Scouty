"""Shared utilities for the card_generator pipeline."""
from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

PIPELINE_DIR = Path(__file__).resolve().parent
DRAFTS_DIR = PIPELINE_DIR / "drafts"
APPROVED_DIR = PIPELINE_DIR / "approved"
SEEDS_DIR = PIPELINE_DIR / "seeds"
REGRESSION_DIR = PIPELINE_DIR / "regression"
SOURCES_DIR = PIPELINE_DIR / "sources"
PROMPTS_DIR = PIPELINE_DIR / "prompts"


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def today_iso_date() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def ensure_directory(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    ensure_directory(path.parent)
    path.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=False) + "\n",
        encoding="utf-8",
    )


def iter_jsonl(path: Path) -> Iterable[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, start=1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            try:
                yield json.loads(line)
            except json.JSONDecodeError as error:
                raise ValueError(
                    f"{path.name}:{line_no} invalid JSON: {error.msg}"
                ) from error


def safe_slug(value: str) -> str:
    folded = unicodedata.normalize("NFKD", value.lower())
    folded = "".join(ch for ch in folded if not unicodedata.combining(ch))
    folded = re.sub(r"[^a-z0-9]+", "_", folded)
    return folded.strip("_")


def chunk_id_for(domain: str, topic_slug: str, language: str = "ro") -> str:
    """Deterministic chunk_id so re-runs don't churn."""
    base = f"{domain}::{topic_slug}::{language}"
    digest = hashlib.sha256(base.encode("utf-8")).hexdigest()[:12]
    return f"cg_{safe_slug(domain)[:18]}_{safe_slug(topic_slug)[:24]}_{digest}"


def normalize_whitespace(value: str) -> str:
    value = value.replace("\xa0", " ")
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def looks_like_romanian(text: str) -> bool:
    """Cheap language hint — looks for Romanian diacritics or stop words."""
    if not text:
        return False
    if any(ch in text for ch in "ăâîșțĂÂÎȘȚ"):
        return True
    lowered = text.lower()
    ro_markers = (
        " și ", " sau ", " este ", " pentru ", " care ",
        " când ", " după ", " înainte ",
        # Words that are also valid English are excluded — we want positive signal.
    )
    return any(marker in f" {lowered} " for marker in ro_markers)


def slurp_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def write_text(path: Path, content: str) -> None:
    ensure_directory(path.parent)
    path.write_text(content, encoding="utf-8")


def load_existing_chunk_ids() -> set[str]:
    """Best-effort dedup: load chunk_ids from the shipped knowledge_pack.sqlite.

    Used by the validator to flag drafts whose chunk_id already exists.
    """
    asset_db = (
        PIPELINE_DIR.parent.parent
        / "app" / "src" / "main" / "scouty_assets" / "knowledge_pack.sqlite"
    )
    if not asset_db.exists():
        return set()

    import sqlite3
    try:
        connection = sqlite3.connect(f"file:{asset_db}?mode=ro", uri=True)
    except sqlite3.OperationalError:
        return set()

    try:
        cursor = connection.execute("SELECT chunk_id FROM knowledge_chunks")
        return {row[0] for row in cursor.fetchall()}
    except sqlite3.DatabaseError:
        return set()
    finally:
        connection.close()


def load_existing_titles_and_bodies() -> tuple[set[str], set[str]]:
    """Hashes of (lowered, whitespace-normalized) titles + first 160 chars of body."""
    asset_db = (
        PIPELINE_DIR.parent.parent
        / "app" / "src" / "main" / "scouty_assets" / "knowledge_pack.sqlite"
    )
    if not asset_db.exists():
        return set(), set()

    import sqlite3
    try:
        connection = sqlite3.connect(f"file:{asset_db}?mode=ro", uri=True)
    except sqlite3.OperationalError:
        return set(), set()

    titles: set[str] = set()
    bodies: set[str] = set()
    try:
        for title, body in connection.execute(
            "SELECT title, body FROM knowledge_chunks"
        ):
            if title:
                titles.add(sha256_text(normalize_whitespace(title.lower())))
            if body:
                bodies.add(sha256_text(normalize_whitespace(body.lower())[:160]))
    except sqlite3.DatabaseError:
        return set(), set()
    finally:
        connection.close()
    return titles, bodies


def list_json_files(directory: Path) -> list[Path]:
    if not directory.exists():
        return []
    return sorted(p for p in directory.glob("*.json") if p.is_file())

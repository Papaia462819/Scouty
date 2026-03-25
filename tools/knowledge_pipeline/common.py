from __future__ import annotations

import hashlib
import html
import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from typing import Any


ROOT_DIR = Path(__file__).resolve().parents[2]
PIPELINE_DIR = Path(__file__).resolve().parent
CACHE_DIR = PIPELINE_DIR / "cache"
BUILD_DIR = PIPELINE_DIR / "build"
SOURCE_REGISTRY_PATH = PIPELINE_DIR / "sources.json"
CURATED_CHUNKS_PATH = PIPELINE_DIR / "curated_chunks.json"
PACK_VERSION_PATH = PIPELINE_DIR / "version.txt"
ASSETS_DIR = ROOT_DIR / "app" / "src" / "main" / "scouty_assets"
ROUTE_CATALOG_PATH = ASSETS_DIR / "local_route_enriched_catalog.json"
ROUTE_GEOMETRY_PATH = ASSETS_DIR / "local_route_geometry_index.json"
FETCH_MANIFEST_PATH = CACHE_DIR / "fetch_manifest.json"


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


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def load_pack_version() -> str:
    return PACK_VERSION_PATH.read_text(encoding="utf-8").strip()


def load_source_registry() -> list[dict[str, Any]]:
    return load_json(SOURCE_REGISTRY_PATH)["sources"]


def load_curated_chunk_specs() -> list[dict[str, Any]]:
    return load_json(CURATED_CHUNKS_PATH)["chunk_specs"]


def normalize_whitespace(value: str) -> str:
    value = html.unescape(value)
    value = value.replace("\xa0", " ")
    value = re.sub(r"\s+", " ", value)
    return value.strip()


class _SimpleHtmlTextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self._skip_depth = 0
        self._parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in {"script", "style", "noscript"}:
            self._skip_depth += 1
            return
        if tag in {"p", "div", "section", "article", "li", "br", "h1", "h2", "h3", "h4"}:
            self._parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in {"script", "style", "noscript"} and self._skip_depth > 0:
            self._skip_depth -= 1
            return
        if tag in {"p", "div", "section", "article", "li", "br", "h1", "h2", "h3", "h4"}:
            self._parts.append("\n")

    def handle_data(self, data: str) -> None:
        if self._skip_depth == 0:
            self._parts.append(data)

    def text(self) -> str:
        return normalize_whitespace(" ".join(self._parts))


def extract_text_from_html(raw_html: str) -> str:
    extractor = _SimpleHtmlTextExtractor()
    extractor.feed(raw_html)
    return extractor.text()


def extract_text_from_pdf(path: Path) -> str | None:
    try:
        from pypdf import PdfReader  # type: ignore
    except Exception:
        return None

    try:
        reader = PdfReader(str(path))
    except Exception:
        return None

    pages = []
    for page in reader.pages:
        page_text = page.extract_text() or ""
        if page_text.strip():
            pages.append(page_text)
    return normalize_whitespace("\n".join(pages)) or None


def clip_text(value: str | None, limit: int = 320) -> str | None:
    if not value:
        return None
    normalized = normalize_whitespace(value)
    if len(normalized) <= limit:
        return normalized
    clipped = normalized[:limit].rsplit(" ", 1)[0].strip()
    return f"{clipped}..."


def marker_label(symbols: list[str]) -> str | None:
    labels = []
    for marker in symbols:
        parts = marker.split(":")
        if len(parts) < 3:
            labels.append(marker.replace("_", " "))
            continue
        color = parts[0].lower()
        shape = parts[2].lower()
        if "stripe" in shape:
            shape_label = "banda"
        elif "triangle" in shape:
            shape_label = "triunghi"
        elif "dot" in shape:
            shape_label = "punct"
        elif "cross" in shape:
            shape_label = "cruce"
        elif "circle" in shape:
            shape_label = "cerc"
        else:
            shape_label = shape.replace("_", " ")
        labels.append(f"{shape_label} {color}")
    deduped = []
    for label in labels:
        if label not in deduped:
            deduped.append(label)
    return " / ".join(deduped) if deduped else None


def safe_slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


@dataclass(frozen=True)
class LocalSourceInfo:
    source_title: str
    source_url: str | None
    publisher: str
    source_trust: int

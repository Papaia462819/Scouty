from __future__ import annotations

import hashlib
import html
import json
import math
import re
import struct
import unicodedata
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
KNOW_HOW_CAMPFIRE_PATH = PIPELINE_DIR / "campfire_cards.json"
PACK_VERSION_PATH = PIPELINE_DIR / "version.txt"
ASSETS_DIR = ROOT_DIR / "app" / "src" / "main" / "scouty_assets"
ROUTE_CATALOG_PATH = ASSETS_DIR / "local_route_enriched_catalog.json"
ROUTE_GEOMETRY_PATH = ASSETS_DIR / "local_route_geometry_index.json"
FETCH_MANIFEST_PATH = CACHE_DIR / "fetch_manifest.json"
DEFAULT_EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
EMBEDDING_DIMENSION = 384


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


def load_campfire_cards() -> list[dict[str, Any]]:
    return load_json(KNOW_HOW_CAMPFIRE_PATH)["cards"]


def normalize_whitespace(value: str) -> str:
    value = html.unescape(value)
    value = value.replace("\xa0", " ")
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def normalize_embedding_text(value: str) -> str:
    value = html.unescape(value)
    value = unicodedata.normalize("NFKD", value.lower())
    value = "".join(ch for ch in value if not unicodedata.combining(ch))
    value = re.sub(r"[^a-z0-9 ]", " ", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def tokenize_embedding_text(value: str) -> list[str]:
    return [token for token in normalize_embedding_text(value).split(" ") if len(token) >= 2]


def generate_typo_variants(phrase: str, limit: int = 24) -> list[str]:
    words = normalize_embedding_text(phrase).split()
    if not words:
        return []

    variants: set[str] = set()
    for word_index, token in enumerate(words):
        if len(token) < 4:
            continue
        token_variants: list[str] = []
        for index in range(len(token)):
            candidate = token[:index] + token[index + 1 :]
            if len(candidate) >= 3 and candidate != token:
                token_variants.append(candidate)
        for index in range(len(token) - 1):
            swapped = list(token)
            swapped[index], swapped[index + 1] = swapped[index + 1], swapped[index]
            candidate = "".join(swapped)
            if candidate != token:
                token_variants.append(candidate)
        for mutated in token_variants[:limit]:
            updated_words = list(words)
            updated_words[word_index] = mutated
            variants.add(" ".join(updated_words))

    normalized_original = normalize_embedding_text(phrase)
    variants.discard(normalized_original)
    return sorted(variants)


def pack_float32_vector(vector: list[float]) -> bytes:
    return struct.pack(f"<{len(vector)}f", *vector)


def build_text_embedder(
    model_name: str = DEFAULT_EMBEDDING_MODEL,
    dimension: int = EMBEDDING_DIMENSION,
) -> "BaseTextEmbedder":
    try:
        return SentenceTransformerTextEmbedder(model_name=model_name)
    except Exception:
        return HashedTextEmbedder(model_name=f"hashed:{model_name}", dimension=dimension)


class BaseTextEmbedder:
    backend_label = "unknown"
    model_name = DEFAULT_EMBEDDING_MODEL
    dimension = EMBEDDING_DIMENSION

    def encode(self, texts: list[str]) -> list[list[float]]:
        raise NotImplementedError


class SentenceTransformerTextEmbedder(BaseTextEmbedder):
    backend_label = "sentence_transformers"

    def __init__(self, model_name: str = DEFAULT_EMBEDDING_MODEL) -> None:
        from sentence_transformers import SentenceTransformer  # type: ignore

        self.model_name = model_name
        self._model = SentenceTransformer(model_name)
        self.dimension = int(self._model.get_sentence_embedding_dimension())

    def encode(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        vectors = self._model.encode(
            texts,
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        return [vector.astype("float32", copy=False).tolist() for vector in vectors]


class HashedTextEmbedder(BaseTextEmbedder):
    backend_label = "hashed_fallback"

    def __init__(self, model_name: str = DEFAULT_EMBEDDING_MODEL, dimension: int = EMBEDDING_DIMENSION) -> None:
        self.model_name = model_name
        self.dimension = dimension

    def encode(self, texts: list[str]) -> list[list[float]]:
        return [self._encode_one(text) for text in texts]

    def _encode_one(self, text: str) -> list[float]:
        features = _embedding_features(text)
        values = [0.0] * self.dimension
        for feature, weight in features:
            index, sign = _hash_feature(feature, self.dimension)
            values[index] += weight * sign
        return _l2_normalize(values)


def _hash_feature(feature: str, dimension: int) -> tuple[int, int]:
    digest = hashlib.sha256(feature.encode("utf-8")).digest()
    index = int.from_bytes(digest[:8], "little", signed=False) % dimension
    sign = -1 if digest[8] & 1 else 1
    return index, sign


def _l2_normalize(values: list[float]) -> list[float]:
    norm = math.sqrt(sum(value * value for value in values))
    if norm == 0.0:
        return values
    return [float(value / norm) for value in values]


def _embedding_features(text: str) -> list[tuple[str, float]]:
    normalized = normalize_embedding_text(text)
    tokens = tokenize_embedding_text(normalized)
    features: list[tuple[str, float]] = []

    for token in tokens:
        features.append((f"token:{token}", 1.6))
    for left, right in zip(tokens, tokens[1:]):
        features.append((f"bigram:{left}_{right}", 1.1))
    compact = normalized.replace(" ", "")
    for start in range(max(0, len(compact) - 2)):
        trigram = compact[start : start + 3]
        if len(trigram) == 3:
            features.append((f"char3:{trigram}", 0.35))
    for concept in _campfire_semantic_concepts(normalized):
        features.append((f"concept:{concept}", 2.4))

    return features


def _campfire_semantic_concepts(normalized: str) -> list[str]:
    concepts: list[str] = []
    token_set = set(tokenize_embedding_text(normalized))

    if token_set.intersection({"caldura", "frig", "incalzire", "incalzesc", "cald"}):
        concepts.append("goal:warmth")
    if token_set.intersection({"gatit", "gatesc", "mancare", "hrana"}):
        concepts.append("goal:cooking")
    if token_set.intersection({"fierb", "fiert", "boil"}) and "apa" in token_set:
        concepts.append("goal:boil_water")
    if token_set.intersection({"amnar", "ferro"}):
        concepts.append("ignition:ferro")
    if token_set.intersection({"bricheta", "brichete", "lighter"}):
        concepts.append("ignition:lighter")
    if token_set.intersection({"chibrit", "chibrite", "matches"}):
        concepts.append("ignition:matches")
    if "scanteie" in token_set or "spark" in token_set:
        concepts.append("ignition:recognized_spark")
    if "ud" in token_set or token_set.intersection({"umed", "umezeala", "ploua", "plouat"}):
        concepts.append("constraint:wet")
    if token_set.intersection({"vant", "vijelie"}):
        concepts.append("constraint:wind")
    if "tinder" in token_set or "iasca" in token_set:
        concepts.append("term:tinder")
    if "kindling" in token_set or "surcele" in token_set:
        concepts.append("term:kindling")
    if "vatra" in token_set:
        concepts.append("term:vatra")
    if (
        "nu am nimic" in normalized or
        "fara aprindere" in normalized or
        ("nu am" in normalized and token_set.intersection({"bricheta", "chibrit", "amnar"}))
    ):
        concepts.append("constraint:no_direct_ignition")

    return concepts


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

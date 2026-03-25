from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from collections import Counter
from pathlib import Path
from typing import Any

from common import (
    ASSETS_DIR,
    BUILD_DIR,
    CURATED_CHUNKS_PATH,
    FETCH_MANIFEST_PATH,
    ROOT_DIR,
    ROUTE_CATALOG_PATH,
    ROUTE_GEOMETRY_PATH,
    SOURCE_REGISTRY_PATH,
    LocalSourceInfo,
    clip_text,
    ensure_directory,
    load_curated_chunk_specs,
    load_json,
    load_pack_version,
    load_source_registry,
    marker_label,
    normalize_whitespace,
    safe_slug,
    sha256_file,
    utc_now_iso,
    write_json,
)


REQUIRED_DOMAINS = {
    "medical_emergency",
    "mountain_safety",
    "survival_basics",
    "wildlife_romania",
    "weather_and_season",
    "route_intelligence_romania",
    "gear_and_preparation",
}


def load_fetch_manifest() -> dict[str, dict[str, Any]]:
    if not FETCH_MANIFEST_PATH.exists():
        return {}
    payload = load_json(FETCH_MANIFEST_PATH)
    return {entry["source_id"]: entry for entry in payload.get("sources", [])}


def expand_curated_chunks(
    sources_by_id: dict[str, dict[str, Any]],
    pack_version: str,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for spec in load_curated_chunk_specs():
        source = sources_by_id[spec["source_id"]]
        for language, localized in spec["languages"].items():
            rows.append(
                {
                    "chunk_id": f"{spec['chunk_id']}_{language}",
                    "domain": spec["domain"],
                    "topic": spec["topic"],
                    "language": language,
                    "title": normalize_whitespace(localized["title"]),
                    "body": normalize_whitespace(localized["body"]),
                    "source_title": source["title"],
                    "source_url": source.get("url"),
                    "publisher": source["publisher"],
                    "source_language": source["source_language"],
                    "adapted_language": language,
                    "publish_or_review_date": spec["publish_or_review_date"],
                    "source_trust": int(source["source_trust"]),
                    "safety_tags": list(spec["safety_tags"]),
                    "country_scope": spec["country_scope"],
                    "pack_version": pack_version,
                    "keywords": normalize_whitespace(spec["keywords"]),
                }
            )
    return rows


def route_source_info(entry: dict[str, Any]) -> LocalSourceInfo:
    mn_data = entry.get("mn_data") or {}
    page_url = mn_data.get("page_url")
    pdf_url = mn_data.get("pdf_url")
    source_urls = entry.get("source_urls") or []
    osm_urls = entry.get("osm_relation_urls") or []

    if page_url or pdf_url:
        return LocalSourceInfo(
            source_title="Muntii Nostri route catalog",
            source_url=page_url or pdf_url,
            publisher="Muntii Nostri",
            source_trust=5,
        )
    if source_urls:
        return LocalSourceInfo(
            source_title="Scouty route source",
            source_url=source_urls[0],
            publisher="Scouty route pipeline",
            source_trust=4,
        )
    if osm_urls:
        return LocalSourceInfo(
            source_title="OpenStreetMap hiking relation",
            source_url=osm_urls[0],
            publisher="OpenStreetMap",
            source_trust=3,
        )
    return LocalSourceInfo(
        source_title="Scouty local route catalog",
        source_url=None,
        publisher="Scouty local route pipeline",
        source_trust=4,
    )


def build_route_bodies(local_code: str, entry: dict[str, Any], catalog_generated_at: str) -> dict[str, str]:
    mn_data = entry.get("mn_data") or {}
    distance_km = mn_data.get("distance_km") or entry.get("local_distance_km")
    ascent_m = mn_data.get("ascent_m")
    descent_m = mn_data.get("descent_m")
    duration = mn_data.get("duration_text")
    difficulty = mn_data.get("difficulty_label")
    region = entry.get("region") or "Romania"
    start = entry.get("from")
    end = entry.get("to")
    marker = marker_label(entry.get("symbols") or [])
    title = entry.get("display_title") or entry.get("title") or local_code
    local_description = clip_text(entry.get("local_description") or (entry.get("description") or {}).get("text_ro"))

    ro_parts = [
        f"Traseu romanesc: {title}.",
        f"Regiune: {region}.",
    ]
    if start or end:
        ro_parts.append(f"Puncte principale: {start or 'start neprecizat'} -> {end or 'finish neprecizat'}.")
    if marker:
        ro_parts.append(f"Marcaj: {marker}.")
    stats = []
    if difficulty:
        stats.append(f"dificultate {difficulty}")
    if duration:
        stats.append(f"durata {duration}")
    if distance_km:
        stats.append(f"distanta {distance_km} km")
    if ascent_m:
        stats.append(f"urcare +{ascent_m} m")
    if descent_m:
        stats.append(f"coborare -{descent_m} m")
    if stats:
        ro_parts.append("Date cheie: " + ", ".join(stats) + ".")
    if local_description:
        ro_parts.append(f"Rezumat local: {local_description}.")
    ro_parts.append(f"Versiunea de catalog folosita in pack: {catalog_generated_at}.")

    en_parts = [
        f"Romanian hiking route: {title}.",
        f"Region: {region}.",
    ]
    if start or end:
        en_parts.append(f"Main endpoints: {start or 'unspecified start'} -> {end or 'unspecified finish'}.")
    if marker:
        en_parts.append(f"Trail marker: {marker}.")
    stats_en = []
    if difficulty:
        stats_en.append(f"difficulty {difficulty}")
    if duration:
        stats_en.append(f"duration {duration}")
    if distance_km:
        stats_en.append(f"distance {distance_km} km")
    if ascent_m:
        stats_en.append(f"ascent +{ascent_m} m")
    if descent_m:
        stats_en.append(f"descent -{descent_m} m")
    if stats_en:
        en_parts.append("Key stats: " + ", ".join(stats_en) + ".")
    if local_description:
        en_parts.append(f"Local route line: {local_description}.")
    en_parts.append(f"Catalog snapshot used in the pack: {catalog_generated_at}.")

    return {
        "ro": " ".join(ro_parts),
        "en": " ".join(en_parts),
    }


def expand_route_chunks(pack_version: str) -> tuple[list[dict[str, Any]], str]:
    catalog_payload = load_json(ROUTE_CATALOG_PATH)
    catalog_generated_at = catalog_payload.get("generated_at", utc_now_iso())
    rows: list[dict[str, Any]] = []

    for local_code, entry in catalog_payload["routes_by_local_code"].items():
        source_info = route_source_info(entry)
        bodies = build_route_bodies(local_code, entry, catalog_generated_at)
        title = entry.get("display_title") or entry.get("title") or local_code
        keywords = normalize_whitespace(
            " ".join(
                filter(
                    None,
                    [
                        local_code,
                        title,
                        entry.get("region"),
                        entry.get("from"),
                        entry.get("to"),
                        " ".join(entry.get("symbols") or []),
                        marker_label(entry.get("symbols") or []),
                        (entry.get("mn_data") or {}).get("difficulty_label"),
                    ],
                )
            )
        )
        publish_or_review_date = catalog_generated_at.split("T", 1)[0]
        for language in ("ro", "en"):
            rows.append(
                {
                    "chunk_id": f"route_{safe_slug(local_code)}_{language}",
                    "domain": "route_intelligence_romania",
                    "topic": local_code,
                    "language": language,
                    "title": title,
                    "body": bodies[language],
                    "source_title": source_info.source_title,
                    "source_url": source_info.source_url,
                    "publisher": source_info.publisher,
                    "source_language": "ro",
                    "adapted_language": language,
                    "publish_or_review_date": publish_or_review_date,
                    "source_trust": source_info.source_trust,
                    "safety_tags": ["route", "marker", "romania", "trail"],
                    "country_scope": "ro",
                    "pack_version": pack_version,
                    "keywords": keywords,
                }
            )

    return rows, catalog_generated_at


def initialize_database(path: Path) -> sqlite3.Connection:
    if path.exists():
        path.unlink()
    connection = sqlite3.connect(path)
    connection.execute("PRAGMA journal_mode=OFF")
    connection.execute("PRAGMA synchronous=OFF")
    connection.execute(
        """
        CREATE TABLE metadata (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """
    )
    connection.execute(
        """
        CREATE TABLE knowledge_chunks (
            row_id INTEGER PRIMARY KEY AUTOINCREMENT,
            chunk_id TEXT NOT NULL UNIQUE,
            domain TEXT NOT NULL,
            topic TEXT NOT NULL,
            language TEXT NOT NULL,
            title TEXT NOT NULL,
            body TEXT NOT NULL,
            source_title TEXT NOT NULL,
            source_url TEXT,
            publisher TEXT NOT NULL,
            source_language TEXT NOT NULL,
            adapted_language TEXT NOT NULL,
            publish_or_review_date TEXT,
            source_trust INTEGER NOT NULL,
            safety_tags TEXT NOT NULL,
            country_scope TEXT NOT NULL,
            pack_version TEXT NOT NULL,
            keywords TEXT NOT NULL
        )
        """
    )
    connection.execute(
        """
        CREATE VIRTUAL TABLE knowledge_chunks_fts USING fts4 (
            title,
            body,
            topic,
            keywords,
            publisher
        )
        """
    )
    return connection


def insert_rows(connection: sqlite3.Connection, rows: list[dict[str, Any]]) -> None:
    cursor = connection.cursor()
    for row in rows:
        cursor.execute(
            """
            INSERT INTO knowledge_chunks (
                chunk_id,
                domain,
                topic,
                language,
                title,
                body,
                source_title,
                source_url,
                publisher,
                source_language,
                adapted_language,
                publish_or_review_date,
                source_trust,
                safety_tags,
                country_scope,
                pack_version,
                keywords
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                row["chunk_id"],
                row["domain"],
                row["topic"],
                row["language"],
                row["title"],
                row["body"],
                row["source_title"],
                row["source_url"],
                row["publisher"],
                row["source_language"],
                row["adapted_language"],
                row["publish_or_review_date"],
                row["source_trust"],
                json.dumps(row["safety_tags"], ensure_ascii=False),
                row["country_scope"],
                row["pack_version"],
                row["keywords"],
            ),
        )
        row_id = cursor.lastrowid
        cursor.execute(
            """
            INSERT INTO knowledge_chunks_fts(rowid, title, body, topic, keywords, publisher)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                row_id,
                row["title"],
                row["body"],
                row["topic"],
                row["keywords"],
                row["publisher"],
            ),
        )
    connection.commit()


def write_metadata(connection: sqlite3.Connection, payload: dict[str, Any]) -> None:
    connection.executemany(
        "INSERT INTO metadata(key, value) VALUES (?, ?)",
        [(key, json.dumps(value, ensure_ascii=False)) for key, value in payload.items()],
    )
    connection.commit()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build the Scouty knowledge pack sqlite database.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=BUILD_DIR,
        help="Directory used for intermediate pack build outputs.",
    )
    parser.add_argument(
        "--assets-dir",
        type=Path,
        default=ASSETS_DIR,
        help="Directory where final assets are copied.",
    )
    args = parser.parse_args(argv)

    pack_version = load_pack_version()
    build_dir = ensure_directory(args.output_dir)
    assets_dir = ensure_directory(args.assets_dir)
    db_path = build_dir / "knowledge_pack.sqlite"
    manifest_path = build_dir / "knowledge_pack_manifest.json"

    sources = load_source_registry()
    sources_by_id = {source["id"]: source for source in sources}
    fetch_manifest = load_fetch_manifest()

    curated_rows = expand_curated_chunks(sources_by_id, pack_version)
    route_rows, route_catalog_generated_at = expand_route_chunks(pack_version)
    all_rows = curated_rows + route_rows

    covered_domains = {row["domain"] for row in all_rows}
    missing_domains = sorted(REQUIRED_DOMAINS - covered_domains)
    if missing_domains:
        raise SystemExit(f"Missing required domains in knowledge pack: {', '.join(missing_domains)}")

    connection = initialize_database(db_path)
    insert_rows(connection, all_rows)
    metadata = {
        "pack_version": pack_version,
        "generated_at": utc_now_iso(),
        "chunk_count": len(all_rows),
        "curated_chunk_count": len(curated_rows),
        "route_chunk_count": len(route_rows),
        "domains": sorted(covered_domains),
        "languages": sorted({row["language"] for row in all_rows}),
    }
    write_metadata(connection, metadata)
    integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
    connection.close()
    if integrity.lower() != "ok":
        raise SystemExit(f"SQLite integrity check failed: {integrity}")

    db_sha256 = sha256_file(db_path)
    domain_counts = Counter(row["domain"] for row in all_rows)
    manifest_sources = []
    for source in sources:
        manifest_entry = dict(source)
        manifest_entry["fetch"] = fetch_manifest.get(source["id"])
        if "path" in manifest_entry:
            local_path = ROOT_DIR / manifest_entry["path"]
            manifest_entry["sha256"] = sha256_file(local_path)
        manifest_sources.append(manifest_entry)

    manifest_payload = {
        "pack_version": pack_version,
        "generated_at": metadata["generated_at"],
        "db_file_name": db_path.name,
        "db_sha256": db_sha256,
        "chunk_count": len(all_rows),
        "curated_chunk_count": len(curated_rows),
        "route_chunk_count": len(route_rows),
        "source_count": len(manifest_sources),
        "languages": metadata["languages"],
        "domains": metadata["domains"],
        "domain_counts": dict(sorted(domain_counts.items())),
        "required_domain_coverage": {domain: domain in covered_domains for domain in sorted(REQUIRED_DOMAINS)},
        "source_registry_sha256": sha256_file(SOURCE_REGISTRY_PATH),
        "curated_chunk_sha256": sha256_file(CURATED_CHUNKS_PATH),
        "route_catalog": {
            "path": str(ROUTE_CATALOG_PATH.relative_to(ASSETS_DIR.parent.parent.parent)),
            "generated_at": route_catalog_generated_at,
            "sha256": sha256_file(ROUTE_CATALOG_PATH)
        },
        "route_geometry_index": {
            "path": str(ROUTE_GEOMETRY_PATH.relative_to(ASSETS_DIR.parent.parent.parent)),
            "sha256": sha256_file(ROUTE_GEOMETRY_PATH)
        },
        "sources": manifest_sources,
    }
    write_json(manifest_path, manifest_payload)

    asset_db_path = assets_dir / db_path.name
    asset_manifest_path = assets_dir / manifest_path.name
    asset_db_path.write_bytes(db_path.read_bytes())
    asset_manifest_path.write_text(manifest_path.read_text(encoding="utf-8"), encoding="utf-8")

    print(f"[build] sqlite pack written to {db_path}")
    print(f"[build] manifest written to {manifest_path}")
    print(f"[build] assets copied to {asset_db_path} and {asset_manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

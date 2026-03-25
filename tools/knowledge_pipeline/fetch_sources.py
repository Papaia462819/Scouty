from __future__ import annotations

import argparse
import sys
import urllib.request
from pathlib import Path

from common import (
    CACHE_DIR,
    FETCH_MANIFEST_PATH,
    ensure_directory,
    extract_text_from_html,
    extract_text_from_pdf,
    load_source_registry,
    sha256_bytes,
    utc_now_iso,
    write_json,
)


USER_AGENT = "ScoutyKnowledgePipeline/1.0 (+offline-first knowledge sync)"


def fetch_url(url: str) -> tuple[bytes, str]:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/pdf,application/xhtml+xml;q=0.9,*/*;q=0.8",
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        payload = response.read()
        content_type = response.headers.get("Content-Type", "application/octet-stream")
    return payload, content_type


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Fetch approved Scouty knowledge sources.")
    parser.add_argument(
        "--cache-dir",
        type=Path,
        default=CACHE_DIR,
        help="Directory used for fetched source payloads.",
    )
    args = parser.parse_args(argv)

    cache_dir = ensure_directory(args.cache_dir)
    normalized_dir = ensure_directory(cache_dir / "normalized")
    manifest_entries = []

    for source in load_source_registry():
        url = source.get("url")
        if not url:
            continue

        extension = ".pdf" if source.get("kind") == "pdf" or url.lower().endswith(".pdf") else ".html"
        raw_path = cache_dir / f"{source['id']}{extension}"
        normalized_path = normalized_dir / f"{source['id']}.txt"

        print(f"[fetch] {source['id']} -> {url}")
        try:
            payload, content_type = fetch_url(url)
        except Exception as exc:
            manifest_entries.append(
                {
                    "source_id": source["id"],
                    "url": url,
                    "status": "error",
                    "error": str(exc),
                    "fetched_at": utc_now_iso(),
                }
            )
            continue

        raw_path.write_bytes(payload)
        normalized_text = None
        if extension == ".html":
            normalized_text = extract_text_from_html(payload.decode("utf-8", errors="ignore"))
        else:
            normalized_text = extract_text_from_pdf(raw_path)
        if normalized_text:
            normalized_path.write_text(normalized_text, encoding="utf-8")

        manifest_entries.append(
            {
                "source_id": source["id"],
                "url": url,
                "status": "ok",
                "kind": source.get("kind"),
                "content_type": content_type,
                "bytes": len(payload),
                "sha256": sha256_bytes(payload),
                "fetched_at": utc_now_iso(),
                "raw_path": raw_path.relative_to(cache_dir.parent).as_posix(),
                "normalized_path": normalized_path.relative_to(cache_dir.parent).as_posix()
                if normalized_text
                else None,
            }
        )

    write_json(
        FETCH_MANIFEST_PATH,
        {
            "generated_at": utc_now_iso(),
            "source_count": len(manifest_entries),
            "sources": manifest_entries,
        },
    )
    print(f"[fetch] manifest written to {FETCH_MANIFEST_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

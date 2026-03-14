#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.request
import zipfile
from datetime import UTC, datetime
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_SOURCE_DIR = REPO_ROOT.parent / "data" / "raw" / "tiles"
DEFAULT_OUTPUT_DIR = SCRIPT_DIR / "generated-map-packs"
DEFAULT_TMP_DIR = SCRIPT_DIR / ".tmp"
DEFAULT_BIN_DIR = SCRIPT_DIR / "bin"
PMTILES_VERSION = "v1.30.1"
PMTILES_ZIP_URL = (
    "https://github.com/protomaps/go-pmtiles/releases/download/"
    f"{PMTILES_VERSION}/go-pmtiles_1.30.1_Windows_x86_64.zip"
)

sys.path.insert(0, str(SCRIPT_DIR))
from build_bucegi_demo_bbox import compute_demo_bbox  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build Scouty offline PMTiles packs from local Romania MBTiles sources."
    )
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=DEFAULT_SOURCE_DIR,
        help="Directory containing Romania_mapbox_z13.mbtiles and Romania.mbtiles.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help="Directory where PMTiles packs will be written.",
    )
    parser.add_argument(
        "--tmp-dir",
        type=Path,
        default=DEFAULT_TMP_DIR,
        help="Temporary directory used during conversion.",
    )
    parser.add_argument(
        "--pmtiles-bin",
        type=Path,
        default=DEFAULT_BIN_DIR / "pmtiles.exe",
        help="Path to the go-pmtiles executable.",
    )
    parser.add_argument(
        "--keep-z14-pack",
        action="store_true",
        help="Keep the temporary national z14 PMTiles archive after extraction.",
    )
    return parser.parse_args()


def ensure_pmtiles_binary(pmtiles_bin: Path) -> Path:
    if pmtiles_bin.exists():
        return pmtiles_bin

    pmtiles_bin.parent.mkdir(parents=True, exist_ok=True)
    zip_path = pmtiles_bin.parent / "go-pmtiles_windows_x86_64.zip"
    print(f"Downloading go-pmtiles {PMTILES_VERSION}...")
    urllib.request.urlretrieve(PMTILES_ZIP_URL, zip_path)

    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(pmtiles_bin.parent)

    if not pmtiles_bin.exists():
        raise FileNotFoundError(f"Missing pmtiles binary after download: {pmtiles_bin}")
    return pmtiles_bin


def run_command(args: list[str | os.PathLike[str]]) -> None:
    printable = " ".join(str(arg) for arg in args)
    print(f"> {printable}")
    subprocess.run([str(arg) for arg in args], check=True)


def format_bbox(payload: dict) -> str:
    bbox = payload["bbox"]
    return f'{bbox["min_lon"]},{bbox["min_lat"]},{bbox["max_lon"]},{bbox["max_lat"]}'


def build_manifest(
    output_dir: Path,
    base_output: Path,
    demo_output: Path,
    demo_bbox_payload: dict,
    source_dir: Path,
) -> dict:
    return {
        "generated_at_utc": datetime.now(UTC).isoformat(),
        "source_dir": str(source_dir),
        "packs": [
            {
                "id": "romania-base",
                "file": base_output.name,
                "path": str(base_output),
                "size_bytes": base_output.stat().st_size,
                "source_mbtiles": "Romania_mapbox_z13.mbtiles",
                "zoom_range": "0-13",
            },
            {
                "id": "bucegi-high",
                "file": demo_output.name,
                "path": str(demo_output),
                "size_bytes": demo_output.stat().st_size,
                "source_mbtiles": "Romania.mbtiles",
                "zoom_range": "13-14",
                "bbox": demo_bbox_payload["bbox"],
                "region": demo_bbox_payload["region"],
                "route_count": demo_bbox_payload["route_count"],
                "padding_km": demo_bbox_payload["padding_km"],
            },
        ],
    }


def main() -> int:
    args = parse_args()
    source_dir = args.source_dir.resolve()
    output_dir = args.output_dir.resolve()
    tmp_dir = args.tmp_dir.resolve()
    pmtiles_bin = ensure_pmtiles_binary(args.pmtiles_bin.resolve())

    romania_base_mbtiles = source_dir / "Romania_mapbox_z13.mbtiles"
    romania_high_mbtiles = source_dir / "Romania.mbtiles"
    if not romania_base_mbtiles.exists():
        raise FileNotFoundError(f"Missing source MBTiles: {romania_base_mbtiles}")
    if not romania_high_mbtiles.exists():
        raise FileNotFoundError(f"Missing source MBTiles: {romania_high_mbtiles}")

    output_dir.mkdir(parents=True, exist_ok=True)
    tmp_dir.mkdir(parents=True, exist_ok=True)

    base_output = output_dir / "romania-base.pmtiles"
    demo_output = output_dir / "bucegi-high.pmtiles"
    full_high_output = tmp_dir / "romania-z14.pmtiles"

    run_command(
        [
            pmtiles_bin,
            "convert",
            romania_base_mbtiles,
            base_output,
            "--force",
            "--tmpdir",
            tmp_dir,
        ]
    )

    run_command(
        [
            pmtiles_bin,
            "convert",
            romania_high_mbtiles,
            full_high_output,
            "--force",
            "--tmpdir",
            tmp_dir,
        ]
    )

    demo_bbox_payload = compute_demo_bbox()
    run_command(
        [
            pmtiles_bin,
            "extract",
            full_high_output,
            demo_output,
            "--bbox",
            format_bbox(demo_bbox_payload),
            "--minzoom",
            "13",
            "--maxzoom",
            "14",
        ]
    )

    run_command([pmtiles_bin, "verify", base_output])
    run_command([pmtiles_bin, "verify", demo_output])

    manifest = build_manifest(output_dir, base_output, demo_output, demo_bbox_payload, source_dir)
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote manifest: {manifest_path}")

    if not args.keep_z14_pack and full_high_output.exists():
        full_high_output.unlink()
        print(f"Removed temporary archive: {full_high_output}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

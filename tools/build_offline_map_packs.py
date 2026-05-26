#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build the Scouty high-detail Romania PMTiles master archive."
    )
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=DEFAULT_SOURCE_DIR,
        help="Directory containing the source Romania MBTiles export.",
    )
    parser.add_argument(
        "--source-mbtiles",
        default="Romania.mbtiles",
        help="Source MBTiles file name inside --source-dir.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help="Directory where PMTiles packs and manifests will be written.",
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
        "--skip-convert",
        action="store_true",
        help="Do not convert MBTiles; only verify and manifest an existing romania-high-detail.pmtiles.",
    )
    parser.add_argument(
        "--skip-verify",
        action="store_true",
        help="Skip pmtiles verify after conversion.",
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


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_manifest(output: Path, source_mbtiles: Path) -> dict:
    generated_at = datetime.now(UTC).isoformat()
    return {
        "generated_at_utc": generated_at,
        "pmtiles_version": PMTILES_VERSION,
        "packs": [
            {
                "id": "romania-high-detail",
                "version": generated_at,
                "file": output.name,
                "upload_path": f"base/{output.name}",
                "path": str(output),
                "size_bytes": output.stat().st_size,
                "sha256": sha256_file(output),
                "source_mbtiles": str(source_mbtiles),
                "description": "Full Romania master PMTiles used online and as source for trail offline extracts.",
            }
        ],
    }


def main() -> int:
    args = parse_args()
    source_dir = args.source_dir.resolve()
    output_dir = args.output_dir.resolve()
    tmp_dir = args.tmp_dir.resolve()
    pmtiles_bin = ensure_pmtiles_binary(args.pmtiles_bin.resolve())

    source_mbtiles = source_dir / args.source_mbtiles
    output = output_dir / "romania-high-detail.pmtiles"

    if not args.skip_convert and not source_mbtiles.exists():
        raise FileNotFoundError(f"Missing source MBTiles: {source_mbtiles}")

    output_dir.mkdir(parents=True, exist_ok=True)
    tmp_dir.mkdir(parents=True, exist_ok=True)

    if not args.skip_convert:
        run_command(
            [
                pmtiles_bin,
                "convert",
                source_mbtiles,
                output,
                "--force",
                "--tmpdir",
                tmp_dir,
            ]
        )
    elif not output.exists():
        raise FileNotFoundError(f"Missing existing PMTiles output: {output}")

    if not args.skip_verify:
        run_command([pmtiles_bin, "verify", output])

    manifest = build_manifest(output, source_mbtiles)
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote manifest: {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

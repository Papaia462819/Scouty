#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import re
import sys
from datetime import UTC, datetime
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_GEOMETRY_INDEX = REPO_ROOT / "app/src/main/scouty_assets/local_route_geometry_index.json"
DEFAULT_MASTER_PMTILES = SCRIPT_DIR / "generated-map-packs/romania-high-detail.pmtiles"
DEFAULT_OUTPUT_DIR = SCRIPT_DIR / "generated-map-packs/trails"
DEFAULT_BIN_DIR = SCRIPT_DIR / "bin"

sys.path.insert(0, str(SCRIPT_DIR))
from build_offline_map_packs import ensure_pmtiles_binary, run_command, sha256_file  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build per-trail PMTiles packs from the high-detail Romania master archive."
    )
    parser.add_argument(
        "--geometry-index",
        type=Path,
        default=DEFAULT_GEOMETRY_INDEX,
        help="Route geometry index JSON.",
    )
    parser.add_argument(
        "--master-pmtiles",
        type=Path,
        default=DEFAULT_MASTER_PMTILES,
        help="High-detail Romania PMTiles archive to extract from.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help="Directory where trail pack folders will be written.",
    )
    parser.add_argument(
        "--pmtiles-bin",
        type=Path,
        default=DEFAULT_BIN_DIR / "pmtiles.exe",
        help="Path to the go-pmtiles executable.",
    )
    parser.add_argument(
        "--trail-code",
        action="append",
        help="Build only this trail code. Can be passed multiple times.",
    )
    parser.add_argument(
        "--padding-km",
        type=float,
        default=5.0,
        help="Buffer around each route bbox.",
    )
    parser.add_argument("--minzoom", type=int, help="Optional minimum zoom for extraction.")
    parser.add_argument("--maxzoom", type=int, help="Optional maximum zoom for extraction.")
    parser.add_argument("--limit", type=int, help="Build at most N packs after filtering.")
    parser.add_argument(
        "--keep-existing",
        action="store_true",
        help="Skip extraction when offline.pmtiles already exists for a trail.",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def safe_path_segment(value: str) -> str:
    return re.sub(r"[^a-z0-9._-]+", "_", value.lower()).strip("_") or "route"


def km_to_lat_degrees(distance_km: float) -> float:
    return distance_km / 111.32


def km_to_lon_degrees(distance_km: float, latitude: float) -> float:
    scale = math.cos(math.radians(latitude)) * 111.32
    if scale <= 0:
        return 0.0
    return distance_km / scale


def expand_bbox(bbox: dict, padding_km: float) -> dict:
    min_lat = float(bbox["min_lat"])
    min_lon = float(bbox["min_lon"])
    max_lat = float(bbox["max_lat"])
    max_lon = float(bbox["max_lon"])
    center_lat = (min_lat + max_lat) / 2.0
    lat_padding = km_to_lat_degrees(padding_km)
    lon_padding = km_to_lon_degrees(padding_km, center_lat)
    return {
        "min_lat": round(max(-90.0, min_lat - lat_padding), 6),
        "min_lon": round(max(-180.0, min_lon - lon_padding), 6),
        "max_lat": round(min(90.0, max_lat + lat_padding), 6),
        "max_lon": round(min(180.0, max_lon + lon_padding), 6),
    }


def bbox_arg(bbox: dict) -> str:
    return f'{bbox["min_lon"]},{bbox["min_lat"]},{bbox["max_lon"]},{bbox["max_lat"]}'


def build_one_pack(
    pmtiles_bin: Path,
    master_pmtiles: Path,
    output_dir: Path,
    route_code: str,
    route_payload: dict,
    padding_km: float,
    minzoom: int | None,
    maxzoom: int | None,
    keep_existing: bool,
    generated_at: str,
) -> dict:
    raw_bbox = route_payload.get("bbox")
    if not raw_bbox:
        raise ValueError(f"Route {route_code} has no bbox.")

    expanded_bbox = expand_bbox(raw_bbox, padding_km)
    trail_dir = output_dir / safe_path_segment(route_code)
    trail_dir.mkdir(parents=True, exist_ok=True)
    pack_path = trail_dir / "offline.pmtiles"

    if not keep_existing or not pack_path.exists():
        if pack_path.exists():
            pack_path.unlink()
        command: list[str | Path] = [
            pmtiles_bin,
            "extract",
            master_pmtiles,
            pack_path,
            "--bbox",
            bbox_arg(expanded_bbox),
        ]
        if minzoom is not None:
            command += ["--minzoom", str(minzoom)]
        if maxzoom is not None:
            command += ["--maxzoom", str(maxzoom)]
        run_command(command)

    if not pack_path.exists() or pack_path.stat().st_size <= 0:
        raise RuntimeError(f"PMTiles extraction produced no pack for {route_code}.")

    manifest = {
        "id": route_code,
        "version": generated_at,
        "file": "offline.pmtiles",
        "size_bytes": pack_path.stat().st_size,
        "sha256": sha256_file(pack_path),
        "generated_at_utc": generated_at,
        "bbox": expanded_bbox,
        "source_bbox": raw_bbox,
        "padding_km": padding_km,
        "source_master": str(master_pmtiles),
        "route": {
            "local_code": route_payload.get("local_code", route_code),
            "point_count": route_payload.get("point_count"),
            "segment_count": route_payload.get("segment_count"),
            "center": route_payload.get("center"),
        },
    }
    (trail_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return manifest


def main() -> int:
    args = parse_args()
    geometry_index = args.geometry_index.resolve()
    master_pmtiles = args.master_pmtiles.resolve()
    output_dir = args.output_dir.resolve()
    pmtiles_bin = ensure_pmtiles_binary(args.pmtiles_bin.resolve())

    if not geometry_index.exists():
        raise FileNotFoundError(f"Missing geometry index: {geometry_index}")
    if not master_pmtiles.exists():
        raise FileNotFoundError(f"Missing master PMTiles: {master_pmtiles}")

    routes = load_json(geometry_index).get("routes_by_local_code", {})
    selected_codes = args.trail_code or sorted(routes.keys())
    selected_codes = [code for code in selected_codes if code in routes]
    if args.limit is not None:
        selected_codes = selected_codes[: args.limit]
    if not selected_codes:
        raise SystemExit("No matching routes found.")

    output_dir.mkdir(parents=True, exist_ok=True)
    generated_at = datetime.now(UTC).isoformat()
    manifests = []
    for index, route_code in enumerate(selected_codes, start=1):
        print(f"[{index}/{len(selected_codes)}] Building {route_code}")
        manifests.append(
            build_one_pack(
                pmtiles_bin=pmtiles_bin,
                master_pmtiles=master_pmtiles,
                output_dir=output_dir,
                route_code=route_code,
                route_payload=routes[route_code],
                padding_km=args.padding_km,
                minzoom=args.minzoom,
                maxzoom=args.maxzoom,
                keep_existing=args.keep_existing,
                generated_at=generated_at,
            )
        )

    aggregate_manifest = {
        "generated_at_utc": generated_at,
        "source_master": str(master_pmtiles),
        "padding_km": args.padding_km,
        "route_count": len(manifests),
        "routes": [
            {
                "id": manifest["id"],
                "path": f'{safe_path_segment(manifest["id"])}/offline.pmtiles',
                "manifest": f'{safe_path_segment(manifest["id"])}/manifest.json',
                "size_bytes": manifest["size_bytes"],
                "sha256": manifest["sha256"],
                "bbox": manifest["bbox"],
            }
            for manifest in manifests
        ],
    }
    aggregate_path = output_dir / "trails-manifest.json"
    aggregate_path.write_text(
        json.dumps(aggregate_manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"Wrote manifest: {aggregate_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

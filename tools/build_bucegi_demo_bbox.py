#!/usr/bin/env python3
from __future__ import annotations

import json
import math
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
CATALOG_PATH = REPO_ROOT / "app/src/main/scouty_assets/local_route_enriched_catalog.json"
GEOMETRY_PATH = REPO_ROOT / "app/src/main/scouty_assets/local_route_geometry_index.json"
TARGET_REGION = "Bucegi - Leaota"
PADDING_KM = 10.0


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def km_to_lat_degrees(distance_km: float) -> float:
    return distance_km / 111.32


def km_to_lon_degrees(distance_km: float, latitude: float) -> float:
    scale = math.cos(math.radians(latitude)) * 111.32
    if scale <= 0:
        return 0.0
    return distance_km / scale


def compute_demo_bbox(
    catalog_path: Path = CATALOG_PATH,
    geometry_path: Path = GEOMETRY_PATH,
) -> dict:
    catalog = load_json(catalog_path).get("routes_by_local_code", {})
    geometry_index = load_json(geometry_path).get("routes_by_local_code", {})

    selected_codes = [
        code
        for code, route in catalog.items()
        if (route.get("region") or "").strip() == TARGET_REGION and code in geometry_index
    ]
    if not selected_codes:
        raise SystemExit("No Bucegi routes with geometry found.")

    min_lat = min(geometry_index[code]["bbox"]["min_lat"] for code in selected_codes)
    min_lon = min(geometry_index[code]["bbox"]["min_lon"] for code in selected_codes)
    max_lat = max(geometry_index[code]["bbox"]["max_lat"] for code in selected_codes)
    max_lon = max(geometry_index[code]["bbox"]["max_lon"] for code in selected_codes)

    center_lat = (min_lat + max_lat) / 2.0
    lat_padding = km_to_lat_degrees(PADDING_KM)
    lon_padding = km_to_lon_degrees(PADDING_KM, center_lat)

    payload = {
        "region": TARGET_REGION,
        "route_count": len(selected_codes),
        "padding_km": PADDING_KM,
        "bbox": {
            "min_lat": round(min_lat - lat_padding, 6),
            "min_lon": round(min_lon - lon_padding, 6),
            "max_lat": round(max_lat + lat_padding, 6),
            "max_lon": round(max_lon + lon_padding, 6),
        },
        "sample_codes": selected_codes[:12],
    }
    return payload


def main() -> int:
    payload = compute_demo_bbox()
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

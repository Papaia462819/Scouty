#!/usr/bin/env python3
"""Validate expected Step 6 tool-call JSON payloads for the Romanian corpus."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_QUERIES = ROOT / "tools/benchmarks/tool_call_ro_queries.json"
DEFAULT_OUTPUT = ROOT / "tools/benchmarks/tool_call_ro_results.json"

TOOLS = {
    "lookup_card",
    "set_gear_packed",
    "check_capability",
    "ask_clarification",
    "recall_previous",
    "respond_directly",
}
DOMAINS = {
    "campfire_basics",
    "gear_and_preparation",
    "tips_and_tricks",
    "survival_basics",
    "weather_and_season",
    "wildlife_romania",
    "route_intelligence_romania",
    "medical_emergency",
    "mountain_safety",
}
METRICS = {"duration", "elevation", "weather"}
SLOTS = {
    "goal",
    "ignition_source",
    "tinder_available",
    "tinder_material",
    "tinder_condition",
    "kindling_available",
    "fuel_condition",
    "wind",
    "permission",
    "ground_risk",
    "tinder_strategy",
    "need_level",
    "daylight",
    "fatigue",
    "compromised_item",
    "compromised_reason",
    "domain",
    "problem_cause",
}


def validate_call(call: dict) -> list[str]:
    issues: list[str] = []
    tool = call.get("tool")
    if tool not in TOOLS:
        issues.append(f"unknown tool {tool!r}")
        return issues
    if tool == "lookup_card":
        if call.get("domain") not in DOMAINS:
            issues.append("lookup_card domain invalid")
        if not isinstance(call.get("slot_filters", {}), dict):
            issues.append("lookup_card slot_filters must be object")
    elif tool == "set_gear_packed":
        if not isinstance(call.get("item_id"), str) or not call["item_id"].strip():
            issues.append("set_gear_packed item_id invalid")
        if not isinstance(call.get("packed"), bool):
            issues.append("set_gear_packed packed must be boolean")
    elif tool == "check_capability":
        if call.get("metric") not in METRICS:
            issues.append("check_capability metric invalid")
    elif tool == "ask_clarification":
        if call.get("slot") not in SLOTS:
            issues.append("ask_clarification slot invalid")
        if not isinstance(call.get("options"), list) or not call["options"]:
            issues.append("ask_clarification options missing")
    elif tool == "recall_previous":
        if not isinstance(call.get("topic"), str) or not call["topic"].strip():
            issues.append("recall_previous topic invalid")
    return issues


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--queries", type=Path, default=DEFAULT_QUERIES)
    parser.add_argument("--json-out", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    payload = json.loads(args.queries.read_text(encoding="utf-8"))
    rows = []
    for item in payload["queries"]:
        issues = validate_call(item["expected_call"])
        rows.append({**item, "valid": not issues, "issues": issues})
    summary = {
        "query_count": len(rows),
        "valid_count": sum(1 for row in rows if row["valid"]),
        "valid_rate": sum(1 for row in rows if row["valid"]) / len(rows) if rows else 0.0,
        "model_validated": False,
        "notes": "Validates the expected JSON schema/catalog for the 50-query corpus; does not call Qwen.",
    }
    args.json_out.write_text(
        json.dumps({"summary": summary, "rows": rows}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary["valid_rate"] == 1.0 else 1


if __name__ == "__main__":
    raise SystemExit(main())

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RULES = ROOT / "knowledge" / "rules" / "engine_air_fuel.json"


def matches(condition: dict, facts: dict) -> bool:
    metric = condition["metric"]
    if metric not in facts:
        return False
    fact = facts[metric]
    operator = condition["operator"]
    if operator == "Exists":
        return True
    if operator == "ContainsAny":
        return any(value in fact for value in condition.get("values", []))
    if operator == "GreaterThan":
        return float(fact) > float(condition["value"])
    if operator == "LessThan":
        return float(fact) < float(condition["value"])
    if operator in {"Between", "Outside"}:
        low, high = [float(value) for value in condition["values"]]
        inside = low <= float(fact) <= high
        return inside if operator == "Between" else not inside
    return False


def score(rule: dict, facts: dict) -> float:
    matched = 0.0
    possible = 0.0
    for condition in rule["conditions"]:
        possible += float(condition.get("weight", 1.0))
        if matches(condition, facts):
            matched += float(condition.get("weight", 1.0))
        elif condition.get("required"):
            return 0.0
    return matched / max(possible, 1.0)


def main() -> None:
    rules = json.loads(RULES.read_text(encoding="utf-8"))
    facts = {
        "combined_trim_b1": 21.5,
        "dtc_codes": ["P0171"],
        "maf_ratio_to_expected": 0.66,
        "throttle_percent": 18.0,
        "fuel_pressure_kpa": 365.0,
    }
    ranked = sorted(((score(rule, facts), rule["id"]) for rule in rules), reverse=True)
    top_score, top_id = ranked[0]
    if top_id != "gasoline.unmetered_air_lean_idle" or top_score < 0.80:
        raise SystemExit(f"smoke failed: top={top_id}, score={top_score:.2f}")
    print(f"diagnostic smoke valid: top={top_id}, score={top_score:.2f}")


if __name__ == "__main__":
    main()

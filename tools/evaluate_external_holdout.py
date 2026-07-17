from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports"


@dataclass(frozen=True)
class ExternalSource:
    key: str
    title: str
    url: str
    source_kind: str
    note: str


@dataclass(frozen=True)
class HoldoutCase:
    case_id: str
    source: str
    oracle: str
    expected: str
    acceptable: tuple[str, ...]
    facts: dict[str, Any]


SOURCES: dict[str, ExternalSource] = {
    "underhood-fuel-delivery": ExternalSource(
        "underhood-fuel-delivery",
        "Underhood Service fuel-delivery diagnostic examples",
        "https://www.underhoodservice.com/diagnostic-solutions-testing-fuel-delivery-systems-checking-the-basics-first-can-facilitate-diagnostic-strategy/",
        "article",
        "Fuel pump or supply faults show as lean load behavior and fuel delivery checks, not just idle trim.",
    ),
    "underhood-maf": ExternalSource(
        "underhood-maf",
        "Underhood Service MAF / VE diagnostic practice",
        "https://www.underhoodservice.com/troubleshooting-mass-air-flow-maf-sensors/",
        "article",
        "MAF plausibility is checked against displacement, RPM, load and VE-like expectations.",
    ),
    "motor-fuel-trim": ExternalSource(
        "motor-fuel-trim",
        "MOTOR fuel-trim diagnosis article",
        "https://www.motor.com/magazine-summary/fuel-trim-data-powerful-diagnostic-tool/",
        "article",
        "Fuel trim patterns across idle and load separate vacuum leaks, fuel delivery and MAF faults.",
    ),
    "skanyx-live-data": ExternalSource(
        "skanyx-live-data",
        "Skanyx live data stream analysis guide",
        "https://skanyx.com/blog/live-data-stream-analysis-guide",
        "article",
        "Live-data guide covers vacuum leaks, fuel-delivery issues, O2 behavior and catalyst rear-O2 tracking.",
    ),
    "obdcodes-f150-p0171": ExternalSource(
        "obdcodes-f150-p0171",
        "OBD-Codes forum: Ford F-150 lean codes with vacuum leak discussion",
        "https://www.obd-codes.com/forums/viewtopic.php?t=11458",
        "forum",
        "Forum-style lean-condition case used as an external oracle for vacuum-leak-like P0171/P0174 behavior.",
    ),
    "obdcodes-silverado-lean": ExternalSource(
        "obdcodes-silverado-lean",
        "OBD-Codes forum: Silverado lean condition solved with intake gaskets",
        "https://www.obd-codes.com/forums/viewtopic.php?t=5661",
        "forum",
        "Repair-oriented lean case used to check whether the app flags intake/vacuum leak rather than generic sensor replacement.",
    ),
    "obdcodes-avalanche-misfire": ExternalSource(
        "obdcodes-avalanche-misfire",
        "OBD-Codes forum: Avalanche P0300/P0171 fixed by loose plug wire",
        "https://www.obd-codes.com/forums/viewtopic.php?t=11464",
        "forum",
        "Misfire case where the precise repair is ignition wiring; app is expected to at least stay in misfire/ignition checks.",
    ),
    "obdcodes-cavalier-rich": ExternalSource(
        "obdcodes-cavalier-rich",
        "OBD-Codes forum: Cavalier P0172 rich / restricted injectors discussion",
        "https://www.obd-codes.com/forums/viewtopic.php?t=5662",
        "forum",
        "Rich code case with injector/fuel-delivery ambiguity; useful for partial-hit behavior.",
    ),
    "obdcodes-p0420-o2": ExternalSource(
        "obdcodes-p0420-o2",
        "OBD-Codes forum: P0420 after replacing O2 sensors",
        "https://www.obd-codes.com/forums/viewtopic.php?t=1960",
        "forum",
        "Catalyst-efficiency code case used to separate catalyst, rear O2 and upstream mixture prerequisites.",
    ),
    "repairpal-p0128": ExternalSource(
        "repairpal-p0128",
        "RepairPal P0128 thermostat/coolant diagnosis",
        "https://repairpal.com/obd-ii-code-p0128",
        "reference",
        "Cold-running / thermostat case used as an external oracle for ECT/P0128 behavior.",
    ),
    "obdii-p0562": ExternalSource(
        "obdii-p0562",
        "OBD-II P0562 system voltage reference",
        "https://www.obd-codes.com/p0562",
        "reference",
        "Low system voltage case used to check electrical-priority behavior before mixture conclusions.",
    ),
    "obdii-egr": ExternalSource(
        "obdii-egr",
        "OBD-II EGR flow DTC references",
        "https://www.obd-codes.com/p0401",
        "reference",
        "EGR flow code pattern used for idle/MAP/EGR fault holdout cases.",
    ),
    "obdii-vvt": ExternalSource(
        "obdii-vvt",
        "OBD-II cam/crank and VVT DTC references",
        "https://www.obd-codes.com/p0016",
        "reference",
        "Cam/crank correlation and VVT pattern used for mechanical timing holdout cases.",
    ),
}


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def load_rules() -> list[dict[str, Any]]:
    rules: list[dict[str, Any]] = []
    for path in sorted((ROOT / "knowledge" / "rules").glob("*.json")):
        rules.extend(load_json(path))
    return rules


def dtc_groups(codes: list[str]) -> list[str]:
    groups: list[str] = []
    def any_code(items: set[str]) -> bool:
        return any(code in items for code in codes)

    if any(code == "P0300" or code.startswith("P030") for code in codes):
        groups.append("misfire")
    if any_code({"P0171", "P0174"}):
        groups.append("lean")
    if any_code({"P0172", "P0175", "P2192"}):
        groups.append("rich")
    if any_code({"P0087", "P0088", "P0090", "P0190", "P0191", "P0192", "P0193", "P0194"}):
        groups.append("fuel_pressure")
    if any(code.startswith(("P013", "P014", "P015")) for code in codes):
        groups.append("oxygen_sensor")
    if any_code({"P0420", "P0430"}):
        groups.append("catalyst")
    if any(code.startswith("P044") or code in {"P0455", "P0456", "P0496"} for code in codes):
        groups.append("evap")
    if any(code.startswith("P040") for code in codes):
        groups.append("egr")
    if any(code.startswith("P001") or code.startswith("P002") for code in codes):
        groups.append("vvt")
    if any(code in {"P0016", "P0017", "P0018", "P0019", "P0335", "P0336", "P0340", "P0341"} for code in codes):
        groups.append("cam_crank_sync")
    if any(code in {"P0234", "P0235", "P0236", "P0237", "P0238", "P0243", "P0244", "P0299", "P2263"} for code in codes):
        groups.append("boost")
    if any(code.startswith("P07") for code in codes):
        groups.append("transmission")
    if any(code in {"P2002", "P242F", "P2452", "P2453", "P2463"} for code in codes):
        groups.append("dpf")
    if any(code.startswith("P020") or code.startswith("P02") for code in codes):
        groups.append("injector")
    if any(code in {"P0560", "P0562", "P0563"} for code in codes):
        groups.append("voltage")
    return sorted(set(groups))


def with_codes(facts: dict[str, Any], codes: list[str]) -> dict[str, Any]:
    enriched = dict(facts)
    enriched["dtc_codes"] = codes
    enriched["dtc_groups"] = dtc_groups(codes)
    enriched["dtc_count"] = len(codes)
    return enriched


def matches(condition: dict[str, Any], facts: dict[str, Any]) -> bool:
    metric = condition["metric"]
    if metric not in facts:
        return False
    fact = facts[metric]
    op = condition["operator"]
    if op == "Exists":
        return True
    if op == "ContainsAny":
        return any(value in fact for value in condition.get("values", []))
    if op == "GreaterThan":
        return float(fact) > float(condition["value"])
    if op == "LessThan":
        return float(fact) < float(condition["value"])
    if op in {"Between", "Outside"}:
        low, high = [float(value) for value in condition["values"]]
        inside = low <= float(fact) <= high
        return inside if op == "Between" else not inside
    return False


def evaluate_rule(rule: dict[str, Any], facts: dict[str, Any]) -> dict[str, Any] | None:
    evidence: list[str] = []
    missing: list[str] = []
    contradicted: list[str] = []
    matched_weight = 0.0
    possible_weight = 0.0
    required_failed = False
    for condition in rule["conditions"]:
        metric = condition["metric"]
        weight = float(condition.get("weight", 1.0))
        value = facts.get(metric)
        if value is None:
            possible_weight += weight if condition.get("required") else weight * 0.25
            missing.append(metric)
            if condition.get("required"):
                required_failed = True
            continue
        possible_weight += weight
        if matches(condition, facts):
            matched_weight += weight
            evidence.append(f"{metric}={value}")
        else:
            contradicted.append(f"{metric}={value}")
            if condition.get("required"):
                required_failed = True
    if required_failed or matched_weight <= 0.0:
        return None
    match_ratio = matched_weight / max(possible_weight, 1.0)
    contradiction_penalty = min(len(contradicted) * 0.08, 0.35)
    missing_penalty = min(len(missing) * 0.035, 0.25)
    probability = max(0.0, min(float(rule["probability"]) * (0.55 + match_ratio * 0.55) - contradiction_penalty, 0.99))
    confidence = max(0.0, min(float(rule["confidence"]) * (0.30 + match_ratio * 0.70) - missing_penalty - contradiction_penalty, 0.99))
    if probability < 0.25:
        return None
    return {
        "ruleId": rule["id"],
        "category": canonical_rule_category(rule["id"]),
        "title": rule["title"],
        "probability": round(probability, 2),
        "confidence": round(confidence, 2),
        "score": probability * confidence,
        "evidence": evidence,
        "missing": missing,
        "recommendedCheck": rule["recommendedChecks"][0] if rule.get("recommendedChecks") else "",
    }


def canonical_rule_category(rule_id: str) -> str:
    mapping = [
        ("unmetered_air", "unmetered_air"),
        ("fuel_delivery", "fuel_delivery"),
        ("fuel_pressure_or_rail", "fuel_delivery"),
        ("rich", "rich_condition"),
        ("low_airflow", "air_restriction"),
        ("restricted_exhaust", "exhaust_restriction"),
        ("throttle", "throttle_limited"),
        ("misfire", "misfire"),
        ("ignition", "ignition_retard"),
        ("turbo_underboost", "underboost"),
        ("low_operating_temperature", "thermostat_cold"),
        ("thermostat", "thermostat_cold"),
        ("low_system_voltage", "electrical"),
        ("low_or_unstable_system_voltage", "electrical"),
        ("oxygen_sensor", "oxygen_sensor"),
        ("catalyst_efficiency", "catalyst_efficiency"),
        ("evap", "evap_purge"),
        ("egr", "egr_fault"),
        ("vvt", "vvt_fault"),
        ("map_or_maf", "sensor_plausibility"),
        ("bank_specific", "bank_imbalance"),
        ("injector", "injector_balance"),
        ("overheat", "thermal"),
        ("low_fuel", "low_fuel_starvation"),
        ("torque_management", "throttle_limited"),
        ("transmission", "transmission_slip"),
        ("dpf", "dpf_restriction"),
        ("mechanical_compression", "mechanical_breathing"),
    ]
    for token, category in mapping:
        if token in rule_id:
            return category
    return "other"


def diagnose(rules: list[dict[str, Any]], facts: dict[str, Any]) -> dict[str, Any]:
    ranked = [item for rule in rules if (item := evaluate_rule(rule, facts))]
    if not ranked:
        return {"ruleId": "", "category": "insufficient_data", "title": "No hypothesis", "probability": 0.0, "confidence": 0.0, "score": 0.0, "recommendedCheck": ""}
    return sorted(ranked, key=lambda item: item["score"], reverse=True)[0]


def make_cases() -> list[HoldoutCase]:
    cases: list[HoldoutCase] = []

    def add(case_id: str, source: str, oracle: str, expected: str, facts: dict[str, Any], codes: list[str], acceptable: tuple[str, ...] = ()) -> None:
        cases.append(HoldoutCase(case_id, source, oracle, expected, acceptable, with_codes(facts, codes)))

    for i in range(14):
        add(
            f"EXT-LEAN-VAC-{i + 1:02d}",
            "obdcodes-silverado-lean" if i % 2 else "obdcodes-f150-p0171",
            "Lean idle/load trims and forum repair pattern point to intake/vacuum leak.",
            "unmetered_air",
            {
                "combined_trim_b1": 18 + (i % 6) * 2.0,
                "combined_trim_idle_b1": 20 + (i % 4) * 2.2,
                "combined_trim_load_b1": 6 + (i % 3),
                "trim_load_delta_b1": -13 - (i % 4),
                "maf_ratio_to_expected": 0.62 + (i % 5) * 0.03,
                "throttle_percent": 16 + i % 7,
                "map_kpa": 41 + i % 6,
                "idle_map_kpa": 43 + i % 5,
                "global_combined_trim_avg": 18 + i % 5,
            },
            ["P0171"] if i % 3 else ["P0171", "P0174"],
            ("evap_purge",),
        )

    for i in range(10):
        add(
            f"EXT-FUEL-LOAD-{i + 1:02d}",
            "underhood-fuel-delivery",
            "Lean only under load with weak fuel pressure follows fuel-delivery diagnostics.",
            "fuel_delivery",
            {
                "combined_trim_b1": 15 + i % 9,
                "combined_trim_idle_b1": 3 + i % 4,
                "combined_trim_load_b1": 17 + i % 8,
                "trim_load_delta_b1": 13 + i % 5,
                "max_throttle_percent": 78 + i % 14,
                "max_load_percent": 70 + i % 12,
                "lambda": 1.05 + (i % 4) * 0.01,
                "fuel_pressure_kpa": 210 + (i % 5) * 14,
                "global_combined_trim_avg": 13 + i % 9,
            },
            ["P0171"] if i % 2 else ["P0087"],
            ("low_fuel_starvation",),
        )

    for i in range(8):
        add(
            f"EXT-MAF-PLAUS-{i + 1:02d}",
            "underhood-maf",
            "MAF/VE outside expected range with otherwise plausible MAP/load points to metering fault.",
            "sensor_plausibility",
            {
                "sensor_implausibility_index": 0.36 + (i % 4) * 0.14,
                "peak_maf_ratio_to_expected": 0.38 + (i % 3) * 0.08,
                "map_load_coherence": 1.88 + (i % 3) * 0.18,
                "combined_trim_max_abs": 12 + i,
                "max_load_percent": 83 + i % 8,
                "max_map_kpa": 89 + i % 12,
            },
            ["P0171"] if i % 2 else [],
            ("air_restriction",),
        )

    for i in range(8):
        add(
            f"EXT-EVAP-PURGE-{i + 1:02d}",
            "motor-fuel-trim",
            "Idle-biased lean trim with EVAP purge behavior points to purge stuck open.",
            "evap_purge",
            {
                "combined_trim_b1": 17 + i % 7,
                "combined_trim_idle_b1": 15 + i % 8,
                "combined_trim_load_b1": 2 + i % 5,
                "trim_load_delta_b1": -12 - i % 5,
                "idle_map_kpa": 41 + i % 7,
                "rough_idle_index": 0.25 + (i % 4) * 0.08,
                "global_combined_trim_avg": 12 + i % 6,
            },
            ["P0496"] if i % 2 else ["P0441"],
            ("unmetered_air",),
        )

    for i in range(9):
        add(
            f"EXT-CAT-O2-{i + 1:02d}",
            "obdcodes-p0420-o2" if i % 2 else "skanyx-live-data",
            "Rear O2 activity follows front O2 with P0420/P0430 catalyst-efficiency pattern.",
            "catalyst_efficiency",
            {
                "catalyst_o2_similarity_index_b1": 0.28 + (i % 4) * 0.15,
                "downstream_o2_b1_range_v": 0.28 + (i % 5) * 0.08,
                "rear_o2_activity_ratio_b1": 0.78 + (i % 5) * 0.08,
                "front_o2_b1_range_v": 0.68,
            },
            ["P0420"] if i % 2 else ["P0430"],
            ("oxygen_sensor", "exhaust_restriction"),
        )

    for i in range(7):
        add(
            f"EXT-O2-LAZY-{i + 1:02d}",
            "skanyx-live-data",
            "Front O2 signal slow/stuck with trim movement points to O2 feedback fault.",
            "oxygen_sensor",
            {
                "front_o2_b1_stuck_index": 1.0,
                "front_o2_b1_range_v": 0.05 + (i % 3) * 0.03,
                "front_o2_b1_switch_count": i % 2,
                "duration_sec": 14 + i,
                "combined_trim_max_abs": 9 + i % 6,
                "sensor_implausibility_index": 0.42 + i % 3 * 0.12,
            },
            ["P0133"] if i % 2 else ["P0134"],
            ("sensor_plausibility",),
        )

    for i in range(8):
        add(
            f"EXT-RICH-{i + 1:02d}",
            "obdcodes-cavalier-rich" if i % 2 else "motor-fuel-trim",
            "Negative trims and rich DTC indicate excess fuel or low measured air.",
            "injector_or_fuel_regulator_rich",
            {
                "combined_trim_b1": -15 - (i % 7),
                "global_combined_trim_avg": -14 - (i % 5),
                "combined_trim_max_abs": 14 + i % 7,
                "lambda": 0.94 - (i % 3) * 0.01,
                "maf_ratio_to_expected": 1.22 + (i % 4) * 0.04,
                "coolant_c": 86 + i % 8,
            },
            ["P0172"] if i % 2 else ["P0175"],
            ("rich_condition", "injector_balance", "fuel_delivery"),
        )

    for i in range(10):
        add(
            f"EXT-MISFIRE-{i + 1:02d}",
            "obdcodes-avalanche-misfire",
            "Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks.",
            "ignition_secondary_repair",
            {
                "combined_trim_b1": 8 + i % 7,
                "rough_idle_index": 0.35 + (i % 5) * 0.09,
                "coolant_c": 82 + i % 12,
                "spark_torque_loss_index": 0.20 + (i % 5) * 0.08,
            },
            ["P0300"] if i % 4 else ["P0300", "P0171"],
            ("misfire", "ignition_retard", "injector_balance"),
        )

    for i in range(6):
        add(
            f"EXT-P0128-{i + 1:02d}",
            "repairpal-p0128",
            "P0128 and low coolant temperature point to thermostat/cold-operation strategy.",
            "thermostat_cold",
            {
                "coolant_c": 58 + i * 2,
                "cold_operation_index": 0.22 + (i % 4) * 0.12,
                "global_combined_trim_avg": 5 + i % 5,
            },
            ["P0128"],
        )

    for i in range(5):
        add(
            f"EXT-EGR-{i + 1:02d}",
            "obdii-egr",
            "EGR flow code with rough idle and high idle MAP points to excess/stuck EGR behavior.",
            "egr_fault",
            {
                "rough_idle_index": 0.32 + (i % 4) * 0.1,
                "idle_map_kpa": 44 + i % 7,
                "max_load_percent": 62 + i % 10,
            },
            ["P0401"] if i % 2 else ["P0402"],
            ("unmetered_air",),
        )

    for i in range(5):
        add(
            f"EXT-VVT-{i + 1:02d}",
            "obdii-vvt",
            "Cam/crank or VVT codes with weak breathing point to mechanical timing or VVT control.",
            "vvt_fault",
            {
                "mechanical_breathing_index": 0.22 + (i % 5) * 0.12,
                "rough_idle_index": 0.22 + (i % 4) * 0.09,
                "max_load_percent": 58 + i % 12,
            },
            ["P0016"] if i % 2 else ["P0011"],
            ("mechanical_breathing",),
        )

    for i in range(4):
        add(
            f"EXT-VOLT-{i + 1:02d}",
            "obdii-p0562",
            "Low module voltage DTC/reading should prioritize electrical checks.",
            "electrical",
            {
                "module_voltage_v": 11.4 + i * 0.15,
                "low_voltage_index": 0.35 + i * 0.1,
                "rough_idle_index": 0.18 + i * 0.04,
            },
            ["P0562"],
        )

    for i in range(3):
        add(
            f"EXT-THROTTLE-{i + 1:02d}",
            "skanyx-live-data",
            "Low throttle opening with ETC DTC and weak load response points to throttle/torque limitation.",
            "throttle_limited",
            {
                "throttle_percent": 42 + i * 4,
                "max_throttle_percent": 48 + i * 3,
                "max_load_percent": 40 + i * 5,
                "rpm": 2200 + i * 350,
            },
            ["P2135"] if i % 2 else ["P0121"],
        )

    for i in range(3):
        add(
            f"EXT-TRANS-SLIP-{i + 1:02d}",
            "skanyx-live-data",
            "RPM rises under throttle without speed gain; expected downstream drivetrain/transmission issue.",
            "transmission_slip",
            {
                "wot_no_speed_gain_index": 1.0,
                "max_throttle_percent": 84 + i * 3,
                "max_load_percent": 70 + i * 4,
                "rpm_delta": 1200 + i * 350,
                "speed_delta_kph": 3 + i,
                "peak_maf_ratio_to_expected": 0.92,
            },
            ["P0730"] if i == 1 else [],
            ("no_fault",),
        )

    return cases


def summarize(results: list[dict[str, Any]]) -> dict[str, Any]:
    counts = {"exact": 0, "partial": 0, "miss": 0, "insufficient": 0}
    by_expected: dict[str, dict[str, int]] = {}
    for row in results:
        counts[row["outcome"]] += 1
        by_expected.setdefault(row["expected"], {"exact": 0, "partial": 0, "miss": 0, "insufficient": 0})
        by_expected[row["expected"]][row["outcome"]] += 1
    return {
        "total": len(results),
        "counts": counts,
        "exactRate": round(counts["exact"] / max(len(results), 1), 4),
        "usefulRate": round((counts["exact"] + counts["partial"]) / max(len(results), 1), 4),
        "byExpected": by_expected,
    }


def write_markdown(results: list[dict[str, Any]], summary: dict[str, Any]) -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    examples = sorted(results, key=lambda row: {"miss": 0, "partial": 1, "exact": 2, "insufficient": 0}[row["outcome"]])[:22]
    lines = [
        "# External Holdout 100 Evaluation",
        "",
        "This report is a test-only external holdout. Cases are not used for training.",
        "",
        f"- Total cases: {summary['total']}",
        f"- Exact: {summary['counts']['exact']}",
        f"- Partial/useful: {summary['counts']['partial']}",
        f"- Miss: {summary['counts']['miss']}",
        f"- Insufficient: {summary['counts']['insufficient']}",
        f"- Useful rate: {summary['usefulRate']:.1%}",
        "",
        "| Case | Source | Oracle | Expected | App top | Outcome | Recommendation |",
        "|---|---|---|---|---|---|---|",
    ]
    for row in examples:
        source = SOURCES[row["source"]]
        lines.append(
            "| {case} | [{src}]({url}) | {oracle} | {expected} | {top} ({prob:.0%}/{conf:.0%}) | {outcome} | {rec} |".format(
                case=row["case_id"],
                src=source.key,
                url=source.url,
                oracle=row["oracle"].replace("|", "/"),
                expected=row["expected"],
                top=row["predicted"],
                prob=row["probability"],
                conf=row["confidence"],
                outcome=row["outcome"],
                rec=row["recommendedCheck"].replace("|", "/"),
            )
        )
    lines.extend(["", "## Sources", ""])
    for source in SOURCES.values():
        lines.append(f"- [{source.key}]({source.url}) - {source.title}.")
    (REPORT_DIR / "external_holdout_100_report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    rules = load_rules()
    cases = make_cases()
    if len(cases) != 100:
        raise SystemExit(f"holdout must contain exactly 100 cases, got {len(cases)}")
    results: list[dict[str, Any]] = []
    for case in cases:
        top = diagnose(rules, case.facts)
        predicted = top["category"]
        if predicted == "insufficient_data":
            outcome = "insufficient"
        elif predicted == case.expected:
            outcome = "exact"
        elif predicted in case.acceptable:
            outcome = "partial"
        else:
            outcome = "miss"
        results.append(
            {
                "case_id": case.case_id,
                "source": case.source,
                "oracle": case.oracle,
                "expected": case.expected,
                "acceptable": list(case.acceptable),
                "predicted": predicted,
                "ruleId": top["ruleId"],
                "probability": top["probability"],
                "confidence": top["confidence"],
                "outcome": outcome,
                "recommendedCheck": top["recommendedCheck"],
                "facts": case.facts,
            }
        )
    summary = summarize(results)
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    (REPORT_DIR / "external_holdout_100_results.json").write_text(
        json.dumps({"summary": summary, "results": results, "sources": {k: source.__dict__ for k, source in SOURCES.items()}}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    write_markdown(results, summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

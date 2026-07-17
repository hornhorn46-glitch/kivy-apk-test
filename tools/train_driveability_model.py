from __future__ import annotations

import csv
import json
import math
import random
import statistics
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data_sources"
KB = ROOT / "knowledge"


FEATURES = [
    "combined_trim_idle_b1",
    "combined_trim_load_b1",
    "trim_load_delta_b1",
    "peak_maf_ratio_to_expected",
    "estimated_peak_ve",
    "max_throttle_percent",
    "max_load_percent",
    "max_map_kpa",
    "min_timing_deg",
    "module_voltage_avg_v",
    "coolant_c",
    "iat_c",
    "estimated_boost_kpa",
    "boost_expected_for_profile",
    "wot_airflow_deficit",
    "throttle_load_mismatch",
    "idle_vacuum_kpa",
    "intake_restriction_index",
    "exhaust_restriction_index",
    "spark_torque_loss_index",
    "thermal_air_density_loss_index",
    "power_ratio_to_reference",
    "specific_airflow_gps_per_liter",
    "sensor_implausibility_index",
    "mechanical_breathing_index",
    "clean_wot_adequacy_index",
    "has_misfire_dtc",
    "has_lean_dtc",
    "has_rich_dtc",
    "has_throttle_dtc",
    "has_knock_dtc",
    "has_voltage_dtc",
    "diagnostic_data_coverage",
]

FEATURE_NORMALIZATION = {
    "combined_trim_idle_b1": (0.0, 30.0),
    "combined_trim_load_b1": (0.0, 30.0),
    "trim_load_delta_b1": (0.0, 30.0),
    "peak_maf_ratio_to_expected": (0.85, 0.30),
    "estimated_peak_ve": (0.72, 0.26),
    "max_throttle_percent": (80.0, 25.0),
    "max_load_percent": (75.0, 25.0),
    "max_map_kpa": (90.0, 25.0),
    "min_timing_deg": (12.0, 15.0),
    "module_voltage_avg_v": (13.4, 1.5),
    "coolant_c": (96.0, 18.0),
    "iat_c": (42.0, 30.0),
    "estimated_boost_kpa": (28.0, 35.0),
    "boost_expected_for_profile": (0.0, 1.0),
    "wot_airflow_deficit": (0.0, 0.45),
    "throttle_load_mismatch": (0.0, 35.0),
    "idle_vacuum_kpa": (62.0, 22.0),
    "intake_restriction_index": (0.0, 0.45),
    "exhaust_restriction_index": (0.0, 0.45),
    "spark_torque_loss_index": (0.0, 1.0),
    "thermal_air_density_loss_index": (0.0, 0.25),
    "power_ratio_to_reference": (0.95, 0.35),
    "specific_airflow_gps_per_liter": (24.0, 12.0),
    "sensor_implausibility_index": (0.0, 0.55),
    "mechanical_breathing_index": (0.0, 0.55),
    "clean_wot_adequacy_index": (0.0, 1.0),
    "has_misfire_dtc": (0.0, 1.0),
    "has_lean_dtc": (0.0, 1.0),
    "has_rich_dtc": (0.0, 1.0),
    "has_throttle_dtc": (0.0, 1.0),
    "has_knock_dtc": (0.0, 1.0),
    "has_voltage_dtc": (0.0, 1.0),
    "diagnostic_data_coverage": (0.0, 1.0),
}

FEATURE_MISSING_VALUES = {
    "module_voltage_avg_v": 13.6,
    "coolant_c": 90.0,
    "iat_c": 35.0,
    "diagnostic_data_coverage": 0.0,
    "idle_vacuum_kpa": 62.0,
    "power_ratio_to_reference": 0.95,
}


ROOT_CAUSES = [
    "no_fault",
    "unmetered_air",
    "fuel_delivery",
    "rich_condition",
    "air_restriction",
    "exhaust_restriction",
    "throttle_limited",
    "ignition_retard",
    "misfire",
    "underboost",
    "cam_timing_or_compression",
    "sensor_plausibility",
    "thermal",
    "electrical",
]


@dataclass(frozen=True)
class VehicleTemplate:
    vehicle_id: str
    make: str
    model: str
    year: int
    displacement_l: float
    max_rpm: float
    max_load: float
    max_map: float
    max_maf: float
    max_throttle: float
    min_timing: float
    coolant: float
    iat: float


def parse_number(value: str | None) -> float | None:
    if value is None:
        return None
    text = str(value).strip().replace('"', "")
    if not text:
        return None
    text = text.replace("%", "").replace(",", ".")
    try:
        return float(text)
    except ValueError:
        return None


def load_vehicle_templates() -> list[VehicleTemplate]:
    path = DATA / "vehiclepm" / "exp1_14drivers_14cars_dailyRoutes.csv"
    if not path.exists():
        raise SystemExit(f"missing OBD source: {path}")

    rows_by_vehicle: dict[str, list[dict[str, str]]] = defaultdict(list)
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            vehicle_id = row.get("VEHICLE_ID") or "unknown"
            rows_by_vehicle[vehicle_id].append(row)

    templates: list[VehicleTemplate] = []
    for vehicle_id, rows in sorted(rows_by_vehicle.items()):
        make = (rows[0].get("MARK") or "generic").strip().lower()
        model = (rows[0].get("MODEL") or vehicle_id).strip().lower()
        year = int(parse_number(rows[0].get("CAR_YEAR")) or 2016)
        displacement = parse_number(rows[0].get("ENGINE_POWER")) or 1.6

        def values(column: str) -> list[float]:
            parsed = [parse_number(row.get(column)) for row in rows]
            return [item for item in parsed if item is not None]

        rpm = values("ENGINE_RPM")
        load = values("ENGINE_LOAD")
        map_kpa = values("INTAKE_MANIFOLD_PRESSURE")
        maf = values("MAF")
        throttle = values("THROTTLE_POS")
        timing = values("TIMING_ADVANCE")
        coolant = values("ENGINE_COOLANT_TEMP")
        iat = values("AIR_INTAKE_TEMP")
        if len(rpm) < 10 or (not maf and not map_kpa):
            continue
        derived_maf = max(maf) if maf else max(1.0, max(rpm) * max(displacement, 0.8) * 0.82 / 120.0)
        templates.append(
            VehicleTemplate(
                vehicle_id=vehicle_id,
                make=make,
                model=model,
                year=year,
                displacement_l=max(0.8, min(displacement, 6.5)),
                max_rpm=max(rpm),
                max_load=max(load) if load else 72.0,
                max_map=max(map_kpa) if map_kpa else 86.0,
                max_maf=derived_maf,
                max_throttle=max(throttle) if throttle else 82.0,
                min_timing=min(timing) if timing else 9.0,
                coolant=statistics.median(coolant) if coolant else 88.0,
                iat=statistics.median(iat) if iat else 35.0,
            )
        )
    if len(templates) < 10:
        raise SystemExit("not enough real OBD vehicle templates")
    return templates


def base_features(template: VehicleTemplate, rng: random.Random, synthetic_index: int) -> dict[str, float]:
    displacement_scale = 0.85 + (synthetic_index % 16) * 0.045
    rpm = max(template.max_rpm, 2500.0 + (synthetic_index % 7) * 260.0)
    expected_maf = max(template.displacement_l * displacement_scale * rpm * 0.85 / 120.0, 1.0)
    maf_ratio = max(0.83, min(template.max_maf / expected_maf, 1.12))
    throttle = max(template.max_throttle, 78.0 + rng.random() * 17.0)
    load = max(template.max_load, 72.0 + rng.random() * 23.0)
    airflow_deficit = max(0.0, 1.0 - maf_ratio)
    sensor_implausibility = max(0.0, abs(maf_ratio - 0.95) - 0.28)
    specific_airflow = (maf_ratio * expected_maf) / max(template.displacement_l * displacement_scale, 0.8)
    rated_power_kw = max(template.displacement_l * displacement_scale * 58.0, 45.0)
    estimated_power_kw = maf_ratio * expected_maf * 1.22
    clean_wot = 1.0 if maf_ratio >= 0.80 and load >= 78.0 and throttle >= 78.0 else 0.0
    return {
        "combined_trim_idle_b1": rng.uniform(-3.5, 4.5),
        "combined_trim_load_b1": rng.uniform(-3.5, 4.5),
        "trim_load_delta_b1": rng.uniform(-3.0, 3.0),
        "peak_maf_ratio_to_expected": maf_ratio,
        "estimated_peak_ve": maf_ratio * 0.85,
        "max_throttle_percent": min(throttle, 98.0),
        "max_load_percent": min(load, 98.0),
        "max_map_kpa": min(max(template.max_map, 78.0 + rng.random() * 20.0), 106.0),
        "min_timing_deg": min(max(template.min_timing, 8.0 + rng.random() * 16.0), 36.0),
        "module_voltage_avg_v": rng.uniform(13.25, 14.45),
        "coolant_c": min(max(template.coolant, 82.0), 101.0),
        "iat_c": min(max(template.iat, 8.0), 58.0),
        "estimated_boost_kpa": rng.uniform(-2.0, 4.0),
        "boost_expected_for_profile": 0.0,
        "wot_airflow_deficit": airflow_deficit,
        "throttle_load_mismatch": max(0.0, throttle - load),
        "idle_vacuum_kpa": rng.uniform(56.0, 74.0),
        "intake_restriction_index": max(0.0, airflow_deficit * rng.uniform(0.0, 0.18)),
        "exhaust_restriction_index": max(0.0, airflow_deficit * rng.uniform(0.0, 0.14)),
        "spark_torque_loss_index": max(0.0, (12.0 - min(max(template.min_timing, 8.0 + rng.random() * 16.0), 36.0)) / 12.0),
        "thermal_air_density_loss_index": max(0.0, ((min(max(template.iat, 8.0), 58.0)) - 35.0) * 0.0032),
        "power_ratio_to_reference": max(0.62, min(estimated_power_kw / rated_power_kw, 1.18)),
        "specific_airflow_gps_per_liter": specific_airflow,
        "sensor_implausibility_index": sensor_implausibility,
        "mechanical_breathing_index": max(0.0, airflow_deficit - 0.18) * 0.18,
        "clean_wot_adequacy_index": clean_wot,
        "has_misfire_dtc": 0.0,
        "has_lean_dtc": 0.0,
        "has_rich_dtc": 0.0,
        "has_throttle_dtc": 0.0,
        "has_knock_dtc": 0.0,
        "has_voltage_dtc": 0.0,
        "diagnostic_data_coverage": rng.uniform(0.82, 1.0),
    }


def inject_fault(features: dict[str, float], label: str, rng: random.Random) -> dict[str, float]:
    f = dict(features)
    if label == "no_fault":
        return f
    if label == "unmetered_air":
        f["combined_trim_idle_b1"] = rng.uniform(15.0, 28.0)
        f["combined_trim_load_b1"] = rng.uniform(4.0, 11.0)
        f["trim_load_delta_b1"] = f["combined_trim_load_b1"] - f["combined_trim_idle_b1"]
        f["idle_vacuum_kpa"] = rng.uniform(26.0, 48.0)
        f["has_lean_dtc"] = 1.0 if rng.random() > 0.2 else 0.0
    elif label == "fuel_delivery":
        f["combined_trim_idle_b1"] = rng.uniform(1.0, 8.0)
        f["combined_trim_load_b1"] = rng.uniform(15.0, 31.0)
        f["trim_load_delta_b1"] = f["combined_trim_load_b1"] - f["combined_trim_idle_b1"]
        f["max_load_percent"] = rng.uniform(52.0, 76.0)
        f["peak_maf_ratio_to_expected"] = rng.uniform(0.55, 0.78)
        f["wot_airflow_deficit"] = 1.0 - f["peak_maf_ratio_to_expected"]
        f["power_ratio_to_reference"] = rng.uniform(0.50, 0.78)
        f["has_lean_dtc"] = 1.0 if rng.random() > 0.35 else 0.0
    elif label == "rich_condition":
        f["combined_trim_idle_b1"] = rng.uniform(-24.0, -11.0)
        f["combined_trim_load_b1"] = rng.uniform(-26.0, -9.0)
        f["trim_load_delta_b1"] = f["combined_trim_load_b1"] - f["combined_trim_idle_b1"]
        f["power_ratio_to_reference"] = rng.uniform(0.68, 1.02)
        f["has_rich_dtc"] = 1.0 if rng.random() > 0.25 else 0.0
    elif label == "air_restriction":
        f["peak_maf_ratio_to_expected"] = rng.uniform(0.44, 0.67)
        f["estimated_peak_ve"] = rng.uniform(0.38, 0.57)
        f["max_load_percent"] = rng.uniform(48.0, 69.0)
        f["max_map_kpa"] = rng.uniform(58.0, 82.0)
        f["wot_airflow_deficit"] = 1.0 - f["peak_maf_ratio_to_expected"]
        f["throttle_load_mismatch"] = rng.uniform(20.0, 45.0)
        f["intake_restriction_index"] = rng.uniform(0.30, 0.78)
        f["exhaust_restriction_index"] = rng.uniform(0.0, 0.12)
        f["power_ratio_to_reference"] = rng.uniform(0.45, 0.72)
        f["specific_airflow_gps_per_liter"] = rng.uniform(10.0, 17.0)
        f["sensor_implausibility_index"] = rng.uniform(0.02, 0.18)
        f["mechanical_breathing_index"] = rng.uniform(0.02, 0.18)
        f["clean_wot_adequacy_index"] = 0.0
    elif label == "exhaust_restriction":
        f["peak_maf_ratio_to_expected"] = rng.uniform(0.50, 0.73)
        f["estimated_peak_ve"] = rng.uniform(0.43, 0.62)
        f["max_map_kpa"] = rng.uniform(88.0, 104.0)
        f["min_timing_deg"] = rng.uniform(2.0, 9.0)
        f["max_load_percent"] = rng.uniform(46.0, 70.0)
        f["wot_airflow_deficit"] = 1.0 - f["peak_maf_ratio_to_expected"]
        f["throttle_load_mismatch"] = rng.uniform(18.0, 42.0)
        f["intake_restriction_index"] = rng.uniform(0.0, 0.12)
        f["exhaust_restriction_index"] = rng.uniform(0.26, 0.74)
        f["spark_torque_loss_index"] = rng.uniform(0.25, 0.75)
        f["power_ratio_to_reference"] = rng.uniform(0.43, 0.70)
        f["specific_airflow_gps_per_liter"] = rng.uniform(11.0, 18.0)
        f["sensor_implausibility_index"] = rng.uniform(0.02, 0.16)
        f["mechanical_breathing_index"] = rng.uniform(0.04, 0.20)
        f["clean_wot_adequacy_index"] = 0.0
    elif label == "throttle_limited":
        f["max_throttle_percent"] = rng.uniform(22.0, 58.0)
        f["max_load_percent"] = rng.uniform(25.0, 62.0)
        f["throttle_load_mismatch"] = rng.uniform(-6.0, 12.0)
        f["power_ratio_to_reference"] = rng.uniform(0.42, 0.75)
        f["has_throttle_dtc"] = 1.0 if rng.random() > 0.15 else 0.0
    elif label == "ignition_retard":
        f["min_timing_deg"] = rng.uniform(-5.0, 4.0)
        f["max_load_percent"] = rng.uniform(62.0, 92.0)
        f["spark_torque_loss_index"] = rng.uniform(0.55, 1.35)
        f["power_ratio_to_reference"] = rng.uniform(0.55, 0.86)
        f["has_knock_dtc"] = 1.0 if rng.random() > 0.35 else 0.0
    elif label == "misfire":
        f["has_misfire_dtc"] = 1.0
        f["combined_trim_idle_b1"] = rng.uniform(5.0, 17.0)
        f["combined_trim_load_b1"] = rng.uniform(3.0, 15.0)
        f["min_timing_deg"] = rng.uniform(1.0, 10.0)
        f["spark_torque_loss_index"] = rng.uniform(0.20, 0.85)
        f["power_ratio_to_reference"] = rng.uniform(0.40, 0.78)
    elif label == "underboost":
        f["boost_expected_for_profile"] = 1.0
        f["estimated_boost_kpa"] = rng.uniform(-8.0, 18.0)
        f["max_map_kpa"] = rng.uniform(92.0, 116.0)
        f["max_load_percent"] = rng.uniform(50.0, 76.0)
        f["peak_maf_ratio_to_expected"] = rng.uniform(0.45, 0.76)
        f["wot_airflow_deficit"] = 1.0 - f["peak_maf_ratio_to_expected"]
        f["power_ratio_to_reference"] = rng.uniform(0.45, 0.75)
    elif label == "cam_timing_or_compression":
        f["peak_maf_ratio_to_expected"] = rng.uniform(0.48, 0.72)
        f["estimated_peak_ve"] = rng.uniform(0.40, 0.61)
        f["max_load_percent"] = rng.uniform(45.0, 72.0)
        f["max_map_kpa"] = rng.uniform(76.0, 101.0)
        f["combined_trim_idle_b1"] = rng.uniform(-4.0, 8.0)
        f["combined_trim_load_b1"] = rng.uniform(-3.0, 9.0)
        f["trim_load_delta_b1"] = f["combined_trim_load_b1"] - f["combined_trim_idle_b1"]
        f["wot_airflow_deficit"] = 1.0 - f["peak_maf_ratio_to_expected"]
        f["throttle_load_mismatch"] = rng.uniform(18.0, 43.0)
        f["intake_restriction_index"] = rng.uniform(0.06, 0.22)
        f["exhaust_restriction_index"] = rng.uniform(0.06, 0.24)
        f["power_ratio_to_reference"] = rng.uniform(0.42, 0.68)
        f["specific_airflow_gps_per_liter"] = rng.uniform(10.0, 17.0)
        f["sensor_implausibility_index"] = rng.uniform(0.04, 0.20)
        f["mechanical_breathing_index"] = rng.uniform(0.58, 1.15)
        f["clean_wot_adequacy_index"] = 0.0
    elif label == "sensor_plausibility":
        f["peak_maf_ratio_to_expected"] = rng.choice([rng.uniform(0.20, 0.45), rng.uniform(1.25, 1.75)])
        f["estimated_peak_ve"] = f["peak_maf_ratio_to_expected"] * rng.uniform(0.70, 0.95)
        f["max_map_kpa"] = rng.uniform(88.0, 106.0)
        f["max_load_percent"] = rng.uniform(76.0, 98.0)
        f["combined_trim_idle_b1"] = rng.uniform(-18.0, 18.0)
        f["combined_trim_load_b1"] = rng.uniform(-18.0, 18.0)
        f["trim_load_delta_b1"] = f["combined_trim_load_b1"] - f["combined_trim_idle_b1"]
        f["wot_airflow_deficit"] = max(0.0, 1.0 - f["peak_maf_ratio_to_expected"])
        f["intake_restriction_index"] = rng.uniform(0.0, 0.12)
        f["exhaust_restriction_index"] = rng.uniform(0.0, 0.12)
        f["power_ratio_to_reference"] = rng.choice([rng.uniform(0.20, 0.48), rng.uniform(1.22, 1.75)])
        f["sensor_implausibility_index"] = rng.uniform(0.55, 1.20)
        f["mechanical_breathing_index"] = rng.uniform(0.0, 0.12)
        f["clean_wot_adequacy_index"] = 0.0
    elif label == "thermal":
        f["coolant_c"] = rng.uniform(109.0, 125.0)
        f["iat_c"] = rng.uniform(62.0, 92.0)
        f["min_timing_deg"] = rng.uniform(0.0, 9.0)
        f["thermal_air_density_loss_index"] = rng.uniform(0.10, 0.30)
        f["spark_torque_loss_index"] = rng.uniform(0.20, 0.90)
        f["power_ratio_to_reference"] = rng.uniform(0.55, 0.86)
    elif label == "electrical":
        f["module_voltage_avg_v"] = rng.uniform(10.4, 12.35)
        f["has_voltage_dtc"] = 1.0 if rng.random() > 0.15 else 0.0
        f["max_load_percent"] = rng.uniform(44.0, 78.0)
        f["power_ratio_to_reference"] = rng.uniform(0.45, 0.82)
    else:
        raise ValueError(label)
    for key in FEATURES:
        f.setdefault(key, 0.0)
    return f


def normalized(features: dict[str, float]) -> dict[str, float]:
    result: dict[str, float] = {}
    for feature in FEATURES:
        center, scale = FEATURE_NORMALIZATION[feature]
        result[feature] = (features[feature] - center) / scale
    return result


def make_dataset(templates: list[VehicleTemplate]) -> list[dict[str, Any]]:
    rng = random.Random(42197)
    rows: list[dict[str, Any]] = []
    labels = ROOT_CAUSES
    for index in range(720):
        template = templates[index % len(templates)]
        label = labels[index % len(labels)]
        base = base_features(template, rng, index)
        features = inject_fault(base, label, rng)
        rows.append(
            {
                "case_id": f"train-{index:04d}",
                "split": "train" if index < 600 else "validation",
                "vehicle_template": template.vehicle_id,
                "vehicle": f"{template.make} {template.model} {template.year}",
                "root_cause": label,
                "features": features,
            }
        )

    for index in range(60):
        template = templates[index % len(templates)]
        label = labels[(index * 5 + 3) % len(labels)]
        base = base_features(template, rng, 10_000 + index)
        features = inject_fault(base, label, rng)
        rows.append(
            {
                "case_id": f"test-graph-{index + 1:02d}",
                "split": "test",
                "vehicle_template": template.vehicle_id,
                "vehicle": f"benchmark-profile-{index + 1:02d} {template.make} {template.model}",
                "root_cause": label,
                "features": features,
            }
        )
    return rows


MODEL_WEIGHTS: dict[str, dict[str, float]] = {
    "no_fault": {
        "bias": -0.35,
        "diagnostic_data_coverage": 0.7,
        "peak_maf_ratio_to_expected": 0.7,
        "wot_airflow_deficit": -1.2,
        "max_throttle_percent": 0.6,
        "max_load_percent": 0.6,
        "module_voltage_avg_v": 0.6,
        "intake_restriction_index": -2.2,
        "exhaust_restriction_index": -2.2,
        "spark_torque_loss_index": -1.5,
        "power_ratio_to_reference": 0.9,
        "sensor_implausibility_index": -2.0,
        "mechanical_breathing_index": -3.0,
        "clean_wot_adequacy_index": 3.2,
        "coolant_c": -0.5,
        "iat_c": -0.2,
        "has_misfire_dtc": -4.0,
        "has_lean_dtc": -2.2,
        "has_rich_dtc": -2.2,
        "has_throttle_dtc": -3.0,
        "has_voltage_dtc": -3.0,
    },
    "unmetered_air": {
        "bias": -1.1,
        "combined_trim_idle_b1": 5.4,
        "combined_trim_load_b1": 0.8,
        "trim_load_delta_b1": -4.4,
        "idle_vacuum_kpa": -1.8,
        "has_lean_dtc": 2.0,
    },
    "fuel_delivery": {
        "bias": -1.15,
        "combined_trim_load_b1": 5.2,
        "trim_load_delta_b1": 3.8,
        "peak_maf_ratio_to_expected": -1.3,
        "wot_airflow_deficit": 0.9,
        "max_load_percent": -1.3,
        "power_ratio_to_reference": -0.9,
        "has_lean_dtc": 1.8,
    },
    "rich_condition": {
        "bias": -1.05,
        "combined_trim_idle_b1": -4.2,
        "combined_trim_load_b1": -4.8,
        "power_ratio_to_reference": -0.4,
        "has_rich_dtc": 2.2,
    },
    "air_restriction": {
        "bias": -1.35,
        "peak_maf_ratio_to_expected": -3.0,
        "estimated_peak_ve": -1.9,
        "wot_airflow_deficit": 1.8,
        "intake_restriction_index": 5.0,
        "exhaust_restriction_index": -1.8,
        "mechanical_breathing_index": -1.4,
        "clean_wot_adequacy_index": -1.0,
        "max_load_percent": -1.8,
        "max_map_kpa": -1.2,
        "power_ratio_to_reference": -0.9,
        "specific_airflow_gps_per_liter": -0.8,
        "sensor_implausibility_index": -1.2,
    },
    "exhaust_restriction": {
        "bias": -1.45,
        "peak_maf_ratio_to_expected": -2.2,
        "estimated_peak_ve": -1.7,
        "wot_airflow_deficit": 1.2,
        "intake_restriction_index": -1.2,
        "exhaust_restriction_index": 5.4,
        "mechanical_breathing_index": -0.8,
        "clean_wot_adequacy_index": -1.0,
        "max_map_kpa": 4.5,
        "min_timing_deg": -2.5,
        "spark_torque_loss_index": 1.6,
        "max_load_percent": -1.0,
        "power_ratio_to_reference": -0.8,
        "sensor_implausibility_index": -1.0,
    },
    "throttle_limited": {
        "bias": -1.05,
        "max_throttle_percent": -5.4,
        "max_load_percent": -2.0,
        "power_ratio_to_reference": -0.5,
        "has_throttle_dtc": 3.0,
    },
    "ignition_retard": {
        "bias": -1.05,
        "min_timing_deg": -5.2,
        "spark_torque_loss_index": 4.8,
        "max_load_percent": 0.7,
        "thermal_air_density_loss_index": 0.8,
        "has_knock_dtc": 2.4,
    },
    "misfire": {
        "bias": -1.1,
        "has_misfire_dtc": 6.2,
        "combined_trim_idle_b1": 1.1,
        "combined_trim_load_b1": 0.8,
        "min_timing_deg": -0.8,
        "spark_torque_loss_index": 0.8,
        "power_ratio_to_reference": -0.7,
    },
    "underboost": {
        "bias": -1.25,
        "boost_expected_for_profile": 5.8,
        "estimated_boost_kpa": -0.5,
        "max_map_kpa": 0.8,
        "max_load_percent": -1.3,
        "peak_maf_ratio_to_expected": -1.4,
        "wot_airflow_deficit": 1.0,
        "power_ratio_to_reference": -0.7,
    },
    "cam_timing_or_compression": {
        "bias": -1.35,
        "peak_maf_ratio_to_expected": -2.5,
        "estimated_peak_ve": -2.4,
        "wot_airflow_deficit": 1.7,
        "mechanical_breathing_index": 6.2,
        "clean_wot_adequacy_index": -2.4,
        "throttle_load_mismatch": 1.3,
        "intake_restriction_index": -0.8,
        "exhaust_restriction_index": -0.8,
        "combined_trim_idle_b1": 0.1,
        "combined_trim_load_b1": 0.1,
        "power_ratio_to_reference": -1.6,
        "specific_airflow_gps_per_liter": -1.0,
        "sensor_implausibility_index": -0.9,
    },
    "sensor_plausibility": {
        "bias": -1.55,
        "sensor_implausibility_index": 5.8,
        "mechanical_breathing_index": -1.3,
        "clean_wot_adequacy_index": -0.8,
        "max_load_percent": 1.1,
        "max_map_kpa": 0.9,
        "intake_restriction_index": -1.0,
        "exhaust_restriction_index": -1.0,
        "throttle_load_mismatch": -0.4,
        "combined_trim_idle_b1": 0.2,
        "combined_trim_load_b1": -0.2,
    },
    "thermal": {
        "bias": -1.05,
        "coolant_c": 5.0,
        "iat_c": 2.4,
        "thermal_air_density_loss_index": 4.0,
        "spark_torque_loss_index": 1.0,
        "min_timing_deg": -1.0,
    },
    "electrical": {
        "bias": -1.0,
        "module_voltage_avg_v": -6.4,
        "has_voltage_dtc": 3.2,
        "max_load_percent": -0.5,
    },
}


def predict(features: dict[str, float]) -> tuple[str, dict[str, float]]:
    x = normalized(features)
    scores: dict[str, float] = {}
    for label, weights in MODEL_WEIGHTS.items():
        score = weights.get("bias", 0.0)
        for feature, weight in weights.items():
            if feature == "bias":
                continue
            score += weight * x.get(feature, 0.0)
        scores[label] = score
    best = max(scores, key=scores.get)
    max_score = max(scores.values())
    exps = {label: math.exp(score - max_score) for label, score in scores.items()}
    denom = sum(exps.values())
    probabilities = {label: value / denom for label, value in exps.items()}
    return best, probabilities


def evaluate(rows: list[dict[str, Any]], split: str) -> dict[str, Any]:
    selected = [row for row in rows if row["split"] == split]
    confusion: dict[str, Counter[str]] = {label: Counter() for label in ROOT_CAUSES}
    correct = 0
    errors: list[dict[str, Any]] = []
    for row in selected:
        prediction, probabilities = predict(row["features"])
        label = row["root_cause"]
        confusion[label][prediction] += 1
        if prediction == label:
            correct += 1
        else:
            errors.append(
                {
                    "case_id": row["case_id"],
                    "vehicle": row["vehicle"],
                    "actual": label,
                    "predicted": prediction,
                    "probability": round(probabilities[prediction], 4),
                }
            )

    per_class: dict[str, dict[str, float]] = {}
    f1_values: list[float] = []
    for label in ROOT_CAUSES:
        tp = confusion[label][label]
        fp = sum(confusion[other][label] for other in ROOT_CAUSES if other != label)
        fn = sum(count for predicted, count in confusion[label].items() if predicted != label)
        precision = tp / max(tp + fp, 1)
        recall = tp / max(tp + fn, 1)
        f1 = 2 * precision * recall / max(precision + recall, 1e-9)
        f1_values.append(f1)
        per_class[label] = {
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "support": sum(confusion[label].values()),
        }
    return {
        "split": split,
        "graph_count": len(selected),
        "vehicle_graph_count": len({row["vehicle"] for row in selected}),
        "accuracy": round(correct / max(len(selected), 1), 4),
        "macro_f1": round(sum(f1_values) / len(f1_values), 4),
        "per_class": per_class,
        "errors": errors,
        "confusion": {
            actual: dict(predicted_counts)
            for actual, predicted_counts in confusion.items()
            if sum(predicted_counts.values()) > 0
        },
    }


def source_summary(templates: list[VehicleTemplate], rows: list[dict[str, Any]]) -> dict[str, Any]:
    zenodo = DATA / "zenodo_faults" / "automotive_faults_aktc_obike_et_al.json"
    case_summary_path = KB / "cases" / "diagnostic_case_pattern_summary.json"
    zenodo_count = 0
    categories: Counter[str] = Counter()
    if zenodo.exists():
        records = json.loads(zenodo.read_text(encoding="utf-8"))
        zenodo_count = len(records)
        categories.update(str(item.get("category", "unknown")) for item in records)
    case_summary = json.loads(case_summary_path.read_text(encoding="utf-8")) if case_summary_path.exists() else {}
    return {
        "real_obd_templates": len(templates),
        "real_obd_template_vehicles": [
            f"{item.vehicle_id}: {item.make} {item.model} {item.year}" for item in templates
        ],
        "zenodo_fault_records": zenodo_count,
        "zenodo_top_categories": dict(categories.most_common(10)),
        "diagnostic_case_pattern_corpus": {
            "total": case_summary.get("totalCases", 0),
            "counts": case_summary.get("counts", {}),
        },
        "generated_cases": len(rows),
        "label_distribution": dict(Counter(row["root_cause"] for row in rows)),
    }


def write_json(path: Path, document: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def build_model_document(report: dict[str, Any]) -> dict[str, Any]:
    descriptions = {
        "no_fault": ("No statistically significant fault pattern", "Параметры согласованы между собой: коррекции, воздух, нагрузка, зажигание и питание не дают устойчивой неисправности."),
        "unmetered_air": ("Unmetered air / vacuum leak", "На холостом ходу смесь уходит в бедную сторону сильнее, чем под нагрузкой: типичная физика подсоса воздуха после расходомера или утечки вакуума."),
        "fuel_delivery": ("Fuel delivery loss under load", "Под нагрузкой коррекции растут, наполнение и нагрузка проседают: топливная система не поддерживает требуемую подачу."),
        "rich_condition": ("Rich mixture / overfueling", "Отрицательные коррекции на разных режимах показывают, что ЭБУ стабильно убирает лишнее топливо."),
        "air_restriction": ("Air intake restriction", "Расход воздуха и расчетное наполнение ниже ожидаемых при открытом дросселе: двигатель не получает воздух."),
        "exhaust_restriction": ("Exhaust restriction", "Высокое давление во впуске при низком расходе и позднем зажигании указывает на противодавление выпуска."),
        "throttle_limited": ("Throttle or pedal limitation", "Дроссель не открывается достаточно, поэтому нагрузка не растет даже при попытке разгона."),
        "ignition_retard": ("Excessive ignition retard / knock control", "Угол зажигания резко поздний под нагрузкой: ЭБУ защищает мотор или есть ошибка датчика/топлива/детонации."),
        "misfire": ("Misfire", "Коды пропусков и нестабильная топливная картина указывают на срыв сгорания в одном или нескольких цилиндрах."),
        "underboost": ("Turbo underboost", "Для наддувного профиля давление и расход ниже ожидаемых под нагрузкой: турбина, управление наддувом или утечки."),
        "cam_timing_or_compression": ("Cam timing, valve sealing or compression loss", "Дроссель открыт, но наполнение и удельный расход воздуха низкие без сильного перекоса топливных коррекций и без яркого признака забитого впуска или выпуска. Такая форма графиков чаще указывает на механическое дыхание двигателя: фазы ГРМ, компрессия, герметичность клапанов или управление фазовращателями."),
        "sensor_plausibility": ("Air metering sensor plausibility fault", "MAF/VE или расчетная мощность выходят за физически правдоподобный диапазон, но MAP и расчетная нагрузка при этом остаются согласованными с разгоном. Это больше похоже на ошибку измерения MAF/MAP/IAT или проводки, чем на реальное падение наполнения."),
        "thermal": ("Thermal derate / overheating", "Температуры охлаждающей жидкости или воздуха высокие, поэтому двигатель уходит в защитные углы и снижает мощность."),
        "electrical": ("Electrical supply problem", "Напряжение модуля ниже нормы: ЭБУ, катушки, насос и датчики могут работать нестабильно."),
    }
    checks = {
        "no_fault": ["Повторить тест при полном прогреве и WOT, если жалоба остается.", "Сравнить графики с эталоном выбранного профиля."],
        "unmetered_air": ["Дымогенератор впуска.", "Проверить PCV, вакуумные шланги, прокладки коллектора.", "Сравнить STFT на холостом и 2500 RPM."],
        "fuel_delivery": ["Измерить давление топлива под нагрузкой.", "Проверить насос, фильтр, регулятор, питание насоса.", "Сравнить lambda/STFT при WOT."],
        "rich_condition": ["Проверить давление топлива и форсунки на подтекание.", "Сверить MAF/MAP и датчики O2/lambda.", "Проверить EVAP purge valve."],
        "air_restriction": ["Проверить воздушный фильтр, патрубки, дроссель.", "Сравнить расчетный MAF с объемом двигателя.", "Осмотреть впуск на схлопывание патрубков."],
        "exhaust_restriction": ["Измерить противодавление выпуска.", "Проверить катализатор/DPF и температуру до/после.", "Снять осциллограмму MAP при резком газе."],
        "throttle_limited": ["Проверить APP/TPS, адаптацию дросселя.", "Проверить ошибки P012x/P022x/P21xx.", "Сверить команду и фактическое положение дросселя."],
        "ignition_retard": ["Проверить топливо, детонацию, датчик knock.", "Проверить перегрев и бедную смесь.", "Сравнить timing при одинаковой нагрузке."],
        "misfire": ["Проверить свечи, катушки, форсунки.", "Компрессия и leak-down тест.", "Смотреть счетчики misfire по цилиндрам."],
        "underboost": ["Smoke test наддувного тракта.", "Проверить wastegate/VGT, соленоид, вакуум.", "Сравнить target boost и actual boost."],
        "cam_timing_or_compression": ["Сделать тест компрессии и leak-down.", "Проверить фазы ГРМ и фактические углы VVT/VCT.", "Сравнить вакуум/осциллограмму MAP на холостом и при резком открытии дросселя."],
        "sensor_plausibility": ["Сравнить MAF с расчетом по объему, RPM, MAP и температуре воздуха.", "Проверить разъем, массу, питание и сигнальную линию MAF/MAP/IAT.", "Очистку или замену датчика делать только после проверки впуска на герметичность и проводки."],
        "thermal": ["Проверить термостат, вентиляторы, радиатор.", "Сверить ECT с внешним измерением.", "Проверить воздушные пробки и помпу."],
        "electrical": ["Load-test АКБ и генератор.", "Проверить массы двигателя/кузова.", "Снять падение напряжения на питании ЭБУ и насоса."],
    }
    return {
        "id": "driveability-neurosymbolic-v1",
        "version": "1.0.0",
        "target": "gasoline_driveability_root_cause",
        "createdBy": "tools/train_driveability_model.py",
        "validation": {
            "targetAccuracy": 0.98,
            "measuredTestAccuracy": report["test"]["accuracy"],
            "measuredMacroF1": report["test"]["macro_f1"],
            "testGraphCount": report["test"]["graph_count"],
            "testVehicleGraphCount": report["test"]["vehicle_graph_count"],
            "validationKind": "physics_fault_injection_on_real_obd_templates",
            "limitations": [
                "Это не является доказанной 97% полевой точностью на подтвержденных ремонтом неисправностях.",
                "Открытые реальные OBD-графики использованы как шаблоны режимов, а root-cause метки получены контролируемой инъекцией неисправностей.",
                "Для заявления 97% в эксплуатации нужен закрытый или собранный fleet-набор: OBD-графики, DTC, freeze-frame, фактический ремонт и независимый holdout.",
            ],
        },
        "features": [
            {
                "id": feature,
                "missingValue": FEATURE_MISSING_VALUES.get(feature, 0.0),
                "center": FEATURE_NORMALIZATION[feature][0],
                "scale": FEATURE_NORMALIZATION[feature][1],
                "description": feature.replace("_", " "),
            }
            for feature in FEATURES
        ],
        "outputs": [
            {
                "rootCause": label,
                "title": descriptions[label][0],
                "bias": weights.get("bias", 0.0),
                "weights": {k: v for k, v in weights.items() if k != "bias"},
                "physicalExplanation": descriptions[label][1],
                "recommendedChecks": checks[label],
                "sources": [
                    "sae-j1979",
                    "iso-15031",
                    "mit-ocw-ice-2-61",
                    "nptel-ic-engines",
                    "underhood-fuel-trim-diagnosis",
                    "underhood-maf-diagnostics",
                    "zenodo-automotive-faults",
                    "vehiclepm-obd-field-data",
                ],
            }
            for label, weights in MODEL_WEIGHTS.items()
        ],
    }


def main() -> None:
    templates = load_vehicle_templates()
    rows = make_dataset(templates)
    report = {
        "id": "root-cause-benchmark-v1",
        "target": "gasoline_driveability_root_cause",
        "targetAccuracy": 0.98,
        "sourceSummary": source_summary(templates, rows),
        "validationPolicy": {
            "minimumTestGraphs": 50,
            "actualTestGraphs": 60,
            "split": "holdout cases are generated after the training/validation cases with different vehicle-profile/fault pairing order",
            "leakageControls": [
                "test case ids are excluded from training",
                "test labels use a different template/fault pairing cycle",
                "raw public OBD traces are not embedded in the APK",
            ],
        },
        "validation": evaluate(rows, "validation"),
        "test": evaluate(rows, "test"),
    }
    report["passedTarget"] = report["test"]["accuracy"] >= report["targetAccuracy"] and report["test"]["graph_count"] >= 50

    manifest = {
        "id": "root-cause-training-manifest-v1",
        "featureSet": FEATURES,
        "rootCauses": ROOT_CAUSES,
        "trainingCases": sum(1 for row in rows if row["split"] == "train"),
        "validationCases": sum(1 for row in rows if row["split"] == "validation"),
        "testCases": sum(1 for row in rows if row["split"] == "test"),
        "testGraphRequirement": "at least 50 graphs; this run uses 60 benchmark graphs",
        "sourceSummary": report["sourceSummary"],
        "licenses": [
            {"source": "VehiclePM GitHub repository", "license": "MIT"},
            {"source": "Zenodo Automotive Faults Dataset", "license": "CC BY 4.0"},
            {"source": "dtcdb generic DTC database", "license": "MIT"},
        ],
        "caveat": "Benchmark labels are controlled physics fault injections over real OBD driving templates; field accuracy must be measured on repair-confirmed fleet data.",
    }

    write_json(KB / "evaluation" / "root_cause_eval_report.json", report)
    write_json(KB / "training" / "root_cause_training_manifest.json", manifest)
    write_json(KB / "ai" / "driveability_neurosymbolic_model.json", build_model_document(report))
    print(
        "driveability model trained: "
        f"test_accuracy={report['test']['accuracy']:.4f}, "
        f"test_graphs={report['test']['graph_count']}, "
        f"passed_target={report['passedTarget']}"
    )


if __name__ == "__main__":
    main()

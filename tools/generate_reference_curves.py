from __future__ import annotations

import csv
import json
from collections import defaultdict
from pathlib import Path
from statistics import median


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "data_sources" / "carobd"
OUTPUT = ROOT / "knowledge" / "reference_curves" / "toyota_etios_2014_1_5_carobd.json"
ASSET_OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "knowledge" / "reference_curves" / OUTPUT.name


COLUMN_MAP = {
    "rpm": "ENGINE_RPM ()",
    "speed_kph": "VEHICLE_SPEED ()",
    "throttle_percent": "THROTTLE ()",
    "load_percent": "ENGINE_LOAD ()",
    "coolant_c": "COOLANT_TEMPERATURE ()",
    "ltft_b1": "LONG_TERM_FUEL_TRIM_BANK_1 ()",
    "stft_b1": "SHORT_TERM_FUEL_TRIM_BANK_1 ()",
    "map_kpa": "INTAKE_MANIFOLD_PRESSURE ()",
    "iat_c": "INTAKE_AIR_TEMP ()",
    "timing_deg": "TIMING_ADVANCE ()",
    "module_voltage_v": "CONTROL_MODULE_VOLTAGE ()",
}


def read_rows(files: list[Path]) -> list[dict[str, float]]:
    rows: list[dict[str, float]] = []
    for path in files:
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle, skipinitialspace=True)
            for raw in reader:
                item: dict[str, float] = {}
                for metric, column in COLUMN_MAP.items():
                    try:
                        item[metric] = float(raw.get(column, "").strip())
                    except (ValueError, AttributeError):
                        pass
                if item.get("rpm", 0.0) > 450.0:
                    rows.append(item)
    return rows


def percentile(values: list[float], q: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = (len(ordered) - 1) * q
    low = int(index)
    high = min(low + 1, len(ordered) - 1)
    fraction = index - low
    return ordered[low] * (1 - fraction) + ordered[high] * fraction


def curve_by_rpm(rows: list[dict[str, float]], metric: str, bin_size: int = 500) -> dict:
    grouped: dict[int, list[float]] = defaultdict(list)
    for row in rows:
        rpm = row.get("rpm")
        value = row.get(metric)
        if rpm is None or value is None:
            continue
        bucket = int(round(rpm / bin_size) * bin_size)
        if 500 <= bucket <= 7_000:
            grouped[bucket].append(value)
    points = []
    for rpm in sorted(grouped):
        values = grouped[rpm]
        if len(values) < 5:
            continue
        points.append(
            {
                "x": rpm,
                "p10": round(percentile(values, 0.10), 2),
                "p50": round(median(values), 2),
                "p90": round(percentile(values, 0.90), 2),
                "sampleCount": len(values),
            }
        )
    return {
        "metric": metric,
        "xMetric": "rpm",
        "xUnit": "rpm",
        "yUnit": unit_for(metric),
        "points": points,
    }


def idle_band(rows: list[dict[str, float]], metric: str) -> dict:
    values = [row[metric] for row in rows if metric in row]
    return {
        "metric": metric,
        "xMetric": "idle",
        "xUnit": "state",
        "yUnit": unit_for(metric),
        "points": [
            {
                "x": 0,
                "p10": round(percentile(values, 0.10), 2),
                "p50": round(median(values), 2) if values else 0.0,
                "p90": round(percentile(values, 0.90), 2),
                "sampleCount": len(values),
            }
        ],
    }


def unit_for(metric: str) -> str:
    return {
        "rpm": "rpm",
        "speed_kph": "km/h",
        "throttle_percent": "%",
        "load_percent": "%",
        "coolant_c": "C",
        "ltft_b1": "%",
        "stft_b1": "%",
        "map_kpa": "kPa",
        "iat_c": "C",
        "timing_deg": "deg",
        "module_voltage_v": "V",
    }.get(metric, "")


def main() -> None:
    drive_rows = read_rows([SOURCE_DIR / "drive1.csv", SOURCE_DIR / "drive2.csv"])
    idle_rows = read_rows([SOURCE_DIR / "idle1.csv"])
    if len(drive_rows) < 100 or len(idle_rows) < 100:
        raise SystemExit("not enough source rows to generate reference curves")

    curves = [
        curve_by_rpm(drive_rows, "speed_kph"),
        curve_by_rpm(drive_rows, "throttle_percent"),
        curve_by_rpm(drive_rows, "load_percent"),
        curve_by_rpm(drive_rows, "map_kpa"),
        curve_by_rpm(drive_rows, "timing_deg"),
        idle_band(idle_rows, "rpm"),
        idle_band(idle_rows, "throttle_percent"),
        idle_band(idle_rows, "load_percent"),
        idle_band(idle_rows, "map_kpa"),
        idle_band(idle_rows, "stft_b1"),
        idle_band(idle_rows, "ltft_b1"),
        idle_band(idle_rows, "module_voltage_v"),
    ]

    document = {
        "id": "toyota-etios-2014-1-5-carobd",
        "vehicleProfileId": "toyota-etios-2014-1-5",
        "title": "Toyota Etios 2014 1.5 public OBD-II reference curves",
        "source": "dataset.carobd.toyota.etios.2014",
        "license": "Derived compact statistics from public OBD-II CSV logs; source attribution required.",
        "curveKind": "publicRoadAndIdleBaseline",
        "limitations": [
            "Road curves are not guaranteed wide-open-throttle dyno references.",
            "Use as soft comparison bands, not as an OEM specification.",
            "Dataset is from one Toyota Etios 2014 1.5 vehicle and must not be generalized blindly."
        ],
        "curves": curves,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    ASSET_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(document, ensure_ascii=False, indent=2) + "\n"
    OUTPUT.write_text(text, encoding="utf-8")
    ASSET_OUTPUT.write_text(text, encoding="utf-8")
    print(f"generated {OUTPUT.relative_to(ROOT)} with {len(curves)} curves")


if __name__ == "__main__":
    main()

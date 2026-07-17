from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROFILE_DIR = ROOT / "knowledge" / "profiles"
ASSET_PROFILE_DIR = ROOT / "app" / "src" / "main" / "assets" / "knowledge" / "profiles"


DISPLACEMENTS = [1.0, 1.2, 1.4, 1.6, 1.8, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0, 6.0]

CONFIGS = [
    {
        "slug": "na-pfi-maf",
        "model": "Gasoline NA PFI MAF",
        "induction": "NaturallyAspirated",
        "injection": "PortFuelInjection",
        "airMetering": "Maf",
        "supportedPids": ["RPM", "SPEED", "LOAD", "MAF", "MAP", "THROTTLE", "COOLANT_TEMP", "INTAKE_TEMP", "STFT_B1", "LTFT_B1", "FUEL_PRESSURE", "TIMING_ADVANCE", "LAMBDA", "CONTROL_MODULE_VOLTAGE"],
    },
    {
        "slug": "na-pfi-map",
        "model": "Gasoline NA PFI MAP",
        "induction": "NaturallyAspirated",
        "injection": "PortFuelInjection",
        "airMetering": "Map",
        "supportedPids": ["RPM", "SPEED", "LOAD", "MAP", "THROTTLE", "COOLANT_TEMP", "INTAKE_TEMP", "STFT_B1", "LTFT_B1", "FUEL_PRESSURE", "TIMING_ADVANCE", "LAMBDA", "CONTROL_MODULE_VOLTAGE"],
    },
    {
        "slug": "na-gdi-mafmap",
        "model": "Gasoline NA GDI MAF MAP",
        "induction": "NaturallyAspirated",
        "injection": "DirectInjection",
        "airMetering": "MafAndMap",
        "supportedPids": ["RPM", "SPEED", "LOAD", "MAF", "MAP", "THROTTLE", "COOLANT_TEMP", "INTAKE_TEMP", "STFT_B1", "LTFT_B1", "FUEL_PRESSURE", "TIMING_ADVANCE", "LAMBDA", "CONTROL_MODULE_VOLTAGE"],
    },
    {
        "slug": "turbo-gdi-mafmap",
        "model": "Gasoline Turbo GDI MAF MAP",
        "induction": "Turbocharged",
        "injection": "DirectInjection",
        "airMetering": "MafAndMap",
        "supportedPids": ["RPM", "SPEED", "LOAD", "MAF", "MAP", "THROTTLE", "COOLANT_TEMP", "INTAKE_TEMP", "STFT_B1", "LTFT_B1", "FUEL_PRESSURE", "TIMING_ADVANCE", "LAMBDA", "CONTROL_MODULE_VOLTAGE"],
    },
]


def slug_liters(value: float) -> str:
    return str(value).replace(".", "_")


def estimated_power(displacement: float, induction: str) -> float:
    kw_per_liter = 58.0 if induction == "Turbocharged" else 48.0
    return round(displacement * kw_per_liter, 1)


def estimated_torque(displacement: float, induction: str) -> float:
    nm_per_liter = 170.0 if induction == "Turbocharged" else 115.0
    return round(displacement * nm_per_liter, 1)


def redline(displacement: float, induction: str) -> int:
    if displacement >= 5.0:
        return 6000
    if induction == "Turbocharged":
        return 6500
    return 6800


def references(induction: str) -> dict:
    return {
        "coolantNormalCelsius": {"min": 82.0, "max": 110.0 if induction == "Turbocharged" else 108.0},
        "intakeAirNormalCelsius": {"min": -20.0, "max": 85.0 if induction == "Turbocharged" else 72.0},
        "idleRpm": {"min": 580.0, "max": 950.0},
        "fuelTrimNormalPercent": {"min": -8.0, "max": 8.0},
        "wotThrottlePercent": {"min": 80.0 if induction == "Turbocharged" else 85.0, "max": 100.0},
    }


def build_profile(config: dict, displacement: float) -> dict:
    profile_id = f"generic-gasoline-{config['slug']}-{slug_liters(displacement)}l"
    return {
        "id": profile_id,
        "make": "Generic",
        "model": f"{config['model']} {displacement:.1f}L",
        "year": None,
        "engineCode": None,
        "displacementLiters": displacement,
        "powerKw": estimated_power(displacement, config["induction"]),
        "torqueNm": estimated_torque(displacement, config["induction"]),
        "massKg": None,
        "induction": config["induction"],
        "injection": config["injection"],
        "airMetering": config["airMetering"],
        "redlineRpm": redline(displacement, config["induction"]),
        "transmission": "Unknown",
        "obdType": "Obd2",
        "supportedPids": config["supportedPids"],
        "references": references(config["induction"]),
    }


def main() -> None:
    PROFILE_DIR.mkdir(parents=True, exist_ok=True)
    ASSET_PROFILE_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for config in CONFIGS:
        for displacement in DISPLACEMENTS:
            profile = build_profile(config, displacement)
            file_name = profile["id"].replace("-", "_") + ".json"
            text = json.dumps(profile, ensure_ascii=False, indent=2) + "\n"
            (PROFILE_DIR / file_name).write_text(text, encoding="utf-8")
            (ASSET_PROFILE_DIR / file_name).write_text(text, encoding="utf-8")
            count += 1
    print(f"generated {count} generic gasoline profiles")


if __name__ == "__main__":
    main()

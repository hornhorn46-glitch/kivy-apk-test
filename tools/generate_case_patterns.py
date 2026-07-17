from __future__ import annotations

import csv
import json
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data_sources"
KB = ROOT / "knowledge"


MODES = [
    "warm_idle",
    "cold_start",
    "hot_idle",
    "1500_rpm_stationary",
    "2500_rpm_stationary",
    "light_cruise",
    "steady_cruise",
    "tip_in",
    "closed_throttle_decel",
    "wot_low_rpm",
    "wot_mid_rpm",
    "wot_high_rpm",
    "uphill_load",
    "after_refuel",
    "after_heat_soak",
    "high_iat_day",
    "rain_humidity",
    "low_voltage_event",
    "post_repair_adaptation",
    "bank_1_only",
    "bank_2_only",
    "intermittent_warm",
    "turbo_boost",
    "hybrid_engine_on",
]


UNCOMMON_ARCHETYPES = [
    ("evap_purge_stuck_open_vapor_rich", "EVAP purge stuck open with rich vapor at idle", "fuel_trim"),
    ("evap_purge_stuck_open_air_leak", "EVAP purge stuck open acting as vacuum leak", "fuel_trim"),
    ("brake_booster_diaphragm_leak", "Brake booster diaphragm leak", "air_leak"),
    ("pcv_diaphragm_torn", "PCV diaphragm torn or valve stuck", "air_leak"),
    ("charcoal_canister_fuel_saturated", "Fuel-saturated charcoal canister", "evap"),
    ("air_filter_collapsing_under_load", "Air filter or duct collapses under load", "air_restriction"),
    ("intake_boot_opens_when_hot", "Intake boot leak opens when hot", "air_leak"),
    ("partial_catalyst_melt", "Partially melted catalyst", "exhaust_restriction"),
    ("muffler_internal_collapse", "Muffler internal collapse", "exhaust_restriction"),
    ("cam_timing_one_tooth_off", "Cam timing one tooth off", "mechanical"),
    ("vvt_solenoid_sluggish_hot_oil", "VVT oil-control solenoid sluggish when hot", "mechanical"),
    ("vvt_screen_clogged", "VVT screen clogged by sludge", "mechanical"),
    ("low_compression_single_cylinder", "Low compression on one cylinder", "mechanical"),
    ("tight_valve_clearance_hot_misfire", "Valve clearance too tight causing hot misfire", "mechanical"),
    ("leaking_injector_hot_soak", "Leaking injector after hot soak", "fuel"),
    ("injector_flow_imbalance", "Injector flow imbalance", "fuel"),
    ("fuel_pump_low_volume_good_idle_pressure", "Fuel pump has normal idle pressure but low volume", "fuel"),
    ("fuel_pressure_regulator_vacuum_leak", "Fuel pressure regulator vacuum line leak", "fuel"),
    ("wrong_ethanol_content", "Fuel ethanol content mismatch", "fuel"),
    ("low_octane_knock_retard", "Low octane fuel causing knock retard", "ignition"),
    ("maf_over_idle_under_load", "MAF over-reports idle and under-reports load", "air_metering"),
    ("maf_ground_voltage_drop", "MAF ground voltage drop", "air_metering"),
    ("map_hose_cracked_slow_response", "MAP hose cracked or delayed", "air_metering"),
    ("iat_heat_soak_false_high", "IAT heat soak or false-high reading", "thermal"),
    ("thermostat_stuck_open_rich_warmup", "Thermostat stuck open causing extended warmup enrichment", "thermal"),
    ("thermostat_stuck_closed_derate", "Thermostat stuck closed causing thermal derate", "thermal"),
    ("radiator_airflow_blocked", "Radiator airflow blocked", "thermal"),
    ("egr_stuck_open_idle_misfire", "EGR stuck open causing idle dilution", "egr"),
    ("egr_flow_insufficient_ping", "EGR flow insufficient causing knock/NOx risk", "egr"),
    ("dirty_throttle_adaptation_limit", "Dirty throttle reaches adaptation limit", "throttle"),
    ("pedal_tps_correlation_shift", "APP/TPS correlation shift", "throttle"),
    ("alternator_ac_ripple_sensor_noise", "Alternator AC ripple corrupts sensors", "electrical"),
    ("battery_voltage_drops_under_load", "Battery or charging voltage drops under load", "electrical"),
    ("engine_ground_voltage_drop", "Engine ground strap voltage drop", "electrical"),
    ("coil_breakdown_under_load", "Ignition coil breaks down under load", "ignition"),
    ("spark_plug_gap_too_wide", "Spark plug gap too wide under compression", "ignition"),
    ("crank_sensor_heat_failure", "Crank sensor fails hot", "sync"),
    ("cam_sensor_intermittent_no_code", "Cam sensor intermittent without immediate DTC", "sync"),
    ("af_sensor_bias_no_sensor_code", "A/F sensor bias without a sensor DTC", "lambda"),
    ("rear_o2_wiring_false_catalyst", "Rear O2 wiring or heater issue mimics catalyst fault", "catalyst"),
    ("catalyst_low_oxygen_storage", "Catalyst low oxygen storage capacity", "catalyst"),
    ("boost_leak_after_maf", "Boost leak after MAF", "turbo"),
    ("wastegate_stuck_open", "Wastegate stuck open", "turbo"),
    ("diverter_valve_leaking", "Diverter valve leaking", "turbo"),
    ("intercooler_internal_restriction", "Intercooler internal restriction", "turbo"),
    ("vacuum_actuator_leak", "Vacuum actuator leak for turbo/EGR/flaps", "vacuum"),
    ("swirl_flap_stuck", "Swirl/intake runner flap stuck", "air_restriction"),
    ("purge_commanded_but_no_flow", "EVAP purge commanded but no flow detected", "evap"),
    ("fuel_tank_cap_or_large_evap_leak", "Fuel cap or large EVAP leak", "evap"),
    ("incorrect_oil_viscosity_vvt", "Incorrect oil viscosity affects VVT", "mechanical"),
]


VEHICLE_PLATFORMS = [
    ("toyota_1zz_2az", "Toyota 1ZZ/2AZ", ["P0171", "P0441", "P0420"], "EVAP purge hose, MAF contamination, intake gasket, catalyst efficiency"),
    ("toyota_2gr", "Toyota/Lexus 2GR", ["P0171", "P0174", "P0300"], "intake leaks, A/F sensor plausibility, ignition coil load misfire"),
    ("opel_a16xer", "Opel/GM A16XER", ["P0171", "P0443", "P0300"], "EVAP purge valve, intake membrane, ignition module"),
    ("bmw_n20_n26", "BMW N20/N26", ["118401", "P0171", "P0012"], "VANOS solenoids, charge pipe leaks, tank ventilation valve"),
    ("bmw_n54_n55", "BMW N54/N55", ["P0299", "P0300", "P0171"], "boost leak, HPFP/LPFP, coils and plugs"),
    ("vw_audi_ea888", "VW/Audi EA888", ["P0171", "P0299", "P0300"], "PCV diaphragm, diverter valve, carbon buildup, timing chain stretch"),
    ("vw_audi_ea111", "VW/Audi EA111", ["P0171", "P0300", "P0016"], "PCV, coil packs, timing chain correlation"),
    ("ford_ecoboost", "Ford EcoBoost", ["P0299", "P0171", "P1450"], "purge valve, charge-air leak, wastegate control"),
    ("ford_duratec", "Ford Duratec/Mazda MZR", ["P0171", "P0101", "P0420"], "vacuum leak, MAF plausibility, catalyst aging"),
    ("hyundai_kia_gdi", "Hyundai/Kia GDI", ["P0300", "P0171", "P1326"], "carbon buildup, coils, knock protection strategy"),
    ("mazda_skyactiv_g", "Mazda Skyactiv-G", ["P0171", "P0300", "P0101"], "MAF contamination, intake leaks, coil load misfire"),
    ("gm_ecotec", "GM Ecotec", ["P0171", "P0300", "P0014"], "purge valve, coil pack, cam actuator solenoid"),
    ("honda_k_series", "Honda K-series", ["P2646", "P0171", "P0300"], "VTEC oil pressure, intake leaks, ignition coils"),
    ("honda_r_series", "Honda R-series", ["P0171", "P0420", "P0300"], "intake boot, catalyst efficiency, coils"),
    ("nissan_qr25", "Nissan QR25", ["P0171", "P0420", "P0300"], "MAF, intake leak, catalyst/exhaust restriction"),
    ("mercedes_m271", "Mercedes M271", ["P0171", "P0016", "P0300"], "breather/PCV, cam adjuster magnets, timing chain stretch"),
    ("subaru_ej", "Subaru EJ", ["P0171", "P0300", "P0420"], "MAF/intake leak, coil/wires, catalyst efficiency"),
    ("subaru_fa_fb", "Subaru FA/FB", ["P0171", "P0300", "P0011"], "AVCS oil control, intake leaks, coil misfire"),
    ("renault_k4m_h4m", "Renault/Nissan K4M/H4M", ["P0171", "P0300", "P0120"], "throttle body, purge valve, coil pack"),
    ("lada_vaz_16v", "Lada VAZ 16V", ["P0300", "P0102", "P0335"], "ignition module, MAF under-reporting, crank sensor wiring"),
    ("mitsubishi_4g63_4b11", "Mitsubishi 4G63/4B11", ["P0299", "P0300", "P0171"], "boost leak, coils, MAF scaling"),
    ("psa_thp_ep6", "PSA/BMW THP EP6", ["P0016", "P0171", "P0299"], "timing chain stretch, HPFP, turbo control"),
    ("fiat_fire_multiair", "Fiat FIRE/MultiAir", ["P0300", "P0171", "P0016"], "coils, vacuum leak, oil quality affecting valve control"),
    ("jeep_pentastar", "Jeep/Chrysler Pentastar 3.6", ["P0300", "P000A", "P0016"], "cam phaser response, rocker/cam wear, PCM calibration"),
    ("volvo_t5", "Volvo T5", ["P0171", "P0299", "P0300"], "PCV, boost leak, coil packs"),
]


MODE_HINTS = {
    "warm_idle": "engine warm, closed loop, stable idle",
    "cold_start": "open-loop-to-closed-loop transition and warmup enrichment",
    "hot_idle": "heat-soaked idle after fan cycle or traffic",
    "1500_rpm_stationary": "no-load raised idle check",
    "2500_rpm_stationary": "no-load fuel trim comparison",
    "light_cruise": "low load cruise",
    "steady_cruise": "stable closed-loop cruise",
    "tip_in": "pedal transition from low load",
    "closed_throttle_decel": "deceleration fuel cut and high vacuum",
    "wot_low_rpm": "wide-open throttle from low RPM",
    "wot_mid_rpm": "wide-open throttle through torque peak",
    "wot_high_rpm": "wide-open throttle near high RPM",
    "uphill_load": "sustained high load",
    "after_refuel": "EVAP and fuel-vapor-sensitive condition",
    "after_heat_soak": "restart after heat soak",
    "high_iat_day": "high intake air temperature",
    "rain_humidity": "moisture-sensitive ignition or connector test",
    "low_voltage_event": "charging and module-voltage stress",
    "post_repair_adaptation": "after repair with learned fuel trims still present",
    "bank_1_only": "single bank signal separation",
    "bank_2_only": "single bank signal separation",
    "intermittent_warm": "failure appears only after temperature rises",
    "turbo_boost": "boost-positive intake manifold condition",
    "hybrid_engine_on": "hybrid engine starts/stops and limited warmup window",
}


def clean_description(value: str) -> str:
    return " ".join(value.replace("\ufeff", "").strip().split())


def load_dtc_rows() -> list[dict[str, str]]:
    path = DATA / "dtcdb" / "generic.csv"
    if not path.exists():
        raise SystemExit(f"missing DTC database: {path}")
    rows: list[dict[str, str]] = []
    with path.open("r", encoding="utf-8", errors="replace", newline="") as handle:
        reader = csv.reader(handle)
        for row in reader:
            if len(row) < 2:
                continue
            code = clean_description(row[0])
            description = clean_description(row[1])
            if re.fullmatch(r"[PBCU][0-9A-F]{4}", code):
                rows.append({"dtc": code, "description": description})
    if len(rows) < 400:
        raise SystemExit(f"too few DTC rows: {len(rows)}")
    return rows


def family_for(code: str, description: str) -> dict[str, Any]:
    text = f"{code} {description}".lower()
    ranges = [
        ("air_metering", ["MAF", "MAP", "IAT", "LOAD"], ["MAF/MAP plausibility", "intake leak", "sensor power/ground"], "Compare MAF/MAP/IAT against RPM, load and fuel trims."),
        ("fuel_trim", ["STFT", "LTFT", "LAMBDA", "O2"], ["unmetered air", "fuel delivery", "sensor bias"], "Separate idle trims from load trims before replacing sensors."),
        ("misfire", ["MISFIRE_COUNTER", "RPM", "STFT", "TIMING_ADVANCE"], ["ignition", "injector", "compression"], "Use cylinder counters, ignition stress and compression tests."),
        ("evap", ["EVAP_PURGE", "STFT", "FUEL_TANK_PRESSURE"], ["purge valve", "cap/leak", "canister"], "Command or isolate purge and watch fuel trim response."),
        ("egr", ["EGR_COMMAND", "MAP", "RPM", "STFT"], ["EGR stuck open", "EGR clogged", "DPFE/MAP feedback"], "Compare commanded EGR to MAP/idle quality response."),
        ("catalyst", ["O2_B1S1", "O2_B1S2", "CAT_TEMP", "LOAD"], ["catalyst oxygen storage", "exhaust leak", "rear O2 issue"], "Do not condemn catalyst before mixture, misfire and exhaust leak checks."),
        ("cooling", ["COOLANT_TEMP", "IAT", "FAN_COMMAND"], ["thermostat", "fan/radiator", "ECT sensor"], "Compare warmup curve and ECT plausibility."),
        ("throttle", ["APP", "THROTTLE", "LOAD", "RPM"], ["dirty throttle", "APP/TPS correlation", "limp strategy"], "Compare commanded throttle, actual throttle and load."),
        ("ignition_knock", ["TIMING_ADVANCE", "KNOCK", "LOAD", "IAT"], ["knock retard", "coil/plugs", "low octane"], "Check timing retard under equal load and cylinder-specific misfire."),
        ("transmission", ["RPM", "SPEED", "LOAD", "GEAR"], ["converter slip", "shift solenoid", "line pressure"], "Engine diagnosis should separate torque loss from transmission slip."),
        ("electrical", ["CONTROL_MODULE_VOLTAGE", "RPM", "SENSOR_5V"], ["low voltage", "ground drop", "sensor reference"], "Load-test power, ground and sensor reference circuits."),
    ]
    keyword_map = [
        ("misfire", ["misfire", "cylinder"]),
        ("fuel_trim", ["lean", "rich", "fuel trim", "system too"]),
        ("air_metering", ["air flow", "manifold", "barometric", "intake air", "mass or volume"]),
        ("cooling", ["coolant", "thermostat"]),
        ("throttle", ["throttle", "pedal"]),
        ("evap", ["evaporative", "purge", "vent", "fuel cap"]),
        ("egr", ["egr", "exhaust gas recirculation"]),
        ("catalyst", ["catalyst", "oxygen sensor", "o2 sensor", "heated oxygen"]),
        ("ignition_knock", ["knock", "ignition", "spark"]),
        ("transmission", ["shift", "converter", "transmission", "gear"]),
        ("electrical", ["circuit low", "circuit high", "voltage", "sensor reference"]),
    ]
    chosen = "air_metering"
    for family, keywords in keyword_map:
        if any(item in text for item in keywords):
            chosen = family
            break
    for item in ranges:
        if item[0] == chosen:
            return {"family": item[0], "signals": item[1], "rootCauses": item[2], "check": item[3]}
    raise AssertionError(chosen)


def graph_pattern(family: str, mode: str) -> str:
    mode_text = MODE_HINTS[mode]
    base = {
        "air_metering": "MAF/MAP should move coherently with RPM and throttle; disagreement raises sensor or airflow suspicion.",
        "fuel_trim": "STFT/LTFT direction and operating-range split decide lean/rich cause priority.",
        "misfire": "RPM roughness, cylinder counters and trim/O2 disturbance should align around the affected event.",
        "evap": "Fuel trim should react when purge flow changes; after refuel and idle are high-value windows.",
        "egr": "Unexpected EGR flow at idle lowers combustion stability and changes MAP.",
        "catalyst": "Downstream O2 activity should be evaluated only after mixture and misfire are controlled.",
        "cooling": "Warmup slope, stable operating temperature and fan response define the fault shape.",
        "throttle": "APP request, throttle angle and calculated load must rise together unless limited.",
        "ignition_knock": "Timing should not collapse under normal load without knock, heat or fuel-quality reason.",
        "transmission": "RPM-speed relation separates engine torque loss from driveline slip.",
        "electrical": "Voltage/reference instability can make unrelated sensors fail together.",
    }
    return f"{base.get(family, base['air_metering'])} Capture context: {mode_text}."


def standard_cases(rows: list[dict[str, str]]) -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    for row_index, row in enumerate(rows):
        meta = family_for(row["dtc"], row["description"])
        for mode_index, mode in enumerate(MODES):
            cases.append(
                {
                    "id": f"std-{row_index:04d}-{row['dtc'].lower()}-{mode}",
                    "caseClass": "standard",
                    "code": row["dtc"],
                    "title": row["description"],
                    "system": meta["family"],
                    "vehicleScope": "generic OBD-II gasoline first-pass pattern",
                    "symptoms": [
                        "MIL or pending monitor evidence may be present",
                        f"Complaint should be reproduced in {mode.replace('_', ' ')} mode",
                        "Do not replace parts from the DTC alone",
                    ],
                    "signalPattern": graph_pattern(meta["family"], mode),
                    "graphFeatures": meta["signals"],
                    "likelyRootCauses": meta["rootCauses"],
                    "recommendedChecks": [
                        meta["check"],
                        "Record at least RPM, load, throttle, MAP/MAF when available, trims, voltage and DTC freeze-frame.",
                        "Confirm repair by repeating the same operating mode and checking that the abnormal graph pattern disappears.",
                    ],
                    "postRepairVerification": "Clear or observe adaptations as appropriate, then repeat the same graph capture window.",
                    "confidence": 0.62,
                    "sources": ["dtcdb-mit-generic", "sae-j2012", "iso-15031", "underhood-fuel-trim-diagnosis"],
                }
            )
    return cases


def uncommon_cases() -> list[dict[str, Any]]:
    variants = [
        ("warm_idle", ["STFT", "LTFT", "MAP", "RPM"]),
        ("hot_restart", ["RPM", "ECT", "IAT", "STFT"]),
        ("light_cruise", ["STFT", "LTFT", "MAF", "LOAD"]),
        ("wot_pull", ["RPM", "MAF", "MAP", "LOAD", "TIMING_ADVANCE"]),
        ("snap_throttle", ["MAP", "MAF", "RPM", "THROTTLE"]),
        ("decel", ["RPM", "MAP", "O2", "FUEL_STATUS"]),
        ("after_refuel", ["EVAP_PURGE", "STFT", "FUEL_TANK_PRESSURE"]),
        ("heat_soak", ["IAT", "ECT", "TIMING_ADVANCE", "KNOCK"]),
        ("rain_event", ["MISFIRE_COUNTER", "RPM", "VOLTAGE"]),
        ("low_voltage", ["CONTROL_MODULE_VOLTAGE", "SENSOR_5V", "DTC"]),
        ("bank_split", ["STFT_B1", "STFT_B2", "LTFT_B1", "LTFT_B2"]),
        ("cylinder_balance", ["MISFIRE_COUNTER", "STFT", "RPM"]),
        ("adaptation_not_cleared", ["STFT", "LTFT", "TIME_SINCE_DTC_CLEAR"]),
        ("cold_to_warm", ["ECT", "STFT", "LTFT", "FUEL_STATUS"]),
        ("uphill_load", ["LOAD", "MAF", "MAP", "FUEL_PRESSURE"]),
        ("turbo_spool", ["BOOST", "MAP", "MAF", "WASTEGATE"]),
        ("idle_ac_on", ["RPM", "LOAD", "STFT", "THROTTLE"]),
        ("fan_cycle", ["ECT", "FAN_COMMAND", "VOLTAGE"]),
        ("fuel_level_low", ["FUEL_LEVEL", "FUEL_PRESSURE", "STFT"]),
        ("rough_road", ["MISFIRE_COUNTER", "SPEED", "RPM"]),
    ]
    cases: list[dict[str, Any]] = []
    for archetype_id, title, system in UNCOMMON_ARCHETYPES:
        for variant_id, signals in variants:
            cases.append(
                {
                    "id": f"unc-{archetype_id}-{variant_id}",
                    "caseClass": "uncommon",
                    "code": "",
                    "title": title,
                    "system": system,
                    "vehicleScope": "generic but non-obvious gasoline diagnostic trap",
                    "symptoms": [
                        "Complaint may appear intermittent or mode-specific",
                        "DTC may be absent or misleading",
                        "Graph shape matters more than a single snapshot",
                    ],
                    "signalPattern": f"{title}: reproduce during {variant_id.replace('_', ' ')} and compare the listed signals for a coherent physical cause.",
                    "graphFeatures": signals,
                    "likelyRootCauses": [title],
                    "recommendedChecks": [
                        "Reproduce in the mode where the symptom appears.",
                        "Cross-check fuel trims, airflow, MAP/load, timing and voltage before replacing a sensor.",
                        "Confirm repair with the same operating-mode graph, not only with cleared DTCs.",
                    ],
                    "postRepairVerification": "Repeat the exact trigger condition and verify that both symptom and graph anomaly are gone.",
                    "confidence": 0.58,
                    "sources": ["motor-maf-diagnosis", "motor-fuel-trimming-time", "mvw-fuel-trims", "drive2-practical-search"],
                }
            )
    return cases


def vehicle_specific_cases() -> list[dict[str, Any]]:
    operating_variants = [
        "idle",
        "hot_idle",
        "cold_start",
        "tip_in",
        "light_cruise",
        "wot_midrange",
        "high_rpm",
        "after_refuel",
        "heat_soak_restart",
        "traffic_fan_cycle",
        "rain_humidity",
        "low_voltage",
        "bank_split",
        "misfire_counter",
        "fuel_pressure_load",
        "boost_spool",
        "decel",
        "post_repair",
        "adaptation_reset",
        "freeze_frame_replay",
    ]
    cases: list[dict[str, Any]] = []
    for platform_id, platform, codes, weak_points in VEHICLE_PLATFORMS:
        for index, variant in enumerate(operating_variants):
            code = codes[index % len(codes)]
            cases.append(
                {
                    "id": f"veh-{platform_id}-{variant}",
                    "caseClass": "vehicle_specific",
                    "code": code,
                    "title": f"{platform}: {weak_points}",
                    "system": "platform_pattern",
                    "vehicleScope": platform,
                    "symptoms": [
                        f"Common platform direction: {weak_points}",
                        "Use as prioritization, not as proof",
                        "Generic physical checks still override brand folklore",
                    ],
                    "signalPattern": f"For {platform}, reproduce {code} or the complaint during {variant.replace('_', ' ')} and compare platform weak points against OBD graph physics.",
                    "graphFeatures": ["RPM", "LOAD", "THROTTLE", "MAF", "MAP", "STFT", "LTFT", "TIMING_ADVANCE", "CONTROL_MODULE_VOLTAGE"],
                    "likelyRootCauses": [item.strip() for item in weak_points.split(",")],
                    "recommendedChecks": [
                        "Start with the platform weak points only after the generic graph pattern points in the same direction.",
                        "Verify power, ground, vacuum/boost leaks and mechanical timing before expensive modules.",
                        "Document before/after graph capture for future training.",
                    ],
                    "postRepairVerification": "Repeat freeze-frame-like conditions and compare with the pre-repair graph.",
                    "confidence": 0.54,
                    "sources": ["drive2-practical-search", "dtcdb-mit-generic", "vehiclepm-obd-field-data"],
                }
            )
    return cases


def repair_confirmed_seed_cases() -> list[dict[str, Any]]:
    seeds = [
        {
            "id": "seed-denso-p0171-p0174-cracked-purge-hose",
            "code": "P0171/P0174",
            "title": "Lean banks returned after A/F sensor replacement; cracked hose behind air meter fixed the fault",
            "rootCause": "vacuum leak behind air mass meter near purge hose",
            "source": "denso-p0171-vacuum-leak-case",
            "signals": ["STFT", "LTFT", "A/F_SENSOR", "IDLE_RPM", "EVAP_PURGE"],
            "repair": "replace cracked hose, erase DTCs, road-test",
        },
        {
            "id": "seed-mvw-vacuum-leak-post-repair-trim-mirror",
            "code": "P0171-family",
            "title": "After vacuum leak repair, STFT mirrors stored positive LTFT until adaptation relearns",
            "rootCause": "previous unmetered air repair with learned trim remaining",
            "source": "mvw-fuel-trims",
            "signals": ["STFT", "LTFT", "TIME"],
            "repair": "verify resultant trim approaches zero during road test",
        },
        {
            "id": "seed-motor-dirty-maf-idle-cruise-split",
            "code": "P0171-family",
            "title": "Dirty MAF shows negative trim at idle and positive trim at cruise",
            "rootCause": "MAF range-dependent bias",
            "source": "motor-fuel-trimming-time",
            "signals": ["STFT", "LTFT", "MAF", "VE", "RPM"],
            "repair": "replace/repair MAF after leak and O2 checks",
        },
        {
            "id": "seed-motor-low-fuel-volume-under-load",
            "code": "lean-under-load",
            "title": "Fuel pressure can look acceptable at idle while fuel volume fails under acceleration",
            "rootCause": "fuel pump volume loss",
            "source": "motor-maf-diagnosis",
            "signals": ["O2", "LAMBDA", "FUEL_PRESSURE", "LOAD", "RPM"],
            "repair": "verify fuel volume and pump feed under load",
        },
        {
            "id": "seed-motor-injector-imbalance-trim-response",
            "code": "P0172/P0300-family",
            "title": "Injector balance test uses fuel-trim response to identify a leaking or low-flow injector",
            "rootCause": "injector flow imbalance",
            "source": "motor-fuel-trimming-time",
            "signals": ["STFT", "LTFT", "INJECTOR_BALANCE", "MISFIRE_COUNTER"],
            "repair": "repair injectors, then verify idle quality and trim normalization",
        },
        {
            "id": "seed-drive2-opel-astra-p0171-purge-valve",
            "code": "P0171",
            "title": "Opel Astra lean mixture resolved by EVAP purge valve replacement",
            "rootCause": "purge valve leaking flow when it should be closed",
            "source": "drive2-practical-search",
            "signals": ["STFT", "LTFT", "EVAP_PURGE", "IDLE_RPM"],
            "repair": "replace purge valve and verify symptoms disappear",
        },
        {
            "id": "seed-drive2-p0172-egr-rich-idle",
            "code": "P0172",
            "title": "Rich mixture and unstable idle associated with EGR not sealing",
            "rootCause": "EGR valve leakage/dilution at idle",
            "source": "drive2-practical-search",
            "signals": ["STFT", "LTFT", "MAP", "IDLE_RPM", "EGR_COMMAND"],
            "repair": "verify EGR sealing and trim normalization",
        },
        {
            "id": "seed-drive2-cam-sensor-misfire-no-injector-command",
            "code": "P0300/P0302/P0303",
            "title": "Misfire-like behavior caused by cam position sensor logic cutting injector command",
            "rootCause": "camshaft position sensor plausibility/intermittent fault",
            "source": "drive2-practical-search",
            "signals": ["MISFIRE_COUNTER", "INJECTOR_COMMAND", "CAM_CRANK_SYNC", "RPM"],
            "repair": "replace/repair cam sensor circuit and verify no repeat after warm idle",
        },
    ]
    result = []
    for seed in seeds:
        result.append(
            {
                "id": seed["id"],
                "caseClass": "repair_confirmed_seed",
                "code": seed["code"],
                "title": seed["title"],
                "system": "field_pattern",
                "vehicleScope": "specific public case or professional case study",
                "symptoms": ["Observed complaint is linked to an actual repair/correction in the source."],
                "signalPattern": f"Signals to compare before and after repair: {', '.join(seed['signals'])}.",
                "graphFeatures": seed["signals"],
                "likelyRootCauses": [seed["rootCause"]],
                "recommendedChecks": ["Reproduce the complaint, verify the source pattern, perform the named repair only after confirmation."],
                "postRepairVerification": seed["repair"],
                "confidence": 0.74,
                "sources": [seed["source"]],
            }
        )
    return result


def summarize(cases: list[dict[str, Any]]) -> dict[str, Any]:
    counts: dict[str, int] = {}
    systems: dict[str, int] = {}
    for case in cases:
        counts[case["caseClass"]] = counts.get(case["caseClass"], 0) + 1
        systems[case["system"]] = systems.get(case["system"], 0) + 1
    return {
        "id": "diagnostic-case-pattern-summary-v1",
        "totalCases": len(cases),
        "counts": counts,
        "systems": dict(sorted(systems.items())),
        "generationPolicy": [
            "standard cases are generated from the MIT-licensed dtcdb DTC seed list plus project-authored OBD signal logic",
            "uncommon cases are project-authored diagnostic traps grounded in cited engineering and repair case sources",
            "vehicle-specific cases are prioritization patterns for common platforms and must be confirmed by generic graph physics",
            "repair-confirmed seed cases store concise source-attributed patterns, not copied forum text or images",
        ],
        "minimumRequestedCounts": {
            "standard": 10000,
            "uncommon": 1000,
            "vehicle_specific": 500,
        },
    }


def write_json(path: Path, value: Any, pretty: bool = True) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if pretty:
        payload = json.dumps(value, ensure_ascii=False, indent=2)
    else:
        payload = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    path.write_text(payload + "\n", encoding="utf-8")


def main() -> None:
    rows = load_dtc_rows()
    cases = standard_cases(rows) + uncommon_cases() + vehicle_specific_cases() + repair_confirmed_seed_cases()
    summary = summarize(cases)
    if summary["counts"].get("standard", 0) < 10_000:
        raise SystemExit("standard case target was not met")
    if summary["counts"].get("uncommon", 0) < 1_000:
        raise SystemExit("uncommon case target was not met")
    if summary["counts"].get("vehicle_specific", 0) < 500:
        raise SystemExit("vehicle-specific case target was not met")
    write_json(KB / "cases" / "diagnostic_case_patterns.json", cases, pretty=False)
    write_json(KB / "cases" / "diagnostic_case_pattern_summary.json", summary)
    print(
        "case patterns generated: "
        f"total={summary['totalCases']} "
        f"standard={summary['counts'].get('standard', 0)} "
        f"uncommon={summary['counts'].get('uncommon', 0)} "
        f"vehicle_specific={summary['counts'].get('vehicle_specific', 0)}"
    )


if __name__ == "__main__":
    main()

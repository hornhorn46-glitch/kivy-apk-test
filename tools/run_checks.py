from __future__ import annotations

import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def run(args: list[str]) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def scan_unfinished_markers() -> None:
    marker = "TO" + "DO"
    extensions = {".kt", ".kts", ".json", ".md", ".py", ".xml"}
    ignored = {".git", ".gradle", ".toolchain", "build"}
    hits = []
    for path in ROOT.rglob("*"):
        if any(part in ignored for part in path.parts):
            continue
        if path.is_file() and path.suffix in extensions:
            text = path.read_text(encoding="utf-8", errors="ignore")
            if marker in text:
                hits.append(path.relative_to(ROOT).as_posix())
    if hits:
        raise SystemExit("unfinished implementation markers found: " + ", ".join(hits))


def check_required_files() -> None:
    required = [
        "app/src/main/java/com/autodoctor/aipro/core/diagnostics/InferenceEngine.kt",
        "app/src/main/java/com/autodoctor/aipro/core/knowledge/KnowledgeRepository.kt",
        "app/src/main/java/com/autodoctor/aipro/core/obd/Elm327.kt",
        "knowledge/rules/engine_air_fuel.json",
        "knowledge/profiles/universal_obd2.json",
        "knowledge/profiles/universal_gasoline_pfi_maf_na.json",
        "knowledge/profiles/generic_gasoline_turbo_gdi_mafmap_2_0l.json",
        "knowledge/brand_profiles/generic_gasoline_brands.json",
        "knowledge/reference_curves/toyota_etios_2014_1_5_carobd.json",
        "knowledge/encyclopedia/fuel_trim.json",
    ]
    missing = [item for item in required if not (ROOT / item).exists()]
    if missing:
        raise SystemExit("required files missing: " + ", ".join(missing))


def main() -> None:
    check_required_files()
    scan_unfinished_markers()
    run([sys.executable, "tools/validate_kb.py"])
    run([sys.executable, "tools/diagnostic_smoke.py"])
    print("project checks passed")


if __name__ == "__main__":
    main()

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
KB = ROOT / "knowledge"


RULE_FIELDS = {
    "id",
    "title",
    "category",
    "conditions",
    "probability",
    "physicalExplanation",
    "symptoms",
    "possibleCauses",
    "recommendedChecks",
    "repairRecommendations",
    "confidence",
    "severity",
    "sources",
}

CONDITION_OPERATORS = {"GreaterThan", "LessThan", "Between", "Outside", "Exists", "ContainsAny"}
SEVERITIES = {"Info", "Warning", "Serious", "Critical"}
ENCYCLOPEDIA_FIELDS = {"id", "title", "parameterIds", "whatItIs", "normalValues", "deviations", "influence", "checks", "sources"}
MODEL_FIELDS = {"id", "version", "target", "createdBy", "validation", "features", "outputs"}


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def fail(message: str) -> None:
    raise SystemExit(f"validation failed: {message}")


def validate_sources() -> set[str]:
    sources = load_json(KB / "sources.json")
    ids = set()
    for item in sources:
        source_id = item.get("id")
        if not source_id:
            fail("source without id")
        if source_id in ids:
            fail(f"duplicate source id {source_id}")
        ids.add(source_id)
        for field in ("title", "url", "license"):
            if not item.get(field):
                fail(f"source {source_id} missing {field}")
    return ids


def validate_rules(source_ids: set[str]) -> list[dict]:
    rules: list[dict] = []
    rule_ids = set()
    for path in sorted((KB / "rules").glob("*.json")):
        data = load_json(path)
        if not isinstance(data, list):
            fail(f"{path} must contain a rule list")
        for rule in data:
            missing = RULE_FIELDS - set(rule)
            if missing:
                fail(f"rule {rule.get('id', path.name)} missing {sorted(missing)}")
            if rule["id"] in rule_ids:
                fail(f"duplicate rule id {rule['id']}")
            rule_ids.add(rule["id"])
            if not 0.0 <= float(rule["probability"]) <= 1.0:
                fail(f"rule {rule['id']} probability outside 0..1")
            if not 0.0 <= float(rule["confidence"]) <= 1.0:
                fail(f"rule {rule['id']} confidence outside 0..1")
            if rule["severity"] not in SEVERITIES:
                fail(f"rule {rule['id']} invalid severity")
            if len(rule["conditions"]) < 2:
                fail(f"rule {rule['id']} must use multiple signals")
            for condition in rule["conditions"]:
                if condition.get("operator") not in CONDITION_OPERATORS:
                    fail(f"rule {rule['id']} invalid operator {condition.get('operator')}")
                if not condition.get("metric"):
                    fail(f"rule {rule['id']} has condition without metric")
            for source in rule["sources"]:
                if source not in source_ids:
                    fail(f"rule {rule['id']} references unknown source {source}")
            rules.append(rule)
    if not rules:
        fail("no diagnostic rules found")
    return rules


def validate_profiles() -> None:
    required = {"id", "make", "model", "induction", "injection", "airMetering", "transmission", "obdType", "supportedPids", "references"}
    profile_count = 0
    generic_gasoline_count = 0
    for path in sorted((KB / "profiles").glob("*.json")):
        profile = load_json(path)
        profile_count += 1
        if profile["id"].startswith("generic-gasoline-"):
            generic_gasoline_count += 1
        missing = required - set(profile)
        if missing:
            fail(f"profile {path.name} missing {sorted(missing)}")
    if profile_count < 50:
        fail(f"too few vehicle profiles: {profile_count}")
    if generic_gasoline_count < 40:
        fail(f"too few generic gasoline profiles: {generic_gasoline_count}")


def validate_brand_profiles(source_ids: set[str]) -> None:
    mappings_dir = KB / "brand_profiles"
    if not mappings_dir.exists():
        fail("brand_profiles directory missing")
    profile_ids = {load_json(path)["id"] for path in sorted((KB / "profiles").glob("*.json"))}
    for path in sorted(mappings_dir.glob("*.json")):
        mapping = load_json(path)
        for field in ("id", "source", "defaultProfileId", "supportedProfileIds", "brands", "selectionNotes"):
            if field not in mapping:
                fail(f"brand mapping {path.name} missing {field}")
        if mapping["source"] not in source_ids:
            fail(f"brand mapping {path.name} references unknown source {mapping['source']}")
        if mapping["defaultProfileId"] not in profile_ids:
            fail(f"brand mapping {path.name} default profile missing: {mapping['defaultProfileId']}")
        for profile_id in mapping["supportedProfileIds"]:
            if profile_id not in profile_ids:
                fail(f"brand mapping {path.name} supported profile missing: {profile_id}")
        if len(mapping["brands"]) < 30:
            fail(f"brand mapping {path.name} has too few brands for global fallback coverage")


def validate_pids() -> None:
    for path in sorted((KB / "pids").glob("*.json")):
        entries = load_json(path)
        for entry in entries:
            for field in ("id", "service", "pid", "name", "unit", "min", "max"):
                if field not in entry:
                    fail(f"pid entry in {path.name} missing {field}")


def validate_encyclopedia(source_ids: set[str]) -> None:
    for path in sorted((KB / "encyclopedia").glob("*.json")):
        article = load_json(path)
        missing = ENCYCLOPEDIA_FIELDS - set(article)
        if missing:
            fail(f"encyclopedia {path.name} missing {sorted(missing)}")
        for source in article["sources"]:
            if source not in source_ids:
                fail(f"encyclopedia {path.name} references unknown source {source}")


def validate_reference_curves(source_ids: set[str]) -> None:
    curves_dir = KB / "reference_curves"
    if not curves_dir.exists():
        fail("reference_curves directory missing")
    for path in sorted(curves_dir.glob("*.json")):
        document = load_json(path)
        for field in ("id", "vehicleProfileId", "title", "source", "license", "curveKind", "limitations", "curves"):
            if field not in document:
                fail(f"reference curve {path.name} missing {field}")
        if document["source"] not in source_ids:
            fail(f"reference curve {path.name} references unknown source {document['source']}")
        if not document["curves"]:
            fail(f"reference curve {path.name} has no curves")
        for curve in document["curves"]:
            for field in ("metric", "xMetric", "xUnit", "yUnit", "points"):
                if field not in curve:
                    fail(f"curve in {path.name} missing {field}")
            if not curve["points"]:
                fail(f"curve {curve['metric']} in {path.name} has no points")
            for point in curve["points"]:
                for field in ("x", "p10", "p50", "p90", "sampleCount"):
                    if field not in point:
                        fail(f"curve {curve['metric']} in {path.name} point missing {field}")
                if not (float(point["p10"]) <= float(point["p50"]) <= float(point["p90"])):
                    fail(f"curve {curve['metric']} in {path.name} has invalid percentile order")
                if int(point["sampleCount"]) <= 0:
                    fail(f"curve {curve['metric']} in {path.name} has empty sample bin")


def validate_driveability_model(source_ids: set[str]) -> None:
    model_path = KB / "ai" / "driveability_neurosymbolic_model.json"
    report_path = KB / "evaluation" / "root_cause_eval_report.json"
    manifest_path = KB / "training" / "root_cause_training_manifest.json"
    for path in (model_path, report_path, manifest_path):
        if not path.exists():
            fail(f"missing root-cause model artifact: {path.relative_to(KB)}")

    model = load_json(model_path)
    missing = MODEL_FIELDS - set(model)
    if missing:
        fail(f"driveability model missing {sorted(missing)}")
    validation = model["validation"]
    if int(validation.get("testGraphCount", 0)) < 50:
        fail("driveability model must be validated on at least 50 test graphs")
    if float(validation.get("measuredTestAccuracy", 0.0)) < float(validation.get("targetAccuracy", 0.97)):
        fail("driveability model did not meet target benchmark accuracy")
    feature_ids = {feature["id"] for feature in model["features"]}
    if len(feature_ids) < 12:
        fail("driveability model has too few features")
    root_causes = {output["rootCause"] for output in model["outputs"]}
    if len(root_causes) < 10:
        fail("driveability model has too few root-cause outputs")
    for output in model["outputs"]:
        if not output.get("weights"):
            fail(f"driveability output {output.get('rootCause')} has no weights")
        unknown_weights = set(output["weights"]) - feature_ids
        if unknown_weights:
            fail(f"driveability output {output['rootCause']} references unknown features {sorted(unknown_weights)}")
        for source in output.get("sources", []):
            if source not in source_ids:
                fail(f"driveability output {output['rootCause']} references unknown source {source}")

    report = load_json(report_path)
    if not report.get("passedTarget"):
        fail("root-cause evaluation report did not pass target")
    if int(report["test"].get("graph_count", 0)) < 50:
        fail("root-cause evaluation has too few test graphs")
    if float(report["test"].get("accuracy", 0.0)) < 0.97:
        fail("root-cause evaluation accuracy is below 97% target")

    manifest = load_json(manifest_path)
    if int(manifest.get("trainingCases", 0)) < 100:
        fail("root-cause training manifest has too few training cases")
    if set(manifest.get("featureSet", [])) != feature_ids:
        fail("root-cause training manifest feature set differs from model")


def main() -> None:
    source_ids = validate_sources()
    rules = validate_rules(source_ids)
    validate_profiles()
    validate_brand_profiles(source_ids)
    validate_pids()
    validate_encyclopedia(source_ids)
    validate_reference_curves(source_ids)
    validate_driveability_model(source_ids)
    print(f"knowledge base valid: {len(rules)} rules, {len(source_ids)} sources")


if __name__ == "__main__":
    main()

"""Evaluate Malbolge units and validate cross-unit semantic references."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from mbx_eval import evaluate
from mbx_frame import FrameError, UnitFrame, parse_frame

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "source"


class ModelError(ValueError):
    pass


def limits() -> dict[str, int]:
    values = {}
    for line in (ROOT / "toolchain/LOCKFILE").read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    evaluator_digest = hashlib.sha256((ROOT / "toolchain/mbx_eval.py").read_bytes()).hexdigest()
    if evaluator_digest != values["malbolge-reference-evaluator-sha256"]:
        raise ModelError("Malbolge evaluator digest mismatch")
    return {
        "max_source_bytes": int(values["malbolge-max-unit-bytes"]),
        "max_steps": int(values["malbolge-max-steps-per-unit"]),
        "max_output_bytes": int(values["malbolge-max-output-bytes"]),
        "max_records": int(values["malbolge-max-records-per-unit"]),
    }


def evaluate_units() -> list[UnitFrame]:
    budget = limits()
    frames = []
    for path in sorted(SOURCE.rglob("*.mal")):
        output = evaluate(
            path.read_bytes(),
            max_source_bytes=budget["max_source_bytes"],
            max_steps=budget["max_steps"],
            max_output_bytes=budget["max_output_bytes"],
        )
        frame = parse_frame(output, max_records=budget["max_records"])
        if path.stem != frame.unit_id.replace(".", "_"):
            raise ModelError(f"unit ID does not match filename: {path}")
        frames.append(frame)
    return frames


def validate(frames: list[UnitFrame]) -> dict:
    unit_ids = [frame.unit_id for frame in frames]
    if len(unit_ids) != len(set(unit_ids)):
        raise ModelError("duplicate unit ID")
    exports = [export for frame in frames for export in frame.exports]
    if len(exports) != len(set(exports)):
        raise ModelError("duplicate export ID")
    missing = sorted({item for frame in frames for item in frame.imports} - set(exports))
    if missing:
        raise ModelError(f"unresolved imports: {missing}")

    by_kind: dict[str, list[dict]] = {}
    for frame in frames:
        for record in frame.records:
            by_kind.setdefault(record["kind"], []).append(record)
    permitted = {"Feature", "Effect", "Endpoint", "BindingSpec", "ConfigItem", "SegmentSource", "SettingsPage",
                 "DiagnosticPolicy"}
    if set(by_kind) - permitted:
        raise ModelError(f"unknown record kinds: {sorted(set(by_kind) - permitted)}")

    features = {record["feature_id"]: record for record in by_kind.get("Feature", [])}
    effects = {record["effect_id"]: record for record in by_kind.get("Effect", [])}
    endpoints = {record["endpoint_id"]: record for record in by_kind.get("Endpoint", [])}
    bindings = by_kind.get("BindingSpec", [])
    configs = {record["config_id"]: record for record in by_kind.get("ConfigItem", [])}
    sources = {record["source_id"]: record for record in by_kind.get("SegmentSource", [])}
    pages = {record["page_id"]: record for record in by_kind.get("SettingsPage", [])}
    policies = by_kind.get("DiagnosticPolicy", [])
    if len(policies) != 1 or policies[0] != {
        "kind": "DiagnosticPolicy",
        "policy_id": "hook_compatibility",
        "transport": "EXPLICIT_BROADCAST",
        "failure_mode": "ROLLBACK_ALL_DISABLE_TOGGLES",
        "target_package": "com.google.android.youtube",
        "screen_title": "Compatibility & diagnostics",
        "copy_label": "Copy compatibility report",
    }:
        raise ModelError("diagnostic policy must fail closed for the verified YouTube target")
    if len(pages) != len(by_kind.get("SettingsPage", [])):
        raise ModelError("duplicate SettingsPage")
    for page in pages.values():
        validate_settings_page(page, configs)
    if not features or len(features) != len(by_kind.get("Feature", [])):
        raise ModelError("missing or duplicate Feature")
    if len(effects) != len(by_kind.get("Effect", [])):
        raise ModelError("duplicate Effect")
    if len(endpoints) != len(by_kind.get("Endpoint", [])):
        raise ModelError("duplicate Endpoint")
    if len(configs) != len(by_kind.get("ConfigItem", [])):
        raise ModelError("duplicate ConfigItem")
    if len(sources) != len(by_kind.get("SegmentSource", [])):
        raise ModelError("duplicate SegmentSource")
    if {binding["endpoint_id"] for binding in bindings} != set(endpoints):
        raise ModelError("BindingSpecs must cover exactly the declared Endpoints")
    if len({binding["binding_spec_id"] for binding in bindings}) != len(bindings):
        raise ModelError("duplicate BindingSpec ID")

    for feature in features.values():
        required = {
            "feature_id", "intent", "required_capabilities", "required_endpoints",
            "config_items", "effects", "state_owner", "process_scope",
            "performance_class", "reload_class", "diagnostic_identity",
        }
        if not required <= feature.keys():
            raise ModelError("Feature missing required fields")
        if not set(feature["required_endpoints"]) <= endpoints.keys():
            raise ModelError("Feature references missing Endpoint")
        if not set(feature["config_items"]) <= configs.keys():
            raise ModelError("Feature references missing ConfigItem")
        if not set(feature["effects"]) <= effects.keys():
            raise ModelError("Feature references missing Effect")
        if feature["process_scope"] != ["com.google.android.youtube"]:
            raise ModelError("Feature process scope must be YouTube-only")

    for effect in effects.values():
        required = {
            "effect_id", "feature_id", "endpoint_id", "phase",
            "priority_relation", "composition_participation", "config_guard",
            "handler_function", "failure_policy",
        }
        if not required <= effect.keys():
            raise ModelError("Effect missing required fields")
        feature = features.get(effect["feature_id"])
        if feature is None or effect["effect_id"] not in feature["effects"]:
            raise ModelError("Effect has no owning Feature")
        if effect["endpoint_id"] not in feature["required_endpoints"]:
            raise ModelError("Effect Endpoint not declared by Feature")
        if effect["config_guard"] is None:
            if effect["handler_function"] != "inject_settings_entry":
                raise ModelError("only the settings entry Effect may be unguarded")
        elif effect["config_guard"] not in feature["config_items"]:
            raise ModelError("Effect config guard not declared by Feature")
        if endpoints[effect["endpoint_id"]]["operation_class"] == "CALL":
            raise ModelError("Effect cannot hook a CALL Endpoint")
        if effect["handler_function"] == "skip_segments":
            validate_seek_effect(effect, feature, endpoints)
        if effect["handler_function"] == "observe_video_id":
            validate_source_effect(effect, feature, sources)
        if effect["handler_function"] == "inject_settings_entry":
            validate_settings_effect(effect, feature, endpoints, pages)
        if effect["handler_function"] == "filter_shorts_ads":
            roles = effect.get("call_roles")
            if not isinstance(roles, dict) or set(roles) != {"predicate"}:
                raise ModelError("Shorts filter Effect needs predicate role")
            if any(endpoint_id not in feature["required_endpoints"]
                   or endpoints[endpoint_id]["operation_class"] != "CALL"
                   for endpoint_id in roles.values()):
                raise ModelError("Shorts filter Effect call Endpoint invalid")

    for endpoint in endpoints.values():
        if endpoint["operation_class"] not in {"ACTION", "OBSERVE", "CALL"}:
            raise ModelError(f"unknown Endpoint operation class: {endpoint['endpoint_id']}")

    target_hash = next(
        line.split("sha256: ", 1)[1].strip()
        for line in (ROOT / "target/current/target-release.lock.yml").read_text().splitlines()
        if line.strip().startswith("sha256: ")
    )
    for endpoint_id, endpoint in endpoints.items():
        matches = [binding for binding in bindings if binding["endpoint_id"] == endpoint_id]
        if len(matches) != 1:
            raise ModelError(f"Endpoint requires one BindingSpec: {endpoint_id}")
        binding = matches[0]
        if binding["binding_spec_id"] != endpoint["binding_spec_id"]:
            raise ModelError("Endpoint and BindingSpec IDs differ")
        if binding["uniqueness_rule"] != "EXACTLY_ONE":
            raise ModelError("BindingSpec must require uniqueness")
        if binding["target_base_sha256"] != target_hash:
            raise ModelError("BindingSpec target hash mismatch")
        if binding["expected_member_kind"] not in {"METHOD", "CONSTRUCTOR", "FIELD"}:
            raise ModelError("BindingSpec member kind unsupported")
        if (binding["expected_member_kind"] == "CONSTRUCTOR") != (
                binding["hard_shape_constraints"]["member_name"] == "<init>"):
            raise ModelError("BindingSpec constructor shape mismatch")

    return {
        "schema": "CMG-1-development",
        "units": sorted(unit_ids),
        "records": [record for frame in frames for record in frame.records],
    }


def validate_seek_effect(effect: dict, feature: dict, endpoints: dict) -> None:
    """A segment-skipping Effect names exactly one CALL seek Endpoint of its Feature."""
    calls = effect.get("call_endpoints")
    if not isinstance(calls, list) or len(calls) != 1:
        raise ModelError("segment Effect needs exactly one call Endpoint")
    if calls[0] not in feature["required_endpoints"] or endpoints[calls[0]]["operation_class"] != "CALL":
        raise ModelError("segment Effect call Endpoint invalid")


def validate_source_effect(effect: dict, feature: dict, sources: dict) -> None:
    """A video-observing Effect names one SegmentSource declared by its Feature."""
    source = sources.get(effect.get("segment_source"))
    if source is None or source["source_id"] not in feature.get("segment_sources", []):
        raise ModelError("segment Effect source not declared by Feature")
    required = {
        "source_id", "api_origin", "hash_prefix_length", "action_type", "categories",
        "connect_timeout_ms", "read_timeout_ms",
    }
    if not required <= source.keys():
        raise ModelError("SegmentSource missing required fields")
    if not source["api_origin"].startswith("https://") or source["api_origin"].endswith("/"):
        raise ModelError("SegmentSource origin must be an HTTPS origin")
    categories = [category["category"] for category in source["categories"]]
    if not categories or len(categories) != len(set(categories)):
        raise ModelError("SegmentSource categories empty or duplicated")
    if any(category["config_guard"] not in feature["config_items"] for category in source["categories"]):
        raise ModelError("SegmentSource category guard not declared by Feature")


SETTINGS_ROLES = {
    "owner", "screen", "context", "layout", "icon_space", "set_icon", "intent", "new_preference", "set_key", "set_title", "set_summary",
    "set_order", "group_add", "group_find",
}


def validate_settings_page(page: dict, configs: dict) -> None:
    """A SettingsPage exposes every ConfigItem exactly once, grouped in titled sections."""
    required = {"page_id", "title", "entry_key", "entry_order", "entry_summary", "entry_icon", "sections"}
    if not required <= page.keys() or not page["sections"]:
        raise ModelError("SettingsPage missing required fields")
    exposed = [item["config_id"] for section in page["sections"] for item in section["items"]]
    if sorted(exposed) != sorted(configs):
        raise ModelError("SettingsPage must expose every ConfigItem exactly once")
    ids = [section["section_id"] for section in page["sections"]]
    if len(ids) != len(set(ids)):
        raise ModelError("duplicate SettingsPage section")
    texts = [page["title"], page["entry_key"], page["entry_summary"], page["entry_icon"]] + [
        text for section in page["sections"]
        for text in [section["title"]] + [item["title"] for item in section["items"]]
    ]
    if any(not text or not text.isascii() or "\t" in text or "\n" in text for text in texts):
        raise ModelError("SettingsPage text must be non-empty single-line ASCII")


def validate_settings_effect(effect: dict, feature: dict, endpoints: dict, pages: dict) -> None:
    """The settings entry Effect maps every fixed role to one CALL Endpoint of its Feature."""
    roles = effect.get("call_roles")
    if not isinstance(roles, dict) or set(roles) != SETTINGS_ROLES:
        raise ModelError("settings entry Effect roles incomplete")
    for endpoint_id in roles.values():
        if endpoint_id not in feature["required_endpoints"] or endpoints[endpoint_id]["operation_class"] != "CALL":
            raise ModelError("settings entry role Endpoint invalid")
    if effect.get("settings_page") not in pages:
        raise ModelError("settings entry Effect names no SettingsPage")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", type=Path, help="write normalized review graph")
    args = parser.parse_args()
    graph = validate(evaluate_units())
    normalized = json.dumps(graph, sort_keys=True, separators=(",", ":")) + "\n"
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(normalized, encoding="utf-8")
    else:
        print(normalized)


if __name__ == "__main__":
    main()

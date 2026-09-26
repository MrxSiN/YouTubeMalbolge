"""Evaluate Malbolge units and validate cross-unit semantic references."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from mbx_eval import evaluate
from mbx_frame import FrameError, UnitFrame, parse_frame
from mbx_program import Program, ProgramError, parse_program

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


def _evaluate_sources() -> list[tuple[Path, bytes]]:
    budget = limits()
    outputs = []
    for path in sorted(SOURCE.rglob("*.mal")):
        output = evaluate(
            path.read_bytes(),
            max_source_bytes=budget["max_source_bytes"],
            max_steps=budget["max_steps"],
            max_output_bytes=budget["max_output_bytes"],
        )
        outputs.append((path, output))
    return outputs


def evaluate_all() -> tuple[list[UnitFrame], list[Program]]:
    """Evaluate every source once; split MBX1 schema units from MBX2 programs."""
    budget = limits()
    frames = []
    programs = []
    for path, output in _evaluate_sources():
        if output.startswith(b"MBX2|"):
            item = parse_program(output)
            programs.append(item)
        else:
            item = parse_frame(output, max_records=budget["max_records"])
            frames.append(item)
        if path.stem != item.unit_id.replace(".", "_"):
            raise ModelError(f"unit ID does not match filename: {path}")
    ids = [program.unit_id for program in programs]
    if len(ids) != len(set(ids)):
        raise ModelError("duplicate executable program ID")
    endpoints = [program.endpoint_id for program in programs if program.endpoint_id != "-"]
    if len(endpoints) != len(set(endpoints)):
        raise ModelError("multiple executable programs per Endpoint unsupported")
    return frames, programs


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
    permitted = {"Endpoint", "BindingSpec"}
    if set(by_kind) - permitted:
        raise ModelError(f"unknown record kinds: {sorted(set(by_kind) - permitted)}")

    endpoints = {record["endpoint_id"]: record for record in by_kind.get("Endpoint", [])}
    bindings = by_kind.get("BindingSpec", [])
    if len(endpoints) != len(by_kind.get("Endpoint", [])):
        raise ModelError("duplicate Endpoint")
    if {binding["endpoint_id"] for binding in bindings} != set(endpoints):
        raise ModelError("BindingSpecs must cover exactly the declared Endpoints")
    if len({binding["binding_spec_id"] for binding in bindings}) != len(bindings):
        raise ModelError("duplicate BindingSpec ID")

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


def validate_programs(programs: list[Program], graph: dict) -> None:
    """Verify executable MBP1 ownership against remaining semantic declarations."""
    records = graph["records"]
    endpoints = {record["endpoint_id"] for record in records if record["kind"] == "Endpoint"}
    config_programs = [program for program in programs if program.group_id == "config_policy"]
    if len(config_programs) != 1 or len(config_programs[0].constants) % 3:
        raise ModelError("invalid config policy program")
    configs = set(config_programs[0].constants[1::3])
    bindings = {record["endpoint_id"] for record in records if record["kind"] == "BindingSpec"}
    for program in programs:
        if program.endpoint_id == "-":
            if program.group_id not in {"resolver_policy", "status_policy", "config_policy"} or program.members or program.config_id is not None:
                raise ModelError("invalid policy program")
            continue
        if program.endpoint_id not in endpoints:
            raise ModelError(f"program references missing Endpoint: {program.endpoint_id}")
        if program.config_id is not None and program.config_id not in configs:
            raise ModelError(f"program references missing ConfigItem: {program.config_id}")
        missing_members = set(program.members) - bindings
        if missing_members:
            raise ModelError(f"program references missing members: {sorted(missing_members)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", type=Path, help="write normalized review graph")
    args = parser.parse_args()
    frames, programs = evaluate_all()
    graph = validate(frames)
    validate_programs(programs, graph)
    normalized = json.dumps(graph, sort_keys=True, separators=(",", ":")) + "\n"
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(normalized, encoding="utf-8")
    else:
        print(normalized)


if __name__ == "__main__":
    main()

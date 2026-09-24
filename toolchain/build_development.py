"""Build development classfile JAR from validated Malbolge records."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
from pathlib import Path
from urllib.parse import quote
from zipfile import ZIP_DEFLATED, ZipFile

from validate_units import ROOT, evaluate_units, validate

GENERATED_ENTRY = "io.github.mrxsin.ytmalbolge.generated.Entry"
ACCESS = {"PUBLIC": 1, "PUBLIC_FINAL": 17, "PROTECTED_FINAL": 20, "PUBLIC_STATIC": 9}
HOOK_HANDLERS = {
    "hide_view", "filter_litho_ads", "skip_void", "capture_receiver", "observe_video_id",
    "skip_segments", "inject_settings_entry",
    "filter_shorts_ads",
}
SEGMENT_RUNTIME_CLASSES = 3
SETTINGS_RUNTIME_CLASSES = 5
MODULE_PACKAGE = "io.github.mrxsin.ytmalbolge"


def verified_bindings(bindings: dict[str, dict]) -> str:
    """Check every BindingSpec against the exact Verified Target Binding Set; return its digest."""
    verified = json.loads((ROOT / "target/current/verified-binding-set.json").read_text(encoding="utf-8"))
    by_endpoint = {r["endpoint_id"]: r for r in verified["bindings"]}
    if set(by_endpoint) != set(bindings) or verified["schema"] != "VBS-1":
        raise ValueError("verified binding set does not cover graph")
    digest_body = {key: value for key, value in verified.items() if key != "set_digest"}
    digest = hashlib.sha256(json.dumps(digest_body, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    if digest != verified["set_digest"]:
        raise ValueError("verified binding set digest mismatch")
    for endpoint_id, binding in bindings.items():
        shape = binding["hard_shape_constraints"]
        proof = by_endpoint[endpoint_id]
        if (proof["class_descriptor"] != shape["class_descriptor"]
                or proof["member_kind"] != binding["expected_member_kind"]
                or proof["member_name"] != shape["member_name"]
                or proof["member_descriptor"] != shape["member_descriptor"]
                or proof["staticness"] != shape["static"]):
            raise ValueError(f"binding differs from verified set: {endpoint_id}")
        evidence = {key: binding[key] for key in (
            "binding_spec_id", "hard_shape_constraints", "positive_evidence", "target_base_sha256"
        )}
        evidence_digest = hashlib.sha256(
            json.dumps(evidence, sort_keys=True, separators=(",", ":")).encode()
        ).hexdigest()
        if evidence_digest != proof["validation_digest"]:
            raise ValueError(f"binding evidence digest mismatch: {endpoint_id}")
    return digest


def member_fields(binding: dict) -> list[str]:
    shape = binding["hard_shape_constraints"]
    if shape["access"] not in ACCESS or shape["static"] != (shape["access"] == "PUBLIC_STATIC"):
        raise ValueError("unsupported physical member shape")
    return [
        binding["expected_member_kind"], shape["class_descriptor"][1:-1].replace("/", "."),
        shape["member_name"], shape["member_descriptor"], str(ACCESS[shape["access"]]),
        shape["superclass"][1:-1].replace("/", "."),
    ]


def plan_lines(records: list[dict], target_hash: str, active: bool) -> list[list[str]]:
    """Lower validated CMG records plus bindings to the backend's Logical Module Plan lines."""
    effects = sorted((r for r in records if r["kind"] == "Effect"), key=lambda r: r["endpoint_id"])
    configs = sorted((r for r in records if r["kind"] == "ConfigItem"), key=lambda r: r["config_id"])
    bindings = {r["endpoint_id"]: r for r in records if r["kind"] == "BindingSpec"}
    endpoints = {r["endpoint_id"]: r for r in records if r["kind"] == "Endpoint"}
    sources = {r["source_id"]: r for r in records if r["kind"] == "SegmentSource"}
    if not effects:
        raise ValueError("no compiled Effect")
    if len({r["endpoint_id"] for r in effects}) != len(effects):
        raise ValueError("multiple Effects per Endpoint unsupported")
    if any(r["value_type"] != "boolean" for r in configs):
        raise ValueError("unsupported ConfigItem")
    if any(r["handler_function"] not in HOOK_HANDLERS for r in effects):
        raise ValueError("unsupported Effect handler")
    config_index = {r["config_id"]: index for index, r in enumerate(configs)}

    lines = [["target", target_hash], ["active", str(active).lower()]]
    (diagnostic,) = (r for r in records if r["kind"] == "DiagnosticPolicy")
    lines.append(["diagnostic", diagnostic["policy_id"], diagnostic["transport"],
                  diagnostic["failure_mode"], diagnostic["target_package"],
                  diagnostic["screen_title"], diagnostic["copy_label"]])
    lines += [["config", r["storage_group"], r["config_id"], str(r["default"]).lower()] for r in configs]
    hooked = set()
    for effect in effects:
        endpoint_id = effect["endpoint_id"]
        endpoint = endpoints[endpoint_id]
        binding = bindings[endpoint_id]
        if endpoint["operation_class"] not in {"ACTION", "OBSERVE"}:
            raise ValueError(f"Endpoint cannot be hooked: {endpoint_id}")
        returns_void = binding["hard_shape_constraints"]["member_descriptor"].endswith(")V")
        if not returns_void and effect["handler_function"] not in {"inject_settings_entry", "filter_litho_ads", "filter_shorts_ads"}:
            raise ValueError("unsupported Endpoint")
        arguments = []
        if effect["handler_function"] == "skip_segments":
            arguments = [str(binding["hard_shape_constraints"]["argument_roles"]["position_ms"])]
        elif effect["handler_function"] == "observe_video_id":
            arguments = [binding["hard_shape_constraints"]["result_fields"]["video_id"]]
        elif effect["handler_function"] == "filter_litho_ads":
            roles = effect["call_roles"]
            context = bindings[roles["identifier"]]["hard_shape_constraints"]
            factory = bindings[roles["factory"]]["hard_shape_constraints"]
            value = bindings[roles["empty_value"]]["hard_shape_constraints"]
            arguments = [context["class_descriptor"][1:-1], context["member_name"],
                         context["superclass"][1:-1], factory["class_descriptor"][1:-1],
                         factory["member_name"], factory["member_descriptor"],
                         factory["superclass"][1:-1], value["class_descriptor"][1:-1],
                         value["member_name"], value["superclass"][1:-1],
                         value["member_descriptor"], *effect["component_patterns"]]
            hooked.update(roles.values())
        elif effect["handler_function"] == "filter_shorts_ads":
            roles = effect["call_roles"]
            arguments = member_fields(bindings[roles["predicate"]])
            hooked.update(roles.values())
        guard = effect["config_guard"]
        lines.append(["hook", *member_fields(binding), effect["handler_function"],
                      str(config_index[guard] if guard is not None else -1), f"ytm.{endpoint_id}.v1", *arguments])
        hooked.add(endpoint_id)

    for effect in (e for e in effects if e["handler_function"] == "skip_segments"):
        (seek_id,) = effect["call_endpoints"]
        seek = bindings[seek_id]
        lines.append(["seek", *member_fields(seek), seek["hard_shape_constraints"]["enum_constant"]])
        hooked.add(seek_id)
    for effect in (e for e in effects if e["handler_function"] == "observe_video_id"):
        source = sources[effect["segment_source"]]
        categories = [c["category"] for c in source["categories"]]
        query = "?categories=" + quote(json.dumps(categories, separators=(",", ":")), safe="")
        query += "&actionType=" + quote(source["action_type"], safe="")
        lines.append(["segments", source["api_origin"], str(source["hash_prefix_length"]),
                      str(source["connect_timeout_ms"]), str(source["read_timeout_ms"]), query,
                      source["action_type"]])
        lines += [["category", c["category"], str(config_index[c["config_guard"]])] for c in source["categories"]]
    pages = {r["page_id"]: r for r in records if r["kind"] == "SettingsPage"}
    for effect in (e for e in effects if e["handler_function"] == "inject_settings_entry"):
        page = pages[effect["settings_page"]]
        for role, endpoint_id in sorted(effect["call_roles"].items()):
            if endpoint_id not in hooked:
                lines.append(["member", endpoint_id, *member_fields(bindings[endpoint_id])])
                hooked.add(endpoint_id)
            lines.append(["role", role, endpoint_id])
        lines.append(["page", page["entry_key"], page["title"], str(page["entry_order"]),
                      MODULE_PACKAGE, page["entry_summary"], page["entry_icon"]])
        layouts = bindings[effect["call_roles"]["layout"]]["hard_shape_constraints"]["resource_layouts"]
        lines.append(["layouts", layouts["row"]])
        for section in page["sections"]:
            lines.append(["section", section["section_id"], section["title"]])
            lines += [["item", section["section_id"], str(config_index[item["config_id"]]), item["title"]]
                      for item in section["items"]]
    if hooked != set(bindings):
        raise ValueError(f"bound Endpoints without backend use: {sorted(set(bindings) - hooked)}")
    if any("\t" in field or "\n" in field for line in lines for field in line):
        raise ValueError("plan field contains a separator")
    return lines


def build(*, device_test: bool = False) -> Path:
    graph = validate(evaluate_units())
    records = graph["records"]
    bindings = {r["endpoint_id"]: r for r in records if r["kind"] == "BindingSpec"}
    target_hashes = {r["target_base_sha256"] for r in bindings.values()}
    if len(target_hashes) != 1:
        raise ValueError("Bindings disagree on target")
    target_hash = target_hashes.pop()
    digest = verified_bindings(bindings)

    asm_jar = os.environ.get("MALBOLGE_ASM_JAR")
    asm_files = [Path(asm_jar)] if asm_jar else sorted(
        (Path.home() / ".gradle/caches/modules-2/files-2.1/org.ow2.asm/asm/9.9.1").rglob("asm-9.9.1.jar")
    )
    if len(asm_files) != 1:
        raise FileNotFoundError("pinned ASM 9.9.1 unavailable")
    asm = asm_files[0]
    if hashlib.sha256(asm.read_bytes()).hexdigest() != "6f3828a215c920059a5efa2fb55c233d6c54ec5cadca99ce1b1bdd10077c7ddd":
        raise ValueError("pinned ASM 9.9.1 digest mismatch")
    java_home = os.environ.get("JAVA_HOME") or r"C:\Program Files\Android\Android Studio\jbr"
    java_bin = Path(java_home) / "bin"
    out = ROOT / "build/generated"
    compiler_classes = out / "compiler"
    runtime_classes = out / "classes"
    compiler_classes.mkdir(parents=True, exist_ok=True)
    if runtime_classes.resolve().parent != out.resolve():
        raise ValueError("generated class path escapes the build directory")
    if runtime_classes.exists():
        shutil.rmtree(runtime_classes)
    runtime_classes.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [str(java_bin / "javac.exe"), "-cp", str(asm), "-d", str(compiler_classes),
         *sorted(str(path) for path in (ROOT / "toolchain/backend").glob("*.java"))],
        check=True,
    )
    target_lock = (ROOT / "target/current/target-release.lock.yml").read_text(encoding="utf-8")
    target_bound = "state: BOUND" in target_lock
    if f"verified_binding_set_digest: {digest}" not in target_lock:
        raise ValueError("target lock binding set digest mismatch")
    if device_test and not target_bound:
        raise ValueError("device test requires a bound exact target")
    bindings_enabled = target_bound and (
        device_test
        or (
            "production_hooks_enabled: true" in target_lock
            and "production_features_enabled: true" in target_lock
        )
    )
    lines = plan_lines(records, target_hash, bindings_enabled)
    plan = out / "plan.tsv"
    plan.write_text("".join("\t".join(line) + "\n" for line in lines), encoding="utf-8")
    subprocess.run(
        [str(java_bin / "java.exe"), "-cp", str(compiler_classes) + ";" + str(asm),
         "toolchain.backend.GenerateModule", str(runtime_classes), str(plan)],
        check=True,
    )
    hooks = sum(1 for line in lines if line[0] == "hook")
    segment_classes = SEGMENT_RUNTIME_CLASSES if any(line[0] == "seek" for line in lines) else 0
    settings_classes = SETTINGS_RUNTIME_CLASSES if any(line[0] == "page" for line in lines) else 0
    classes = sorted(runtime_classes.rglob("*.class"))
    litho_classes = int(any(line[0] == "hook" and line[7] == "filter_litho_ads" for line in lines))
    shorts_classes = int(any(line[0] == "hook" and line[7] == "filter_shorts_ads" for line in lines))
    if len(classes) != 8 + hooks + segment_classes + settings_classes + litho_classes + shorts_classes:
        raise ValueError("unexpected generated class count")
    jar = out / "module.jar"
    metadata = {
        "META-INF/xposed/java_init.list": GENERATED_ENTRY + "\n",
        "META-INF/xposed/scope.list": "com.google.android.youtube\n",
        "META-INF/xposed/module.prop": (
            "minApiVersion=102\n"
            "targetApiVersion=102\n"
            "staticScope=true\n"
            "exceptionMode=protective\n"
            "autoHotReload=false\n"
        ),
    }
    with ZipFile(jar, "w", compression=ZIP_DEFLATED) as archive:
        for path in classes:
            archive.write(path, path.relative_to(runtime_classes).as_posix())
        for name, content in metadata.items():
            archive.writestr(name, content)
        archive.write(ROOT / "app/src/main/res/drawable-nodpi/ytm_hellfire.png", "ytm_hellfire.png")
    return jar


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device-test", action="store_true", help="enable bound hook in a development APK")
    print(build(device_test=parser.parse_args().device_test))

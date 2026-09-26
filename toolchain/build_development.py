"""Build development classfile JAR from validated Malbolge records."""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path
from urllib.parse import quote
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from validate_units import ROOT, evaluate_all, validate, validate_programs
from mbx_program import OPCODES, code_uses, disassemble

GENERATED_ENTRY = "io.github.mrxsin.ytmalbolge.generated.Entry"
ACCESS = {"PUBLIC": 1, "PUBLIC_FINAL": 17, "PROTECTED_FINAL": 20, "PUBLIC_STATIC": 9}
SEGMENT_RUNTIME_CLASSES = 4
SETTINGS_RUNTIME_CLASSES = 5
MODULE_PACKAGE = "io.github.mrxsin.ytmalbolge"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def aggregate_hash(paths: list[Path]) -> str:
    digest = hashlib.sha256()
    for path in sorted(paths):
        digest.update(path.relative_to(ROOT).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(bytes.fromhex(sha256(path)))
    return digest.hexdigest()


def zip_bytes(archive: ZipFile, name: str, content: bytes) -> None:
    entry = ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    entry.compress_type = ZIP_DEFLATED
    entry.external_attr = 0o100644 << 16
    archive.writestr(entry, content)


def validate_fallback_fixtures(bindings: dict[str, dict]) -> str:
    """Check checked-in fallback descriptors against their regression fixture."""
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
    counts = [int(match.group(1)) for evidence in binding["positive_evidence"]
              if (match := re.search(r"(?:opcodeCount|insns_size)=(\d+)", evidence))]
    if len(set(counts)) > 1:
        raise ValueError("conflicting opcode counts")
    return [
        binding["expected_member_kind"], shape["class_descriptor"][1:-1].replace("/", "."),
        shape["member_name"], shape["member_descriptor"], str(ACCESS[shape["access"]]),
        shape["superclass"][1:-1].replace("/", "."), str(counts[0] if counts else -1),
    ]


def plan_lines(records: list[dict], target_hash: str, programs) -> list[list[str]]:
    """Lower validated CMG records plus bindings to the backend's Logical Module Plan lines."""
    bindings = {r["endpoint_id"]: r for r in records if r["kind"] == "BindingSpec"}
    hook_programs = [program for program in programs if program.endpoint_id != "-"]
    resolver_policies = [program for program in programs if program.group_id == "resolver_policy"]
    status_policies = [program for program in programs if program.group_id == "status_policy"]
    config_policies = [program for program in programs if program.group_id == "config_policy"]
    if len(resolver_policies) != 1 or len(resolver_policies[0].constants) != 7:
        raise ValueError("one seven-weight resolver policy is required")
    if len(status_policies) != 1 or len(status_policies[0].constants) != 22:
        raise ValueError("one twenty-two-field status policy is required")
    if len(config_policies) != 1 or len(config_policies[0].constants) % 3:
        raise ValueError("one triple-based config policy is required")
    configs = [{"storage_group": group, "config_id": key, "default": fallback == "true"}
               for group, key, fallback in zip(config_policies[0].constants[0::3],
                                                config_policies[0].constants[1::3],
                                                config_policies[0].constants[2::3])]
    configs.sort(key=lambda item: item["config_id"])
    if any(value not in {"true", "false"} for value in config_policies[0].constants[2::3]):
        raise ValueError("config defaults must be boolean")
    config_index = {r["config_id"]: index for index, r in enumerate(configs)}
    try:
        resolver_weights = [str(int(value)) for value in resolver_policies[0].constants]
    except ValueError as error:
        raise ValueError("resolver weights must be integers") from error
    lines = [["target", target_hash], ["resolver", *resolver_weights]]
    program_member_ids = sorted({member for program in hook_programs for member in program.members})
    program_member_index = {member: index for index, member in enumerate(program_member_ids)}

    lines.append(["diagnostic", *status_policies[0].constants])
    lines += [["config", r["storage_group"], r["config_id"], str(r["default"]).lower()] for r in configs]
    hooked = set()
    for program in sorted(hook_programs, key=lambda item: item.endpoint_id):
        binding = bindings[program.endpoint_id]
        guard = -1 if program.config_id is None else config_index[program.config_id]
        lines.append([
            "hook", *member_fields(binding), str(guard),
            f"ytm.{program.endpoint_id}.v2", program.group_id, "MBP1", str(program.startup),
            "1" if program.fail_open else "0",
            str(len(program.constants)), *program.constants,
            str(len(program.members)), *(str(program_member_index[item]) for item in program.members),
            program.code.hex(),
        ])
        hooked.add(program.endpoint_id)

    for endpoint_id in program_member_ids:
        lines.append(["pmember", endpoint_id, *member_fields(bindings[endpoint_id])])
        hooked.add(endpoint_id)

    range_programs = [p for p in hook_programs if code_uses(p.code, OPCODES["ASYNC_LOAD"])]
    if range_programs:
        if len(range_programs) != 1:
            raise ValueError("range service requires one authority program")
        service = range_programs[0]
        if len(service.members) != 1 or len(service.constants) < 23 or len(service.constants[21:]) % 2:
            raise ValueError("invalid range service ABI")
        seek_id = service.members[0]
        seek = bindings[seek_id]
        lines.append(["seek", *member_fields(seek), seek["hard_shape_constraints"]["enum_constant"]])
        categories = list(service.constants[21::2])
        guards = list(service.constants[22::2])
        query = "?" + quote(service.constants[10], safe="") + "="
        query += quote(json.dumps(categories, separators=(",", ":")), safe="")
        query += "&" + quote(service.constants[11], safe="") + "=" + quote(service.constants[17], safe="")
        lines.append(["segments", *service.constants[2:10], query, *service.constants[12:21]])
        lines += [["category", category, str(config_index[guard])]
                  for category, guard in zip(categories, guards)]
        hooked.add(seek_id)
    ui_programs = [p for p in hook_programs if code_uses(p.code, OPCODES["UI_APPLY"])]
    if ui_programs:
        if len(ui_programs) != 1:
            raise ValueError("UI model requires one authority program")
        ui = ui_programs[0]
        roles = ("owner", "screen", "context", "intent", "layout", "icon_space", "set_icon",
                 "new_preference", "set_key", "set_title", "set_summary", "set_order",
                 "group_add", "group_find")
        if len(ui.members) != len(roles):
            raise ValueError("invalid UI model ABI")
        for role, endpoint_id in zip(roles, ui.members):
            lines.append(["member", endpoint_id, *member_fields(bindings[endpoint_id])])
            hooked.add(endpoint_id)
            lines.append(["role", role, endpoint_id])
        values = list(ui.constants)
        if len(values) < 10:
            raise ValueError("incomplete UI model")
        lines.append(["page", values[0], values[1], values[2], MODULE_PACKAGE, values[3], values[4],
                      values[6], values[7], values[8]])
        lines.append(["layouts", values[5]])
        section_count = int(values[9]); at = 10; exposed = []
        for _ in range(section_count):
            section_id, title, item_count = values[at], values[at + 1], int(values[at + 2]); at += 3
            lines.append(["section", section_id, title])
            for _ in range(item_count):
                config_id, title = values[at], values[at + 1]; at += 2
                lines.append(["item", section_id, str(config_index[config_id]), title])
                exposed.append(config_id)
        if at != len(values) or sorted(exposed) != sorted(config_index):
            raise ValueError("UI model must expose every config exactly once")
    if hooked != set(bindings):
        raise ValueError(f"bound Endpoints without backend use: {sorted(set(bindings) - hooked)}")
    if any("\t" in field or "\n" in field for line in lines for field in line):
        raise ValueError("plan field contains a separator")
    return lines


def build() -> Path:
    frames, programs = evaluate_all()
    graph = validate(frames)
    validate_programs(programs, graph)
    records = graph["records"]
    bindings = {r["endpoint_id"]: r for r in records if r["kind"] == "BindingSpec"}
    validate_fallback_fixtures(bindings)
    authority = hashlib.sha256()
    for path in sorted((ROOT / "source").rglob("*.mal")):
        authority.update(path.relative_to(ROOT).as_posix().encode("ascii"))
        authority.update(hashlib.sha256(path.read_bytes()).digest())
    authority_digest = authority.hexdigest()

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
    if compiler_classes.exists():
        shutil.rmtree(compiler_classes)
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
    lines = plan_lines(records, authority_digest, programs)
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
    member_classes = int(any(line[0] == "pmember" for line in lines))
    if len(classes) != 7 + hooks + segment_classes + settings_classes + member_classes:
        raise ValueError("unexpected generated class count")
    hook_programs = sorted((p for p in programs if p.endpoint_id != "-"), key=lambda p: p.endpoint_id)
    hook_index = {program.unit_id: index for index, program in enumerate(hook_programs)}
    provenance = []
    review = []
    for program in sorted(programs, key=lambda p: p.unit_id):
        source = next(path for path in (ROOT / "source").rglob("*.mal")
                      if path.stem == program.unit_id.replace(".", "_"))
        generated = []
        if program.unit_id in hook_index:
            generated_path = runtime_classes / MODULE_PACKAGE.replace(".", "/") / f"generated/ProgramHooker{hook_index[program.unit_id]}.class"
            generated.append({"class": f"{MODULE_PACKAGE}.generated.ProgramHooker{hook_index[program.unit_id]}",
                              "sha256": sha256(generated_path)})
        provenance.append({
            "unit": program.unit_id,
            "source": source.relative_to(ROOT).as_posix(),
            "source_sha256": sha256(source),
            "program_sha256": program.body_sha256,
            "generated": generated,
        })
        review += disassemble(program) + [""]
    raw_sources = sorted((ROOT / "source").rglob("*.mal"))
    compiler_sources = [ROOT / "toolchain/build_development.py", ROOT / "toolchain/mbx_program.py",
                        ROOT / "toolchain/validate_units.py", *sorted((ROOT / "toolchain/backend").glob("*.java"))]
    apk_inputs = [ROOT / "app/build.gradle.kts", ROOT / "app/src/main/AndroidManifest.xml",
                  ROOT / "app/src/main/java/io/github/mrxsin/ytmalbolge/RuntimeResolver.java",
                  ROOT / "toolchain/LOCKFILE"]
    manifest = {
        "schema": "MBP-PROVENANCE-2",
        "authority_sha256": authority_digest,
        "abi": {"name": "MBP1", "sha256": sha256(ROOT / "toolchain/mbx_program.py")},
        "evaluator": {"profile": "MBX-CLASSIC-REF/1", "sha256": sha256(ROOT / "toolchain/mbx_eval.py")},
        "compiler": {"version": "architecture-generation-5", "sha256": aggregate_hash(compiler_sources)},
        "low_level_ir": {"path": plan.relative_to(ROOT).as_posix(), "sha256": sha256(plan)},
        "raw_sources": [{"path": path.relative_to(ROOT).as_posix(), "sha256": sha256(path)}
                        for path in raw_sources],
        "generated_classfiles": [{"path": path.relative_to(runtime_classes).as_posix(), "sha256": sha256(path)}
                                 for path in classes],
        "apk_inputs": [{"path": path.relative_to(ROOT).as_posix(), "sha256": sha256(path)}
                       for path in apk_inputs],
        "units": provenance,
    }
    provenance_path = out / "provenance.json"
    provenance_path.write_text(json.dumps(manifest,
                                           sort_keys=True, indent=2) + "\n", encoding="utf-8")
    review_path = out / "review.txt"
    review_path.write_text("\n".join(review), encoding="utf-8")
    jar = out / "module.jar"
    metadata = {
        "META-INF/xposed/java_init.list": GENERATED_ENTRY + "\n",
        "META-INF/xposed/scope.list": "com.google.android.youtube\n",
        "META-INF/xposed/module.prop": (
            "minApiVersion=102\n"
            "targetApiVersion=102\n"
            "staticScope=true\n"
            "exceptionMode=protective\n"
            "autoHotReload=true\n"
        ),
    }
    with ZipFile(jar, "w", compression=ZIP_DEFLATED) as archive:
        for path in classes:
            zip_bytes(archive, path.relative_to(runtime_classes).as_posix(), path.read_bytes())
        for name, content in metadata.items():
            zip_bytes(archive, name, content.encode("utf-8"))
        zip_bytes(archive, "META-INF/ytm/provenance.json", provenance_path.read_bytes())
        zip_bytes(archive, "META-INF/ytm/review.txt", review_path.read_bytes())
        zip_bytes(archive, "ytm_hellfire.png",
                  (ROOT / "app/src/main/res/drawable-nodpi/ytm_hellfire.png").read_bytes())
    return jar


if __name__ == "__main__":
    print(build())

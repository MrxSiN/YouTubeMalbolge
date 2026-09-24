# Toolchain Boundary

All versions, commits, hashes and resource limits (pinned and `UNFROZEN`) are
authoritative only in `toolchain/LOCKFILE`. This file does not restate them.

Canonical project runtime code path:

```text
validated LMP
→ generated JVM classfiles
→ AGP/R8/D8
→ DEX/APK
```

External toolchain components may be implemented in conventional languages. They are not
an alternate source of project behavior.

The development frontend evaluates each `.mal` unit under pinned limits and
validates its typed records. The classfile backend accepts one uniquely verified
BindingSpec per Endpoint, emits one Hook Controller and one Xposed entry, and
packages no runtime Malbolge evaluator or DexKit library. Gradle runs this
generation before packaging. The `bindinglab` Android app is a separate build
tool for exact-target discovery.

Current source units were generated with the pinned zb3 `malbolge-tools`
linear generator and its output was independently run through the bounded
evaluator. Its MIT notice is in `LICENSES/MALBOLGE-TOOLS-MIT.txt`.

Every release archives exact binary/version/hash provenance privately.

`frame_unit.py` frames a unit's typed records as the exact bytes it must print; that
target is fed to the pinned linear generator with the input opcode removed from its
random filler choices (MBX-CLASSIC-REF/1 prohibits `/`). `build_development.py` lowers
the validated graph to `build/generated/plan.tsv`, which `GenerateModule` compiles (settings entry and manager classes in
`SettingsGenerator` and `DiagnosticsGenerator`).

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

The frontend evaluates each `.mal` unit under pinned limits. Feature units emit
MBP1 executable bytecode; schema units provide validated endpoints, config, UI,
diagnostics, and fallback fixtures. The backend AOT-compiles MBP1 to classfiles.
The APK has no Malbolge evaluator. DexKit is packaged for bounded cold resolution;
warm cache hits validate descriptors without opening it. `toolchain/bindinglab` remains
for reference-fixture research; it is no longer part of the Gradle build.

Current source units were generated with the pinned zb3 `malbolge-tools`
linear generator and its output was independently run through the bounded
evaluator. Its MIT notice is in `LICENSES/MALBOLGE-TOOLS-MIT.txt`.

Every release archives exact binary/version/hash provenance privately.

`assemble_program.py` encodes MBP1 and feeds those bytes to the pinned linear generator
with the input opcode excluded from filler choices. `build_development.py` lowers the
validated graph/programs to a review plan; `GenerateModule` emits the generic hook,
UI-model, range-service, status-transport, and member-table classfiles.

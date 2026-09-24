# Architecture Invariants

`MUST` invariants are release/CI gates; `SHOULD` invariants require an ADR-backed
exception.

```text
INV-001 MUST  Project-owned executable source is only Malbolge.
INV-002 MUST  No executable project source exists before the executable-source freeze gate.
INV-003 MUST  Malbolge is never interpreted inside the module/YouTube runtime.
INV-004 MUST  CMG is the single canonical semantic representation.
INV-005 MUST  Feature does not depend on Vector/libxposed.
INV-006 MUST  Feature does not depend on DexKit.
INV-007 MUST  Feature does not reference physical/obfuscated YouTube symbols.
INV-008 MUST  DexKit exists only in the build/compatibility laboratory.
INV-009 MUST  Runtime target discovery is absent.
INV-010 MUST  Runtime resolution cache is absent.
INV-011 MUST  Production target is one exact Target Release Manifest.
INV-012 MUST  Unsupported/unbound target installs zero behavioral hooks.
INV-013 MUST  Unknown target process installs zero behavioral hooks.
INV-014 MUST  Ambiguous target binding blocks release.
INV-015 MUST  One Endpoint has one authoritative BindingSpec.
INV-016 MUST  Binding acceptance requires uniqueness after hard validation.
INV-017 MUST  No fallback resolver chain exists.
INV-018 MUST  One physical hook has one Hook Controller owner.
INV-019 MUST  One target executable/phase has at most one project physical hook.
INV-020 MUST  Hook install/replace/remove operations are idempotent.
INV-021 MUST  Duplicate hooks do not accumulate after reload.
INV-022 MUST  HookAbiId is stable within a hot-reload epoch.
INV-023 MUST  HookAbiId is exempt from release remapping within that epoch.
INV-024 MUST  Feature toggles normally use ConfigSnapshot, not hook churn.
INV-025 MUST  Runtime config view is immutable.
INV-026 MUST  ConfigSnapshot publication is atomic.
INV-027 MUST  Invalid config never replaces last-known-good runtime config.
INV-028 MUST  RemotePreferences is the only persistent configuration truth.
INV-029 MUST  HOT callbacks perform no disk I/O.
INV-030 MUST  HOT callbacks perform no network I/O.
INV-031 MUST  HOT callbacks perform no Binder/config service calls.
INV-032 MUST  HOT callbacks perform no target discovery.
INV-033 MUST  HOT callbacks perform no Malbolge interpretation.
INV-034 MUST  HOT callbacks perform no project VM dispatch.
INV-035 MUST  HOT callbacks perform no reflection member lookup.
INV-036 MUST  HOT callbacks perform no normal diagnostic logging.
INV-037 SHOULD HOT callbacks perform no project JNI; baseline has no project native runtime.
INV-038 SHOULD HOT callbacks allocate no module-owned objects.
INV-039 MUST  Effect order is explicit and independent of registration/source order.
INV-040 MUST  Mutating conflict has an explicit compatible composition policy.
INV-041 MUST  Capability exists only when it adds independent semantic value.
INV-042 MUST  Process/lifecycle state has exactly one declared owner.
INV-043 MUST  Feature state has exactly one declared owner.
INV-044 MUST  API-102 code reload transfers only classloader-neutral declared state.
INV-045 MUST  Unsafe multi-hook generation transitions require restart.
INV-046 MUST  Target/binding epoch change requires restart.
INV-047 MUST  Production hook exception mode is PROTECTIVE.
INV-048 MUST  Generated module has exactly one Java Xposed entry.
INV-049 MUST  Generated scope contains only com.google.android.youtube.
INV-050 MUST  Generated module has no project native init entry.
INV-051 MUST  Vector/libxposed API implementation is not bundled as project code.
INV-052 MUST  Release hardening cannot add/remove/reorder semantic Effects.
INV-053 MUST  Debug and hardened release pass semantic-equivalence vectors.
INV-054 MUST  Baseline performs no post-R8 semantic DEX rewriting.
INV-055 MUST  Release contains no private provenance.
INV-056 MUST  Release contains no BindingSpec evidence/history.
INV-057 MUST  Release contains no historical target binding set.
INV-058 MUST  Release contains no Malbolge source.
INV-059 MUST  Baseline has no project-owned native runtime/control plane.
INV-060 MUST  Baseline has no target-process background service or compatibility daemon.
INV-061 MUST  Module has no remote executable payload path.
INV-062 MUST  Diagnostics contain no account/auth/session/user-content data.
INV-063 MUST  Obfuscation is never treated as a confidentiality boundary.
INV-064 MUST  Normal feature addition requires no central framework switch edit.
INV-065 MUST  One semantic responsibility has one authoritative representation.
INV-066 MUST  Canonical CMG bytes are independently parsed/validated.
INV-067 MUST  Behavioral Malbolge units cannot consume release-layout seed.
INV-068 MUST  Clean builds use pinned toolchain/target inputs.
INV-069 MUST  Public APK indexes cannot directly populate production target lock.
INV-070 MUST  Production target lock is derived from exact reference artifact bytes.
INV-071 MUST  Normal target-ready activation uses onPackageReady/final ClassLoader.
INV-072 MUST  onPackageLoaded hooks require explicit early-availability contract.
INV-073 MUST  Module support claims are backed by a frozen reference runtime environment.
INV-074 MUST  Module minSdk is derived from exact target/runtime requirements, not Vector's broad minimum alone.
```

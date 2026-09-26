# Phase 0 — Technology Characterization
## v4 Architecture Evidence Dossier — 2026-09-23

**Purpose:** close architecture assumptions using current authoritative documentation
before any project executable source is created.

**Status:** documentary characterization substantially complete; device/runtime probes
remain pending.

---

## 1. Results summary

### Proven from current upstream documentation

- Vector stable is v2.2, release commit `88f8e1f`.
- Vector v2.2 integrates libxposed API 102 hot reload.
- libxposed API stable is 102.0.0, release commit `45e7c5c`.
- libxposed service stable is 102.0.0.
- API 102 provides hook IDs and atomic per-hook replacement.
- `onHotReloading` may veto reload by returning false.
- reload state can be saved by the old generation and read by the new generation.
- new generation receives old hook handles.
- hook handles can be unhooked or replaced.
- normal app lifecycle provides `onPackageLoaded` and `onPackageReady`.
- `onPackageReady` exposes the final package ClassLoader.
- RemotePreferences are available from the injected Xposed interface when remote
  capability is present.
- the manager app accesses the same remote preference store through libxposed service.
- modern module metadata uses `META-INF/xposed/java_init.list`, `module.prop`, and
  optional `scope.list`.
- API 102 supports `autoHotReload=true`.
- official libxposed R8 rules adapt `java_init.list` and preserve/allow obfuscation of
  the module entry.
- DexKit latest stable is 2.3.0, commit `c9cd12a14b75409bebd2f73e4dfc5ff575df3eb8`.
- DexKit 2.3 reduces allocation/copy overhead and fixes shared-pool cleanup crashes.
- current Android stable AGP is 9.4.1.
- AGP 9.4 supports compile API 37 and uses Gradle 9.6.0 / JDK 17 compatibility baseline.

### Architecture decisions now frozen from those facts

- Vector v2.2 + libxposed API 102 only.
- libxposed service 102.0.0 for manager/framework IPC.
- exactly one project Xposed entry.
- static scope only `com.google.android.youtube`.
- global `exceptionMode=protective`.
- `autoHotReload=true` after device acceptance.
- stable opaque HookAbiId for every physical project hook.
- injected runtime config reads from framework RemotePreferences once/low-frequency,
  compiles immutable ConfigSnapshot, then hot callbacks read the snapshot only.
- manager writes the same RemotePreferences via `XposedService`.
- one build backend: generated JVM classfiles → AGP/R8/D8 → DEX.
- AGP 9.4.1 / Gradle 9.6.0 / JDK 17 / compileSdk 37 / targetSdk 37.
- DexKit 2.3.0 serves bounded runtime resolution and the binding lab.

### Still requires empirical/device characterization

- exact Vector v2.2 reload callback ordering in a real injected YouTube process;
- whether any normal package callbacks are replayed after reload;
- exact behavior when only part of a multi-hook replacement sequence fails;
- old module classloader collection after replacement;
- RemotePreferences listener callback thread and reconnection behavior under Vector;
- exact YouTube process names and final ClassLoader topology;
- exact stable YouTube reference APK/split/signing set;
- low-end performance baseline;
- Vector v2.2 API/service submodule SHAs are pinned in `toolchain/LOCKFILE`.

The architecture deliberately does not depend on optimistic answers to these questions.

---

## 2. Vector v2.2 characterization

Vector identifies itself as a Zygisk ART-hooking framework built around LSPlant. It
supports both legacy and modern standards, but this project uses modern libxposed only.

v2.2 release notes state that API 102 enables module code replacement inside a process
without killing it and that hookers can be swapped atomically.

Architectural consequence:

```text
physical hook identity
    =
stable runtime ABI
```

Therefore HookAbiId is not an obfuscation-only value. It must remain stable across
hot-reload-compatible generations.

---

## 3. libxposed API-102 surface used by v4

The project adapter needs only this conceptual subset:

```text
XposedModule lifecycle:
  onModuleLoaded
  onPackageLoaded
  onPackageReady
  onHotReloading
  onHotReloaded

framework info:
  getApiVersion
  getFrameworkName
  getFrameworkVersion
  getFrameworkVersionCode
  getFrameworkProperties

hooking:
  hook(Executable)
  HookBuilder.setPriority
  HookBuilder.setExceptionMode
  HookBuilder.setId
  HookBuilder.intercept
  HookHandle.getExecutable
  HookHandle.getId
  HookHandle.unhook
  HookHandle.replaceHook

remote configuration:
  getRemotePreferences

logging:
  log(...)
```

No Feature is permitted to see these types directly.

---

## 4. Lifecycle result

Documented lifecycle facts:

```text
onModuleLoaded
  process-level module entry

onPackageLoaded
  package/default-ClassLoader boundary

onPackageReady
  package final-ClassLoader / AppComponentFactory-ready boundary

onHotReloading
  old generation; may save state and return true/false

onHotReloaded
  new generation; receives saved state + old hook handles
```

v4 therefore uses `onPackageReady` as the normal target-ready phase.

An Endpoint may declare `PACKAGE_LOADED` only when it genuinely must exist before the
final ready boundary.

### Important robustness rule

The new generation MUST NOT depend on framework replay of `onModuleLoaded`,
`onPackageLoaded`, or `onPackageReady`.

Even if a particular framework implementation happens to replay a callback, the reload
path restores all necessary project state from the API-102 reload context or declares
restart required.

This makes the architecture correct across the unresolved implementation detail.

---

## 5. Hot reload result

Documented API-102 primitives are sufficient for a clean hot-reload model:

```text
old generation
  onHotReloading
    → save classloader-neutral state
    → optionally veto

new generation
  onHotReloaded
    → read saved state
    → enumerate old handles
    → match stable ID
    → replace / unhook / install as required
```

Per-hook replacement can be treated as atomic.

A collection of several independent hook replacements is NOT assumed to be a framework
transaction.

Therefore:

```text
single-hook feature:
  may be RELOAD_ATOMIC_ENDPOINT

multi-hook feature:
  RELOAD_MIXED_SAFE only with proof
  otherwise RESTART_REQUIRED
```

---

## 6. Configuration result

There is one framework-backed preference namespace.

Injected process:

```text
XposedModule.getRemotePreferences(group)
→ SharedPreferences-compatible remote object
→ listener/read outside HOT callback
→ ConfigSnapshot
→ atomic publication
```

Manager process:

```text
XposedServiceHelper / XposedService
→ service.getRemotePreferences(group)
→ edit same preference store
```

This is cleaner than a custom Binder protocol or file bridge.

Required runtime capability:

```text
PROP_CAP_REMOTE
```

If unavailable, the module does not invent a fallback configuration transport.

---

## 7. Packaging/R8 result

Required generated resources:

```text
META-INF/xposed/java_init.list
META-INF/xposed/module.prop
META-INF/xposed/scope.list
```

No project native init entry.

Selected module properties:

```properties
minApiVersion=102
targetApiVersion=102
staticScope=true
exceptionMode=protective
autoHotReload=false
```

After the device reload acceptance suite passes:

```properties
autoHotReload=true
```

Official libxposed R8 guidance is represented semantically as:

```text
adapt java_init.list to obfuscation
keep XposedModule subclass constructor
allow optimization
allow obfuscation
suppress obsolete annotation warnings as required
```

This is part of the generated backend contract, not handwritten application logic.

---

## 8. Android build toolchain result

Current stable Android build baseline:

```text
Android Gradle Plugin: 9.4.1
Gradle:                9.6.0
JDK:                   17
compileSdk:             37
targetSdk:              37
SDK Build Tools:        36.0.0
```

The project has no Kotlin implementation source, therefore KGP is not part of the
project implementation path.

`minSdk` remains:

```text
max(
  Vector practical floor,
  libxposed API requirement,
  exact supported YouTube target requirement,
  generated manager/runtime API requirements
)
```

Historical note: this freeze rule was superseded by ADR-040 runtime resolution.

---

## 9. DexKit result

DexKit 2.3.0 is the current stable release.

Relevant 2.3 properties:

- composite matchers (`allOf`, `anyOf`, `noneOf`, `not`);
- improved query/native matcher performance;
- optimized native caches, string queries, and zero-copy eligible DEX loading;
- fixed shared thread-pool cleanup and cache isolation defects.

The runtime uses one bounded bridge only on descriptor-cache misses, then closes it.

`DexKitCacheBridge` remains unnecessary because the project validates and persists
descriptors in its own app/version-scoped cache.

---


## 9A. Malbolge reference result

The source-language semantic ambiguity is now closed.

`MBX-CLASSIC-REF/1` uses Ben Olmstead's original 1998 interpreter as normative semantic
authority. Where historical prose and implementation differ, the interpreter wins.

Project-specific deterministic restrictions:

```text
ASCII source
C-locale ASCII whitespace
minimum 2 logical source words
no `/` input instruction in project semantic-generator units
no ambient stdin/environment/time/random/network
bounded execution/output
```

The original interpreter's `<` instruction is output and `/` is input. Conformance
testing verifies both reference behaviors even though authored project units prohibit
input.

The exact raw reference-source SHA-256 is pinned in `toolchain/LOCKFILE`.
The evaluator and conformance suite remain pending.

See `phase0/MALBOLGE_REFERENCE_CHARACTERIZATION.md`.

---

## 10. What Phase 0 does not do

Phase 0 does not:

- author production Malbolge;
- bind YouTube methods;
- create product features;
- claim a YouTube version compatible;
- enable auto hot reload;
- claim performance targets;
- add fallback behavior.

Those belong to later gates.

---

## 11. Phase 0 disposition

Documentary characterization is sufficiently complete to proceed to the **controlled
device/toolchain probes**, but Gate A remains open until those empirical tests and the
Malbolge reference evaluator pin are completed.

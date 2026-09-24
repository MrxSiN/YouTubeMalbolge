# Malbolge Xposed Module Blueprint v4 — Architecture-First Redesign
## Audited Revision — 2026-09-23

**Status:** architecture only; not implementation  
**Target package:** `com.google.android.youtube`  
**Primary/only Xposed framework target:** JingMatrix/Vector v2.2 stable  
**Module API:** libxposed API 102  
**Discovery:** DexKit 2.2 build/compatibility laboratory only  
**Project runtime executable representation:** ordinary optimized DEX  
**Project-owned executable source:** Malbolge only, after architecture freeze  
**Compatibility:** one module release × one exact validated current YouTube release  
**Production target:** `BOUND` (YouTube 21.37.42)  
**Production features/hooks:** enabled for release 1.0.0
**Phase-0 documentary characterization:** completed; device probes pending

Terminology labels used below:

- **FACT** — current upstream/public documentation.
- **RESEARCH FINDING** — conclusion from researched facts.
- **RECOMMENDATION** — v4 architectural decision.
- **ASSUMPTION** — deliberate temporary premise.
- **OPEN QUESTION** — requires a prototype/device measurement.

---

## 1. Executive summary

v4 deliberately removes the most expensive v3 runtime architecture.

The smallest complete design is:

```text
Malbolge source
    ↓
pinned deterministic frontend
    ↓
Canonical Module Graph (CMG)
    ↓
independent semantic validator
    ↓
Logical Module Plan (LMP)
    ↑
Verified Target Binding Set ← offline DexKit laboratory ← exact YouTube artifact set
    ↓
generated JVM classfiles
    ↓
standard R8/D8
    ↓
Vector/API-102 module APK
    ↓
exact target/process gate
    ↓
immutable ConfigSnapshot
    ↓
Hook Controller
    ↓
typed Semantic Endpoint pipelines
    ↓
feature effects
```

The target process contains no Malbolge interpreter, DexKit, resolver cache, custom
startup VM, project native control plane, compatibility daemon, or periodic scanner.

Static opacity is created primarily by language distance, generated physical structure,
R8 optimization/obfuscation, compact binding data, private semantic maps, and
deterministic layout diversification that cannot change behavior.

Hot loading is defined narrowly and truthfully: configuration publication is hot;
ordinary feature toggles are hot; API-102 can replace individual hook implementations;
whole-feature live replacement is allowed only when mixed generations are safe; target
or binding epoch changes require restart.

---

## 2. Architecture-first philosophy

The required design order is authoritative:

```text
1 research technology/constraints
2 establish requirements
3 define boundaries
4 define semantic models/contracts
5 define lifecycle/state models
6 define dependency/ownership rules
7 define runtime/hot-load behavior
8 define build/compiler architecture
9 define compatibility strategy
10 define performance architecture
11 define release/hardening architecture
12 define testing/validation
13 define implementation phases
14 only then implement product features
```

No production hook or feature exists before the freeze gates.

Earlier placeholder Malbolge stubs have been removed because architecture-only work
should not masquerade as executable implementation.

---

## 3. Goals

The module architecture MUST provide:

- Vector-native modern API-102 integration;
- one exact current YouTube target;
- Malbolge-originated project executable behavior;
- no runtime Malbolge interpretation;
- one canonical semantic truth;
- one binding strategy per Endpoint;
- typed semantics independent of obfuscated target members;
- deterministic effect composition;
- explicit state ownership;
- safe immutable hot configuration;
- honest API-102 code reload boundaries;
- low-end-device practical runtime behavior;
- compact bounded diagnostics;
- extreme release opacity without hostile runtime behavior;
- private provenance sufficient for AI/maintainer repair;
- fail-closed target ambiguity;
- architecture that grows by adding features, not central switches.

---

## 4. Non-goals

Not supported:

- historical YouTube compatibility matrix in runtime;
- legacy Xposed API;
- alternate Xposed framework backend;
- runtime DexKit/discovery;
- runtime resolution cache;
- resolver fallback ladder;
- generic scripting/plugin engine;
- custom target-process VM;
- project-owned native control plane baseline;
- compatibility background service;
- remote executable payload;
- anti-debugger/anti-emulator sabotage;
- root/Vector hiding;
- self-modifying executable memory;
- production product features before architecture freeze.

---

## 5. Technology research findings

### Vector

**FACT:** Vector v2.2 stable (`88f8e1f`, 2026-08-04) brings libxposed API 102 hot
reload and documents in-process module replacement and atomic replacement of individual
hookers.

**RESEARCH FINDING:** Vector is not merely another LSPosed loader. API-102 lifecycle and
hook identity should shape the project architecture directly.

### libxposed

**FACT:** libxposed API stable is 102.0.0 (`45e7c5c`).

**FACT:** libxposed service 102.0.0 is the communication layer between module app and
framework.

**RECOMMENDATION:** API 102 only. Use one framework-managed RemotePreferences store:
the manager accesses it through libxposed service 102, while the injected module accesses
it through the Xposed interface.

### DexKit

**FACT:** DexKit 2.x exposes DEX metadata without requiring target reflection/ClassLoader
and supports cross-platform analysis.

**RESEARCH FINDING:** latest-only compatibility removes the main reason to ship DexKit
into YouTube.

### Android/ART/R8

**FACT:** Android recommends R8 release optimization and full mode has been default since
AGP 8.0.

**FACT:** Android warns that tools replacing/modifying R8 output can regress runtime
performance.

**RECOMMENDATION:** one standard classfile→R8/D8→DEX backend. No baseline destructive
post-R8 rewriter.

### YouTube distribution

**RESEARCH FINDING:** contemporary public indexes show stable, beta, split and signing
variants at the same time.

**RECOMMENDATION:** “latest supported” means the exact release artifact set selected and
hashed by release engineering, not whatever version string a website currently lists.


### Runtime environment

**FACT:** current Vector documentation states Android 8.1 through Android 17 Beta support
and requires a recent Magisk or KernelSU environment with Zygisk enabled.

**RECOMMENDATION:** that broad range is not the module support promise. Freeze one
reference runtime environment and derive module `minSdk` from the exact supported
YouTube target and actual project requirements.

---

## 6. Vector architecture findings

Vector/API-102 is the only external hook boundary.

Generated module metadata:

```text
minApiVersion=102
targetApiVersion=102
staticScope=true
exceptionMode=protective
autoHotReload=<false until reload gate passes, then true>
```

The APK has exactly one generated Java entry in `java_init.list` and scope contains only
`com.google.android.youtube`.

No project `native_init.list` is generated.

The project adapter exposes only the API-102 subset documented in Phase 0:

```text
lifecycle
hook install
hook stable ID
hook replacement
hook removal
RemotePreferences access
framework logging
hot-reload lifecycle
```

Feature code never sees framework types.

### Hook identity

`HookAbiId` is a first-class compatibility ABI.

It remains stable within one `hot_reload_epoch` and is exempt from release ID
diversification that would prevent new code from identifying old handles.

### Framework priority

Framework hook priority is not used to compose project features. Project effect order is
inside the generated Endpoint pipeline.


### Lifecycle mapping

Normal activation is anchored to `onPackageReady` and the final YouTube ClassLoader.

`onPackageLoaded` is reserved for explicitly declared `PACKAGE_LOADED` Endpoints that
must exist before the final app-ready boundary. It is not a second competing
initialization path.

API-102 hot reload is handled through the dedicated reload callbacks; the architecture
does not assume normal package callbacks are replayed in the new generation.

See `contracts/vector-lifecycle-mapping.md`.

---

## 7. Malbolge feasibility findings

A literal runtime Malbolge program cannot practically implement Android/Vector object
interaction at acceptable cost.

The technically coherent interpretation is:

> every project-owned executable behavior originates from Malbolge, then is compiled
> ahead of time into ordinary runtime artifacts.

External infrastructure is not a second project language:

- Malbolge evaluator/compiler;
- semantic validator;
- classfile backend;
- Gradle/AGP;
- R8/D8;
- Android packaging/signing;
- Vector/libxposed;
- DexKit build laboratory.

### Profile

`MBX-CLASSIC-REF/1` is pinned to one reference evaluator artifact/hash before source work.

Behavioral units have no time/random/network/filesystem/environment input.

### Source boundary

Project executable source after freeze:

```text
*.mal
*.mbg
```

Generated Java/Kotlin/Smali/C/C++/Rust/Python source is not used as an intermediate
project source language.

### Exact boundary still requiring prototype

**OPEN QUESTION:** the concrete reference evaluator implementation/hash and resource
limits are not yet frozen.

That is a toolchain question, not a reason to introduce another implementation language.

---

## 8. Architectural principles

### SOLID

**SRP:** Process Coordinator, Hook Controller, Config Publisher, Binding Materializer,
Diagnostic Sink and individual Features each have one owner/reason to change.

**OCP:** adding a normal feature adds a Feature + Effects + tests. No central framework
switch statement changes.

**LSP:** inheritance is not used as an architectural decoration. Any interchangeable
adapter must preserve the exact contract; composition is preferred.

**ISP:** Feature sees only required Endpoints/Capabilities, ConfigView, FeatureState and
DiagnosticSink.

**DIP:** feature semantics depend inward on project contracts, never Vector, DexKit,
reflection, target symbols or storage.

### General principles

**DRY:** machine/contract authority is centralized by `architecture/authority-map.yml`.

**KISS:** one framework, one backend, one target, one binding path, no project native
control plane, no custom VM.

**YAGNI:** no multi-version compatibility, alternate runtime backend, cloud/remote config,
generic plugin platform or runtime rediscovery.

**Law of Demeter:** a Feature cannot navigate framework/resolver/controller internals.

**Boy Scout Rule:** obsolete v3 concepts are removed rather than wrapped indefinitely.

**Separation of Concerns:** authorship, semantic model, target binding, runtime hooks,
configuration, lifecycle, diagnostics, compiler, packaging and release hardening are
separate boundaries.

---

## 9. System context diagram

```text
                       PRIVATE DEVELOPMENT / RELEASE

 Maintainer / AI
      ↓
 Malbolge source
      ↓
 deterministic frontend
      ↓
 Canonical Module Graph ─────────────→ normalized semantic diff
      ↓
 independent validator
      ↓
 Logical Module Plan
      ↑
      │
 Verified Target Binding Set
      ↑
 Binding Laboratory ── DexKit 2.2 ── exact reference YouTube artifact set
      ↓
 generated JVM classfiles
      ↓
 R8/D8 + Release Layout Plan
      ↓
 APK ────────────────────────────────→ private provenance/maps (not shipped)

                              RUNTIME

 Vector/API 102
      ↓
 generated entry
      ↓
 package/process/exact-target gate
      ↓
 RemotePreferences → ConfigSnapshot
      ↓
 materialize verified bindings
      ↓
 Hook Controller
      ↓
 Semantic Endpoint pipelines
      ↓
 Feature Effects
```

---

## 10. Layered architecture

```text
L0 Authorship
   Malbolge source

L1 Canonical semantics
   CMG + schemas + contracts

L2 Validated logical plan
   LMP

L3 Compatibility binding
   BindingSpec + Verified Target Binding Set

L4 Generated platform adapter
   module entry + Vector adapter + target member adapter

L5 Runtime semantic kernel
   Process Coordinator + Config Publisher + Hook Controller + Endpoint pipelines

L6 Product semantics
   Capabilities + Features + Effects

L7 Release representation
   R8/D8 + Release Layout Plan + package metadata
```

Dependency direction flows inward/downward through declared interfaces; L6 never reaches
around L5/L4 into framework or target implementation.

---

## 11. Dependency rules

Allowed:

```text
Feature
→ Capability / Endpoint
→ Effect
→ generated Endpoint pipeline
→ TargetBinding adapter
→ Vector adapter
```

Side dependencies:

```text
Feature → ConfigView
Feature → declared FeatureState
Feature → DiagnosticSink
Manager → libxposed service / RemotePreferences
Build Binding Lab → DexKit
```

Forbidden:

```text
Feature → Vector/libxposed
Feature → DexKit
Feature → Method/Field/Class physical member
Feature → obfuscated YouTube name
Feature → persistent preferences
Feature → release layout/hardening
Feature → global service locator
```

---

## 12. Canonical semantic model

The **Canonical Module Graph (CMG)** is the one private semantic truth.

Node families:

```text
Module
Function
Feature
Capability
Endpoint
Effect
BindingSpec
TargetBinding
ConfigItem
RuntimeState
LifecycleHandler
DiagnosticEvent
Component
Resource
```

CMG is generated from Malbolge; it is not hand-authored as a second language.

Normative serialized form is deterministic CBOR. A normalized JSON projection exists
only for review/semantic diffs.

The independent validator parses serialized CMG bytes from scratch.

---

## 13. Capability model

Capabilities are retained, but sparse.

Create a Capability only when at least one is true:

1. it normalizes/aggregates semantic information;
2. it owns meaningful lifecycle/state;
3. it hides several target-specific Endpoints;
4. multiple independent Features consume it.

A one-Endpoint alias is rejected as needless abstraction.

Example legitimate shapes:

```text
CAP_VIDEO_POSITION
  ← several raw player-state/clock Endpoints
  → normalized position/timebase

CAP_PLAYER_RESPONSE
  ← parsing/access Endpoints
  → stable semantic response view
```

If a Feature can consume one Endpoint directly, no Capability is created.

---

## 14. Semantic Endpoint ABI

Operation classes:

```text
EVENT
OBSERVATION
DECISION
TRANSFORM
ACTION
```

Every Endpoint defines:

```text
endpoint_id
meaning
operation_class
input_schema
output_schema
nullability
phase
availability_phase
thread_context
reentrancy
frequency
lifetime
allowed_side_effects
mutation_permissions
failure_behavior
composition_policy
ordering_constraints
performance_class
reload_class
process_scope
binding_spec_id
```

A Feature never receives:

- raw hook argument arrays as its architecture API;
- framework hook handles;
- DexKit results;
- reflection member objects;
- target member names.

Generated adapters map physical invocation to typed semantic values and back.

---

## 15. Feature model

A Feature contains:

```text
feature_id
intent
required_capabilities[]
required_endpoints[]
config_items[]
effects[]
state_owner
process_scope
performance_class
reload_class
diagnostic_identity
```

Feature states:

```text
UNAVAILABLE
OFF
ON
RESTART_REQUIRED
```

Feature enablement cannot override missing required Endpoint compatibility.

No feature registry with duplicated metadata is manually maintained; registries/tables
are derived from CMG.

---

## 16. Composition/conflict model

One physical hook may host several semantic Effects.

Pipeline:

```text
host input
→ schema mapping
→ PRE_TRANSFORM
→ DECISION
→ ORIGINAL or declared replacement
→ POST_TRANSFORM
→ OBSERVATION / EVENT
→ host output
```

Minimal composition operators:

```text
EXCLUSIVE
ORDERED_TRANSFORM
FIRST_NON_ABSTAIN
BOOLEAN_AND
BOOLEAN_OR
BITSET_UNION
OBSERVE_ALL
```

There is no accidental registration-order behavior and no generic `LAST_WINS` escape
hatch.

Two mutating Effects without a valid common composition policy fail semantic validation.

---

## 17. Target binding/discovery architecture

Discovery is offline/release-time.

```text
BindingSpec
+ exact target APK/code splits
+ DexKit
→ candidates
→ hard evidence predicates
→ post-resolution validation
→ exactly one candidate?
     yes → Verified Target Binding
     no  → release block / Endpoint unavailable
```

### One resolver

One Endpoint has one BindingSpec/pipeline.

Multiple independent evidence predicates are desirable. Multiple competing fallback
resolvers are not.

### Scoring

Scores may sort candidates for maintainer analysis. They are not production truth.

Production acceptance requires exactly one candidate satisfying the complete hard
contract.

### Target identity

Target Release Manifest captures:

- package;
- stable/beta channel selection;
- version name/code;
- approved signer set;
- base APK identity;
- all relevant code-bearing split identities/DEX digests;
- binding set digest;
- allowed process names;
- reload epoch.

Public APK indexes cannot populate this directly.

---

## 18. Hot-loading architecture

Hot loading is designed, not bolted on.

### Configuration hot reload

Persistent RemotePreferences change → validate → create new ConfigSnapshot → atomically
publish.

HOT callbacks never call storage/Binder.

### Feature OFF

Publish new snapshot. Keep normal hook topology.

### Feature ON

If required hooks already exist: snapshot-only.

If a declared `runtime-installable` hook is absent: Hook Controller installs exactly
once, validates, then publishes ON.

If lifecycle passed an early-only Endpoint: mark `RESTART_REQUIRED`.

### Hook removal

Not ordinary feature-toggle behavior.

Used for:

- removed hook in code generation;
- fail-closed target incompatibility;
- API-102 generation retirement;
- explicit terminal endpoint failure where safe.

### Module code reload

Use stable HookAbiId to match generations.

Individual matching hook replacement may use API-102 atomic hook replacement.

New/removed identities install/unhook explicitly.

### Atomicity truth

A set of several physical hooks is not assumed to replace transactionally.

Feature code that cannot tolerate a mixed generation is restart-only.

### Target update

A new exact YouTube target/binding epoch is not live-migrated in an existing process.

Restart/relaunch is required.

---

## 19. Lifecycle/state machines

Process, Endpoint, Feature, Configuration and Reload state machines are canonical in
`contracts/lifecycle-hot-reload.md`.

Summary: out-of-scope, unsupported-target and identity-invariant failures install zero
behavioral hooks; only independent endpoint failures may degrade; ineligible reloads are
rejected while the old generation stays active; Process Coordinator alone owns
transitions; no subsystem owns a generic retry loop.

---

## 20. Threading/concurrency model

Contexts:

```text
CALLER_THREAD
MAIN_THREAD_REQUIRED
BINDER_POSSIBLE
INIT_THREAD
MANAGER_UI
BUILD_ONLY
```

Rules:

- hooks normally execute on the original caller thread;
- no project thread pool exists by default;
- HOT callbacks never block waiting for another thread;
- ConfigSnapshot is immutable + atomically published;
- TargetBindingSet is immutable;
- Hook Controller mutations are serialized;
- feature mutable state is atomic or explicitly thread-confined;
- diagnostic storage is bounded;
- build/discovery state does not exist in target process.

If a semantic action requires main thread, Endpoint contract states it and the action is
designed as WARM/ACTION—not hidden inside a HOT callback.

---

## 21. Configuration architecture

One persistent truth:

```text
framework-managed RemotePreferences namespace
```

Manager writes it through libxposed service 102.

The injected module accesses the same store through `getRemotePreferences`, observes
low-frequency changes, and compiles them into ConfigSnapshot.

No second local config database, broadcast fallback, file polling or per-hook reads.

Invalid configuration never replaces the running last-known-good snapshot.

Configuration schema/migrations are CMG-derived so IDs/defaults/UI/runtime parsing cannot
drift independently.

---

## 22. Manager/runtime process boundary

Manager is configuration/control/diagnostics only.

It may:

- edit RemotePreferences;
- display exact target compatibility;
- show framework/API status;
- show bounded diagnostics;
- request module code reload through framework service where supported;
- explain restart-required state;
- show licenses/privacy.

It may not:

- run DexKit;
- prewarm target bindings;
- keep a compatibility cache;
- run a background resolver service;
- download executable behavior.

Each allowed YouTube process is an independent lifecycle/reload unit; cross-process
transactional reload is not assumed.

---

## 23. Performance architecture

### HOT

MUST NOT:

```text
disk/network I/O
Binder/config service calls
DexKit/discovery
Malbolge interpreter
project VM dispatch
reflection member lookup
normal logging
```

Baseline v4 contains no project JNI runtime.

HOT SHOULD avoid module-owned allocation and global locks.

### WARM

Screen/lifecycle/user-action work. Bounded allocation is acceptable; discovery is not.

### COLD

Bootstrap, config validation, member materialization, manager work, diagnostics export,
and build-time analysis.

### Measurement

Do not freeze guessed microsecond targets.

Freeze budgets only after baseline p50/p95/p99 and retained-heap measurement on one
chosen low-end rooted device.

---

## 24. Diagnostics architecture

Record:

```text
timestamp_delta
diag_code
opaque_semantic_id
lifecycle_transition
reason_code
small_numeric_context
```

Categories:

```text
BOOT TARGET CONFIG BIND HOOK RELOAD CALLBACK COMPAT
```

Rules:

- fixed bounded buffer;
- no per-frame logs;
- no account data;
- no tokens/cookies/auth headers;
- no video/comment/watch-history payloads;
- no unbounded stack traces in release.

A callback failure should preserve original YouTube behavior where safe and open the
affected Effect/Endpoint circuit for the process rather than retrying indefinitely.

Private provenance maps opaque IDs back to semantics for owner diagnosis.

---

## 25. Build/compiler architecture

```text
Malbolge units
→ MBX-CLASSIC-REF/1 evaluator
→ framed typed records
→ CMG fragments
→ semantic linker
→ deterministic CBOR CMG
→ independent validator
→ LMP
→ generated JVM classfiles
→ R8/D8
→ DEX/APK
```

### Why classfiles first

This avoids maintaining a custom DEX verifier/layout backend and lets Android's standard
optimizer see the whole generated program.

There is exactly one executable backend path.

### Malformed build behavior

Any invalid frame, duplicate ID, graph violation, evaluator budget overflow, failed
binding, or semantic validator failure aborts the build.

### Provenance

Every final physical method can be traced privately:

```text
Malbolge unit
→ CMG Function/Effect
→ LMP node
→ pre-R8 generated class/member
→ R8 mapping
→ final artifact
```

---

## 26. Toolchain trust model

Trust is staged.

```text
Malbolge source
↓ external evaluator output = UNTRUSTED
framed records
↓ parser/linker
serialized CMG = UNTRUSTED
↓ independent validator
validated CMG/LMP = TRUSTED SEMANTIC INPUT
↓ backend/R8/D8
generated artifact
↓ artifact verifier/tests
release candidate
```

The frontend and validator must not share hidden in-memory semantic objects.

Binding evidence is untrusted until uniqueness + hard validation passes.

Release transformer has no authority to change Feature/Effect semantics.

Pinned toolchain versions/hashes are the authority in `toolchain/LOCKFILE`.

---

## 27. Release architecture

Release pipeline:

```text
validated LMP
+ Verified Binding Set
+ generated Android metadata
→ generated classfiles/resources
→ R8 full optimization/minification
→ D8/package
→ leakage/equivalence/performance checks
→ sign
```

The Release Layout Plan is representation-only.

Allowed controls:

- generated physical names;
- non-ABI numeric mappings;
- table ordering;
- constant bank placement;
- cold binding-data encoding;
- class grouping hints;
- debug/source metadata policy;
- deterministic seed.

Forbidden controls:

- add/remove Effects;
- change effect order;
- change feature logic;
- change lifecycle semantics;
- remap HookAbiId inside reload epoch.

---

## 28. Reverse-engineering-hardening architecture

Desired model:

> difficult to understand statically; boring and efficient at runtime.

Layers of work factor:

1. Malbolge-to-runtime language distance;
2. private CMG not shipped;
3. generated physical class graph;
4. R8 inlining/merging/repackaging/renaming;
5. semantic IDs omitted/remapped where not ABI;
6. compact/encoded cold binding descriptors;
7. constant sharding;
8. release-specific deterministic layout;
9. no shipped BindingSpec/evidence/history;
10. no shipped provenance/R8 owner map.

Baseline does not perform arbitrary post-R8 control-flow rewriting because it risks
undoing optimizer/layout work.

No runtime hostile anti-analysis is permitted.

Obfuscation is explicitly not cryptographic secrecy.

---

## 29. Security/privacy boundaries

Trust boundaries:

```text
Malbolge frontend
CMG parser/validator
binding laboratory
backend/R8
generated APK
Vector
YouTube target
libxposed service
manager UI
```

Security/privacy baseline:

- minimum Xposed scope;
- no architecture-required network;
- no telemetry;
- no account/token/cookie collection;
- no remote executable code;
- no secret whose security depends on APK opacity;
- no project background target service;
- no analysis-environment sabotage.

YouTube host inputs are untrusted and validated at semantic adapter boundaries.

---

## 30. Testing/validation architecture

Required categories:

1. Malbolge conformance/determinism/resource limits.
2. CMG schema/negative/mutation/fuzz tests.
3. binding uniqueness/ambiguity/decoy tests.
4. generated classfile/D8/R8/ART verifier tests.
5. module metadata/scope/entry tests.
6. exact target/process fail-closed tests.
7. hook install idempotency/duplicate tests.
8. ConfigSnapshot concurrency/invalid-update tests.
9. API-102 same/add/remove/failure/mismatch reload tests.
10. ReloadEnvelope/classloader-GC tests.
11. debug vs hardened semantic equivalence.
12. leakage scan.
13. reproducible clean build.
14. low-end performance p50/p95/p99 + retained heap.

Full acceptance rules are in `contracts/testing-validation.md`.

---

## 31. Repository architecture

```text
architecture/
  authority-map.yml

source/
  README + ownership directories
  # no executable Malbolge before freeze

contracts/
  semantic/runtime/framework/testing/hardening contracts

schemas/
  CMG/LMP/Endpoint/Binding/Config/Reload/Packaging schemas

target/current/
  one authoritative exact target lock

testdata/
  frontend/semantic/binding/callback/config/reload/performance vectors

adr/
  long-lived decisions

docs/
  researched upstream baseline

toolchain/
  LOCKFILE + frontend/toolchain contract

ci/
  architecture gates + derived build policy

generated/   # ignored/disposable
private/     # ignored/private provenance/evidence
```

No v3 runtime-discovery, native-control-plane or release-plan directories exist.

---

## 32. Architecture invariants

Seventy audited invariants are canonical in
`contracts/architecture-invariants.md`.

Core invariants include:

```text
Feature !→ Vector
Feature !→ DexKit
Feature !→ physical YouTube symbols
HOT !→ disk/network/Binder/discovery/Malbolge/VM/member lookup
target ambiguity → block/fail closed
one Endpoint → one BindingSpec
one physical hook → Hook Controller
normal feature toggle → immutable ConfigSnapshot
reload identity → stable HookAbiId
unsafe multi-hook reload → restart
release hardening !→ semantic change
release !→ private provenance/evidence/history
baseline runtime !→ DexKit/native-control-plane/custom-VM
```

---

## 33. ADR list

Accepted baseline ADR topics:

```text
ADR-001 Malbolge executable authorship
ADR-002 exact Malbolge profile
ADR-003 Vector/API-102 only
ADR-004 definition of hot loading
ADR-005 latest-exact YouTube policy
ADR-006 build-time discovery
ADR-007 no fallback resolvers
ADR-008 CMG
ADR-009 sparse capabilities
ADR-010 Semantic Endpoint ABI
ADR-011 deterministic composition
ADR-012 stable Hook ABI
ADR-013 immutable ConfigSnapshot
ADR-014 DEX-only project runtime
ADR-015 no project native control plane
ADR-016 no startup plan VM
ADR-017 representation-only hardening
ADR-018 private provenance
ADR-019 performance classes
ADR-020 diagnostics/privacy
ADR-021 manager config/control only
ADR-022 toolchain trust
ADR-023 target/binding epoch restart
ADR-024 obfuscation is not secrecy
```

The audited package additionally freezes RemotePreferences, classfile→R8/D8 backend,
exact packaging metadata and no-post-R8 baseline through contracts/invariants; these can
be promoted into separate ADR files when implementation begins if governance prefers
one decision per file.

---

## 34. Risks and unresolved research questions

Genuine prototype questions:

- exact Vector API-102 callback ordering/failure behavior;
- multi-hook partial replacement behavior;
- classloader collection after reload;
- RemotePreferences notification/reconnection behavior;
- safe ReloadEnvelope carrier types;
- exact YouTube process topology for first feature set;
- exact reference stable YouTube artifact set;
- binding uniqueness on that artifact;
- measured low-end budgets;
- pinned AGP/R8 keep/resource adaptation behavior.

These are not permission to add fallbacks. Failed validation remains fail closed.

---

## 35. Development phases

### Phase 0 — architecture characterization

No product feature.

Validate Vector/API-102 lifecycle, RemotePreferences, reload semantics, frontend profile
and backend packaging.

### Phase 1 — compiler substrate

Implement deterministic frontend, CMG linker/serialization, independent validator,
semantic diff, classfile backend and provenance.

### Phase 2 — binding laboratory

Select exact current stable YouTube artifacts, author first BindingSpec, prove uniqueness,
emit Verified Binding Set.

### Phase 3 — runtime kernel

Exact target/process gate, Config Publisher, Binding Materializer, Hook Controller,
Endpoint adapter, diagnostics.

### Phase 4 — harmless vertical slice

One low-risk Endpoint/Feature proves end-to-end dependency direction.

### Phase 5 — hot reload hardening

Same/add/remove hook generations, listener/state handoff, classloader-GC, failure modes.

### Phase 6 — performance hardening

Freeze budgets on low-end reference hardware.

### Phase 7 — release hardening

R8, layout, leakage, equivalence, provenance/reproducibility.

### Phase 8 — product features

Only now grow the actual feature set.

---

## 36. Architecture-freeze criteria

Before serious feature implementation the following must be objectively frozen:

```text
Vector/API-102 boundary
module metadata/scope/entry contract
hot-reload behavior and failure boundaries
RemotePreferences configuration transport
Malbolge evaluator/profile/limits
CMG schema + deterministic serialization
independent validator
LMP
classfile→R8/D8 backend
Semantic Endpoint ABI
capability admission policy
effect composition
state ownership
Hook Controller topology
threading rules
target-selection policy
exact current target
BindingSpec/uniqueness model
failure/circuit behavior
diagnostic/privacy model
release-hardening boundary
testing/performance method
private provenance/reproducibility
repository authority map
```

No production implementation is required to freeze architecture, but the small technology
spikes required to verify external API behavior must pass.

---

## 37. Final recommended architecture diagram

```text
                         PROJECT-OWNED SEMANTICS

 Malbolge source
      ↓
 deterministic frontend
      ↓
 Canonical Module Graph
      ↓
 independent semantic validator
      ↓
 Logical Module Plan
      ↑
      └──────────── Verified Target Binding Set
                          ↑
                    Binding Laboratory
                          ↑
                 DexKit + exact YouTube artifacts

                              ↓

                        RELEASE BOUNDARY

 generated JVM classfiles
      ↓
 R8/D8 + representation-only Release Layout Plan
      ↓
 generated API-102 module metadata
      ↓
 module APK
      │
      ├──────── private provenance/R8 maps → owner archive only
      ↓

                             RUNTIME

 Vector v2.2 / API 102
      ↓
 one generated entry
      ↓
 exact package/process/target gate
      ↓
 RemotePreferences → immutable ConfigSnapshot
      ↓
 Verified Target Binding materialization
      ↓
 Hook Controller
      ↓
 one physical hook per target/phase
      ↓
 typed Semantic Endpoint pipeline
      ↓
 sparse Capability / Feature Effects
      ↓
 original YouTube behavior unless explicit semantic mutation
```

---

## 38. Final evaluation

### v3 score

**6.5 / 10 architecturally**, independent of whether any implementation happened to
work.

### Strongest v3 decisions

- Malbolge build-time only;
- semantic separation from obfuscated members;
- fail-closed ambiguity;
- immutable config snapshots;
- strict HOT-path discipline;
- shared physical hooks;
- private provenance/reproducibility;
- explicit ban on destabilizing anti-analysis.

### Largest v3 weaknesses

- complexity was sometimes a goal rather than a consequence of a real requirement;
- native control plane + plan VM + multi-representation runtime duplicated ART/Vector;
- runtime DexKit/cache/prewarming contradicted latest-only release targeting;
- resolver fallback strategies conflicted with one canonical path;
- destructive Release IR was too close to semantic behavior;
- lifecycle/hot reload was not designed around API 102;
- authoritative data was duplicated across registries/caches/plans.

### Complexity removed

```text
runtime DexKit
runtime resolution cache
manager prewarm
compatibility daemon
native control plane
custom startup VM
multi-executable backend
fallback resolvers
behavioral destructive Release IR
post-R8 custom control-flow rewriting baseline
```

### Missing foundations added

```text
Vector/API-102 packaging boundary
stable HookAbiId
classloader-neutral reload envelope
RemotePreferences as one config truth
exact target-selection policy
one BindingSpec + uniqueness proof
deterministic CMG serialization
independent validator
one classfile→R8/D8 backend
state ownership map
complete testing architecture
authority map
hard freeze gates
R8 performance/hardening boundary
```

### Why v4 is stronger

v4 has fewer runtime mechanisms, fewer sources of truth and fewer failure modes while
making semantic contracts more explicit.

It also moves YouTube-update complexity to release engineering, where latest-only support
makes it easiest to validate and where expensive discovery does not affect the device.

Release opacity remains strong through Malbolge distance, private CMG, generated
structure, R8 and private provenance without making hot callbacks complex.

### Remaining risks

The unusual source language makes compiler correctness a first-order risk. YouTube
updates can invalidate bindings rapidly. API-102 hot reload has per-hook rather than
assumed whole-feature atomicity. Reverse-engineering resistance can never provide
cryptographic secrecy.

### Questions that genuinely require prototyping

- exact Vector/API-102 failure/callback lifecycle on the chosen device;
- RemotePreferences listener/reconnection behavior;
- old-classloader collection;
- exact reference YouTube artifact/process topology;
- first Endpoint binding uniqueness;
- measured low-end runtime budgets;
- final AGP/R8 keep/resource adaptation under the pinned toolchain.

Those are the intended Phase-0/Phase-2 experiments. None justifies redesigning the core
architecture if the contracts above are respected.

# Lifecycle and Hot-Load Contract

## Process state machine

This contract is the single authority for all runtime state machines. Derived documents
reference it and must not restate them.

```text
NEW
→ SCOPE_CHECK            ─ package/process not in scope ─→ OUT_OF_SCOPE
→ TARGET_CHECK           ─ identity ≠ Target Release Manifest ─→ UNSUPPORTED_TARGET
→ CONFIG_READY
→ BINDINGS_MATERIALIZING ─ independent endpoint failure ─→ DEGRADED
→ HOOKING                ─ independent endpoint failure ─→ DEGRADED
→ READY
→ HOT_RELOAD_PREPARE     ─ reload ineligible ─→ RELOAD_REJECTED (old generation stays READY)
→ RELOADING
→ READY | RESTART_REQUIRED | FAIL_CLOSED
```

`OUT_OF_SCOPE`, `UNSUPPORTED_TARGET` and `FAIL_CLOSED` install zero behavioral hooks.

`DEGRADED` is allowed only when failures are independent. An identity-level invariant
failure is `FAIL_CLOSED`, never `DEGRADED`.

Only `Process Coordinator` owns transitions.

## Endpoint state

```text
DECLARED
→ MATERIALIZED
→ VALIDATED
→ INSTALLED
→ ACTIVE | CIRCUIT_OPEN
```

Failure terminals:

```text
UNAVAILABLE
MATERIALIZATION_FAILED
VALIDATION_FAILED
INSTALL_FAILED
```

No unbounded retry loops.

## Feature state

```text
UNAVAILABLE
OFF
ON
RESTART_REQUIRED
```

`UNAVAILABLE` derives from missing required endpoints/capabilities. `OFF`/`ON` derive from
the published ConfigSnapshot. Features never mutate their own lifecycle state.

## Configuration state

```text
NO_SNAPSHOT
→ SAFE_DEFAULT | VALIDATED_PERSISTED
→ PUBLISHED(generation N)
→ VALIDATING_UPDATE
→ PUBLISHED(N+1) | REJECTED_KEEP_N
```

## Reload state

```text
IDLE
→ PREPARING
→ ENVELOPE_READY
→ REPLACING
→ ACTIVE_NEW | RESTART_REQUIRED | FAIL_CLOSED
```

No subsystem owns a generic retry loop. Recovery is explicit lifecycle behavior.

## What is actually hot-loadable

| Change | Normal action |
|---|---|
| ordinary setting | validate + atomically publish ConfigSnapshot |
| feature OFF | snapshot change; keep hook topology |
| feature ON, hooks already present | snapshot change |
| feature ON, runtime-installable missing hook | idempotent install, then publish ON |
| feature ON after an early-only hook point was missed | `RESTART_REQUIRED` |
| target binding epoch change | restart required |
| exact YouTube target changes | restart/relaunch required |
| one API-102 hook implementation update | stable-ID replacement when validated |
| multiple-hook update | hot only if mixed generations are explicitly safe |
| framework/Vector update | outside module hot-load guarantee |

## API-102 code reload protocol

Old generation:

1. enters `HOT_RELOAD_PREPARE`;
2. stops accepting architecture-owned mutable work;
3. unregisters configuration/listener resources;
4. emits a **classloader-neutral ReloadEnvelope** only;
5. exposes old hook handles by stable HookAbiId.

New generation:

1. validates target identity is still compatible with its Target Release Manifest;
2. if incompatible, removes/retires old behavioral hooks and fails closed;
3. rebuilds immutable target/config state;
4. matches physical hooks by HookAbiId;
5. same ID → request framework atomic replacement;
6. new ID → install once;
7. removed ID → unhook old;
8. transfer only declared safe state;
9. enter READY only after validation.

Vector v2.2 provides no reload transaction (`phase0/HOT_RELOAD_RESULTS.md`, HR-07): an
exception escaping `onHotReloaded` is reported as `FAILED` but leaves already-installed
new hooks and unreplaced old hooks running together. Therefore the new generation MUST
NOT throw out of reconciliation. On any reconciliation failure it unhooks every
behavioral handle it can reach (old and new), enters `FAIL_CLOSED`, and reports
`RESTART_REQUIRED`. A later successful reload receives all installed handles from every
prior generation and may reconcile from them.

Normal package callbacks are not replayed after hot reload (HR-01); reload correctness
never depends on them (ADR-035).

## Atomicity limit

API-102 individual hook replacement is treated as atomic per handle/identity.

The architecture does **not** assume transaction-level atomicity across a group of
different hooks.

A feature whose correctness cannot tolerate a transient mixed generation is
`RESTART_REQUIRED`.

## ReloadEnvelope constraints

Allowed:

- primitives;
- immutable strings;
- framework/boot-classloader-safe value containers;
- host-owned object references only where explicitly validated;
- opaque numeric IDs.

Forbidden:

- old-generation project classes;
- old-generation lambdas/callbacks;
- Hook Controller internals;
- Thread/Executor objects;
- arbitrary caches that retain old classloader graphs.

Classloader collection is a required validation test.

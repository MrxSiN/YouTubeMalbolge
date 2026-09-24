# Hook Topology Contract

## Ownership

`Hook Controller` is the sole owner allowed to install, replace, or remove a project
hook.

One physical target executable/phase has at most one project physical hook.

Multiple features sharing an Endpoint compose inside the generated semantic pipeline,
not by registering competing framework hooks.

## Priority

Vector/libxposed hook priority is not a feature-composition mechanism.

Use framework default priority unless an Endpoint has a documented inter-module
interaction requirement. Internal feature order is always defined by Effect semantics.

## Endpoint availability phase

Every Endpoint declares one:

```text
PACKAGE_LOADED
PACKAGE_READY
LATE_SIGNAL
```

`LATE_SIGNAL` requires an already-bound semantic signal that tells the Hook Controller
when materialization becomes valid.

Forbidden late-resolution shortcuts:

- polling ClassLoader state;
- broad ClassLoader hooks;
- periodic reflection scans;
- DexKit in target process;
- “try repeatedly until it works”.

If an early-only Endpoint was missed, dependent behavior becomes `RESTART_REQUIRED`.

## Idempotency key

Hook Controller tracks:

```text
HookAbiId
target Executable identity
generation
install state
```

A repeated installation request for the same generation and identity is a no-op or a
validated replacement, never a duplicate registration.

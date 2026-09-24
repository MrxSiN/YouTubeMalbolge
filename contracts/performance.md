# Performance Architecture

## HOT

MUST NOT perform:

- disk/network I/O;
- DexKit/discovery;
- Malbolge interpretation;
- project VM dispatch;
- reflection member lookup;
- normal logging;
- project JNI in baseline v4;
- unbounded locking.

SHOULD:

- allocate no module-owned objects;
- read immutable/precomputed state;
- execute direct generated endpoint code.

## WARM

Bounded UI/lifecycle/user-action work; no discovery.

## COLD

Bootstrap, config validation, member materialization, diagnostics export, manager work,
and build-time binding analysis.

No project target-process thread pool exists by default.

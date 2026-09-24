# Performance Baseline Plan

Budgets are deliberately UNFROZEN until hardware measurement.

Freeze one low-end rooted reference device, disable all other YouTube-scoped modules,
and record:

```text
Vector/API-102 empty module startup delta
exact target/config/binding bootstrap delta
pass-through hook p50/p95/p99
minimal transform hook p50/p95/p99
HOT module-owned allocations/invocation
retained heap after init
ConfigSnapshot publish cost
reload latency
old-classloader retained heap after reload
```

Runtime discovery budget is exactly zero because runtime DexKit does not exist.

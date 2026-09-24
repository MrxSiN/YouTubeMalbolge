# Testing and Validation Architecture

Testing validates architecture boundaries before product behavior.

## 1. Malbolge frontend

- reference evaluator conformance corpus;
- deterministic replay across two clean hosts;
- malformed source;
- step/output/graph limit exhaustion;
- invalid frame and digest;
- import/export collision;
- forbidden environment influence test.

## 2. CMG / semantic validator

- positive schema vectors;
- missing owner;
- dependency cycle;
- illegal Feature→Vector/DexKit dependency;
- duplicate semantic ID;
- undeclared mutating conflict;
- capability alias rejection;
- invalid HOT operation;
- invalid release-hardening semantic mutation.

Validator mutation/fuzz tests are mandatory because the frontend is a trust boundary.

## 3. Target binding laboratory

For each Endpoint:

- one true unique candidate;
- zero candidate;
- two candidates that both satisfy evidence → fail ambiguous;
- decoy with strong positive but failing hard validator;
- descriptor/staticness/call-relationship drift;
- exact target manifest mismatch.

No “top scoring candidate wins” acceptance rule is sufficient by itself.

## 4. Generated Android artifact

- classfile verification;
- D8/R8 success;
- ART load/verifier smoke test;
- generated `module.prop` exact values;
- exactly one `java_init.list` entry;
- scope contains only YouTube;
- no native init entry;
- R8 rewrites entry resource correctly;
- no API implementation accidentally packaged.

## 5. Runtime lifecycle

- non-target package → zero behavioral hooks;
- unknown process → zero behavioral hooks;
- unbound target → zero behavioral hooks;
- repeated initialization → no duplicate hooks;
- endpoint failure isolates only dependents;
- circuit breaker preserves original host path.

## 6. Configuration/concurrency

- safe default startup;
- valid update atomically visible;
- invalid update does not replace last-good snapshot;
- concurrent HOT readers never observe partial snapshot;
- no Binder/disk read in HOT callback;
- listener is single-owner and unregisters on reload.

## 7. Hot reload/API-102

Required device tests:

- same hook ID replacement;
- hook addition;
- hook removal;
- replacement failure;
- new-generation initialization failure;
- target-manifest mismatch during reload;
- feature state transfer;
- listener teardown/rebind;
- mixed-generation classification enforcement;
- repeated reload idempotency;
- old classloader becomes collectible;
- no duplicate physical hook after N reloads.

## 8. Performance

Measure on the frozen low-end reference device:

- module cache-free/normal startup contribution;
- pass-through physical hook p50/p95/p99;
- minimal semantic transform p50/p95/p99;
- allocations per HOT callback;
- retained module heap after initialization;
- config-snapshot publication cost;
- reload latency;
- classloader retained heap after reload.

Budgets are frozen from baseline measurements, not guessed in architecture prose.

## 9. Release equivalence

Debug semantic build and hardened release build run the same semantic vector suite.

Required:

```text
semantic_output(debug) == semantic_output(release)
```

for all architecture test vectors.

## 10. Leakage / reverse-engineering checks

Release scan rejects:

- private provenance;
- BindingSpec evidence/history;
- friendly internal feature/endpoint names not intentionally user-visible;
- compiler debug/source paths;
- unstripped private mapping files;
- accidental DexKit runtime dependency;
- native project runtime artifact;
- Malbolge source.

## 11. Reproducibility

Two clean builds with identical pinned inputs and layout seed must produce identical
unsigned canonical artifacts. Signing reproducibility is tracked separately according to
the signing tool/provider contract.

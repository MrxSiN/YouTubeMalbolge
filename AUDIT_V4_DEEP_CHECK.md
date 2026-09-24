# v4 Deep Audit Report — 2026-09-23

## Result

The first clean v4 package was architecturally directionally correct but incomplete
against the original research specification.

This audited revision closes the architectural-documentation gaps and removes fake
implementation scaffolding.

## High-severity findings fixed

1. **Missing exact Vector/API-102 module metadata contract.**
   Added one-entry, module.prop, scope, protective exception and hot-reload gating rules.

2. **Configuration transport was undecided.**
   Frozen to libxposed service RemotePreferences with immutable ConfigSnapshot
   publication and no fallback store.

3. **Hot reload was underspecified.**
   Added stable HookAbiId, classloader-neutral ReloadEnvelope, same/add/remove behavior,
   multi-hook atomicity limit, target-mismatch fail-closed and classloader-GC tests.

4. **Testing/validation architecture missing from the main design.**
   Added frontend, CMG, binding, ART, lifecycle, config, reload, performance, leakage,
   equivalence and reproducibility suites.

5. **Target “latest” policy was ambiguous.**
   Added exact Reference Target Artifact Set policy and stable-channel default.

6. **Compiler backend still allowed too much ambiguity.**
   Frozen one path: validated LMP → generated JVM classfiles → R8/D8 → DEX.

7. **Release hardening boundary incomplete.**
   Added explicit no-post-R8 semantic rewriter baseline and stable HookAbiId exception.

8. **DRY/source-of-truth problem.**
   Added `architecture/authority-map.yml` and removed duplicate version pins from
   `ci/build-config.yml`.

9. **Placeholder Malbolge source was misleading.**
   Removed all 28 architecture stubs. No executable project source now exists before
   freeze.

10. **Vector lifecycle callback mapping was not explicit.**
    Normal activation is now `onPackageReady`; early `onPackageLoaded` use is Endpoint-declared.

11. **Runtime support envelope was conflated with module support.**
    Added exact reference runtime environment lock and target-derived `minSdk`.

12. **Original required final evaluation was incomplete.**
    Added full v3 score/strength/weakness/removal/missing-foundation/v4 comparison.

## Medium-severity findings fixed

- explicit state ownership;
- hook priority/topology rules;
- late Endpoint lifecycle phase;
- Binder prohibition in HOT callbacks;
- multi-process reload non-atomicity;
- target code-bearing split identity;
- no weighted-score-only production binding acceptance;
- canonical deterministic CBOR CMG;
- independent serialized-CMG validator;
- Malbolge build-seed isolation;
- manager component/background-service policy;
- generated metadata schema;
- package leakage tests;
- exact freeze gates for enabling `autoHotReload`.

## Intentionally still open

These require empirical work, not more architecture prose:

- exact Vector v2.2 callback/failure behavior;
- RemotePreferences callback/reconnection details;
- reference Malbolge evaluator binary/hash/limits;
- exact stable YouTube reference artifact set;
- first real target bindings;
- low-end measured budgets;
- final AGP/R8 toolchain versions.

The project remains correctly `UNBOUND` and production-disabled until those gates pass.

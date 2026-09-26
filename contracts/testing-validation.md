# Testing and Validation

Required local gates:

1. evaluate every raw `.mal` with the pinned evaluator under step/output bounds;
2. verify MBX2 length/digest and MBP1 types, control flow, stack depth, and references;
3. validate graph ownership and one executable program per hooked endpoint;
4. run purity search against conventional sources;
5. run resolver vectors for rename, drift, decoys, ambiguity, hierarchy, and cache state;
6. deterministically regenerate review, provenance, and pre-shrinker classes;
7. build binding-lab debug plus module debug and release through D8/R8;
8. inspect generated JAR/APKs for raw Malbolge, interpreters, obsolete handlers, and
   unexpected runtime classes.

Required device gates:

- YouTube startup/crash/ANR and group fail-open behavior;
- feed, video ads, Shorts, Premium, settings, and SponsorBlock behavior;
- RemotePreferences persistence and single-listener hot reload;
- cold DexKit resolution, warm cache hit, and target-update invalidation;
- startup/resolver/hot-hook timing on representative hardware.

Device results are never inferred from a successful desktop build.

# Risks and Open Questions

These are genuine prototype/release-engineering questions, not reasons to leave the
architecture undefined.

## Must be experimentally resolved

1. Repeat the hot-reload probes on the low-end reference device (characterization
   device results: `phase0/HOT_RELOAD_RESULTS.md`).
2. Automatic hot reload on module update (`autoHotReload=true`) behavior.
3. RemotePreferences service death/reconnection behavior under Vector.
4. The generated (not probe) classloader-neutral ReloadEnvelope value types.
5. YouTube process names actually required by the first supported feature set.
6. Which semantic target bindings remain uniquely detectable on the selected 21.37.42
   artifact set.
7. Low-end performance budgets after the minimal Vector pass-through baseline is measured.
8. R8 keep/resource-adaptation details for the generated entry under the pinned AGP/R8
   version.

## Engineering risks

- Malbolge compiler/toolchain bugs can silently corrupt semantics; independent CMG
  validation and semantic vectors are mandatory.
- A latest-only target policy reduces runtime complexity but increases release-engineering
  cadence when YouTube changes.
- API-102 per-hook replacement does not imply whole-feature transactional replacement.
- Obfuscation can complicate owner diagnostics if private provenance is lost.
- Over-hardening can undo R8 performance/layout work; baseline forbids post-R8 rewriting.
- YouTube may move behavior to native/server/resource paths with no safe runtime semantic
  hook; such a feature must remain unavailable rather than broaden hooks recklessly.

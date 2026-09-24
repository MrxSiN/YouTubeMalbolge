# Phase-0 v4 Validation Status

Validation date: 2026-09-23

## Documentary characterization

PASS / VERIFIED:

- Vector stable v2.2 selected;
- libxposed API 102.0.0 selected;
- libxposed service 102.0.0 selected;
- hook ID / replacement / unhook interfaces documented;
- hot-reload state/old-handle interfaces documented;
- onPackageReady final-ClassLoader role documented;
- RemotePreferences two-access-surface/one-store model documented;
- module metadata contract documented;
- official R8 entry adaptation contract documented;
- DexKit 2.2.0 selected build-only;
- AGP 9.4.1 / Gradle 9.6.0 / JDK 17 / API 37 baseline pinned.
- MBX-CLASSIC-REF/1 semantic authority defined as Ben Olmstead 1998 interpreter.
- historical prose/interpreter mismatch rule defined: interpreter wins.
- deterministic project-unit input/environment restrictions defined.

## Development implementation audit

- exact target: BOUND 21.37.42 with three verified Endpoints
- production hooks and features: enabled by target lock for release 1.0.0
- executable project `.mal`: eight units, thirteen typed records
- generated runtime classfiles: eight, with one Xposed entry
- handwritten Java/Python: build toolchain and Binding Laboratory only
- runtime DexKit: ABSENT
- runtime resolver cache: ABSENT
- native control plane: ABSENT
- custom startup VM: ABSENT
- post-R8 semantic rewriter: ABSENT
- compatibility daemon/prewarmer: ABSENT

## Empirical Gate-A tests

Device evidence: `phase0/HOT_RELOAD_RESULTS.md` (characterization device, ADR-037 probe).


- Vector reload callback sequence: PASS (no lifecycle replay)
- hook replace/add/remove under Vector: PASS
- partial multi-hook failure: CHARACTERIZED (no framework rollback)
- RemotePreferences listener thread: Binder; reconnect: NOT RUN
- classloader collection after reload: PASS (24/24)
- exact Vector v2.2 API/service submodule SHAs: VERIFIED against release Git tree
- low-end reference environment: UNBOUND
- Malbolge semantic profile: DEFINED
- Malbolge raw reference source SHA-256: PINNED in `toolchain/LOCKFILE`
- Malbolge evaluator artifact/hash: pinned in `toolchain/LOCKFILE`
- Malbolge development tests: six passing
- exact YouTube target: BOUND 21.37.42
- real target bindings: Premium offer and two video-ad methods
- on-device generated three-hook installation: PASS, Vector v2.2/API 102, 2026-09-23
- on-device offer rendering and hiding: NOT OBSERVED

Visual hiding, ad playback behavior, channel whitelist semantics, the broader
Morphe Hide Ads functionality, and production compatibility remain open.

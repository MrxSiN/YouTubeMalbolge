# Architecture invariants

1. Raw Malbolge is the behavioral authority. Feature units emit validated MBP1 generic operations, never JSON records or backend function names.
2. MBP1 is compiled ahead of time to JVM bytecode. Android contains no Malbolge interpreter and no per-callback plan VM.
3. Conventional code may implement only generic ABI primitives, platform transport, UI rendering, networking, scheduling, storage, DexKit access, and libxposed integration.
4. Feature decisions, constants, filter patterns, resolver weights, and group policy originate in Malbolge output.
5. Adding a normal hook program requires no Java/Python dispatch registration.
6. HOT callbacks perform no DexKit work, disk/network I/O, member lookup, or Malbolge interpretation.
7. Resolution runs during the declared startup phase. Warm cache hits validate descriptors without opening DexKit.
8. A cache miss uses one bounded DexKit bridge, then closes it before hooks execute.
9. Cache identity covers the installed base/splits metadata, resolver schema, and complete Malbolge authority digest.
10. Ambiguous, missing, or invalid candidates fail open. A feature group installs atomically; partial install rolls back.
11. Checked-in `target/current` descriptors are fallback fixtures and regression evidence, not a production compatibility gate.
12. The module remains scoped to `com.google.android.youtube`, uses modern libxposed API 102, and ships no remote executable/config path.
13. Config snapshots are immutable on hot paths; RemotePreferences remains persistent config truth.
14. Diagnostics exclude account, auth, session, and user-content data.
15. Generated output carries source/program hashes and has a deterministic review representation.

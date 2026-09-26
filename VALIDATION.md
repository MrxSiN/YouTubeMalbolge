# Validation status

Statically verified in-repository:

- all feature Malbolge evaluates to non-JSON MBX2/MBP1 programs;
- MBP1 hashes, operands, constants, member references, and terminal returns validate;
- the purity gate rejects legacy dispatch and conventional-code feature policy;
- generated JVM bytecode passes D8/R8 builds;
- DexKit 2.3.0, libxposed 102, ASM, evaluator, and generator provenance are pinned;
- descriptor cache identity covers base/splits metadata plus the complete Malbolge authority digest;
- cache miss shares and closes one DexKit bridge; resolver weights and opcode-count evidence originate in raw Malbolge;
- missing or ambiguous resolution fails open; installation rollback is atomic within each Malbolge-defined group.
- generated API-102 callbacks transfer only target context, detach listeners, remove old handles, and rebuild one clean generation.

Device-smoke verified on 2026-09-26 with Pixel 8 Pro, Android 17, Vector 2.2/API 102,
and YouTube 21.38.130:

- final debug APK installed and Vector loaded the generated entry;
- all hook groups reported `ready` with no startup crash/ANR;
- cold app launch completed in 909 ms and subsequent launch in 734 ms;
- the warm launch reused the descriptor cache without rewriting it;
- generated manager Activity started without a process exception.
- installing version 1.1.0 over 1.0.0 hot-reloaded the generated entry; Vector logged
  `Hot reloaded`, hooks returned to `ready`, and the YouTube PID stayed `21741`.
- resolver cache v3 was created beside the old v2 cache, proving schema migration without destructive deletion.

Still requires interactive/device validation: actual ad surfaces, feed and Shorts behavior,
SponsorBlock request/parse/seek behavior, switch persistence, UI accessibility, forced
DexKit fallback against a changed compatible YouTube build, and update invalidation.

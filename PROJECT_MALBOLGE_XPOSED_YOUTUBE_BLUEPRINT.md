# YouTubeMalbolge architecture

Raw `.mal` programs emit the binary MBP1 instruction format. The build validates control flow, constants, member references, failure policy, startup class, and provenance, then specializes each program into JVM bytecode. No Malbolge interpreter or generic program VM ships in the APK.

The host ABI supplies only generic operations: hook context, member access/call, strings/control flow, view mutation, immutable config reads, UI model application, range-index networking/storage, logging, DexKit resolution, and libxposed installation. Feature patterns, policies, categories, labels, endpoint choices, and resolver weights remain Malbolge-owned.

Runtime resolution uses installed base/split identity plus the full Malbolge authority digest. Valid cache entries are reflected and shape-checked without DexKit. Cache misses share one DexKit 2.3 bridge, use narrow queries followed by bounded structural fallback, require a unique score winner, persist descriptors, and close the bridge before callbacks. Missing or ambiguous groups install nothing.

The checked-in YouTube target is a differential-test fixture and cheap fallback—not an allowlist. Compatibility with unknown future releases is attempted safely, never promised.

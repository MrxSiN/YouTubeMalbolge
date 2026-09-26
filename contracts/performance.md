# Performance contract

- Warm launch: validate cached descriptors; no DexKit bridge, scan, network, or APK hashing.
- Cold launch after identity change: one bounded DexKit bridge, narrow queries before structural fallback, persist results, then close the shared bridge at the end of installation.
- Hot callbacks: direct generated JVM code only; no DexKit, reflection lookup, disk/network I/O, Malbolge interpretation, or generic runtime VM dispatch.
- The data-driven range fetch remains bounded background HTTPS work; progress checks are primitive, allocation-free table scans generated from Malbolge.

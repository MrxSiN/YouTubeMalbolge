# SponsorBlock migration status

Authoritative behavior is split across executable MBP1 programs generated from raw Malbolge:

- `sponsorblock_capture.mal` stores the resolved seek receiver through generic state-slot operations.
- `sponsorblock_stage.mal` reads and validates the video key, owns invalidation, clears stale data, and schedules a bounded load.
- `sponsorblock_progress.mal` owns range matching, enabled-category checks, the 250 ms end margin, duplicate protection, seek choice, and logging.
- `config_policy.mal` owns category defaults.

The conventional bridge provides generic SHA-256, HTTPS, bounded byte loading, data-driven JSON projection into a validated immutable `long[]`, volatile state slots, and reflected member invocation. Origin, path, hash-prefix length, timeouts, response limit, accepted status, JSON keys, action value, scale, cache/retry policy, categories, and preference mapping are Malbolge constants.

The progress program AOT-compiles to primitive array/long/int JVM instructions. Its loop allocates nothing, performs no reflection lookup, I/O, JSON parsing, DexKit work, or Malbolge interpretation, and calls the seek bridge only after its Malbolge-derived predicates succeed.

Malformed/non-finite/negative/reversed ranges are rejected. Oversized, non-successful, stale, or malformed responses fail open.

## Device evidence

On 2026-09-26 the migrated debug APK loaded all generated hook groups on Pixel 8 Pro /
Android 17 / Vector 2.2 / YouTube 21.38.130, reported `ready`, reused its warm descriptor
cache, and opened the manager without a process exception. That is startup/injection
evidence only. SponsorBlock network, response projection, and live seek behavior still
require an interactive playback test.

Voting, segment creation, seekbar drawing, toasts, skip counts, custom API URLs, channel whitelists, and cast seeking remain outside the repository's feature set.

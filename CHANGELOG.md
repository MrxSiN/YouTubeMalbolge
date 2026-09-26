# Changelog

## Unreleased

### Removed

- Unused build and test scaffolding: the `-PdeviceTest` Gradle flag, the always-true
  `active` plan flag, the unused `CONFIG` MBP1 opcode (`CONFIG_DYNAMIC` covers it), the
  `MANIFEST.sha256` CI check, orphan `testdata/` vectors, unconsumed `ci/`,
  `architecture/` and `packaging/xposed/` files, and schemas for retired formats.
- `toolchain/bindinglab` is no longer included in the Gradle build.

### Changed

- Each Malbolge source unit is evaluated once per build instead of twice.

## 1.1.0

### Changed

- DexKit 2.3.0 now resolves cache misses in the injected process with obfuscation-safe
  shorty matching, bounded opcode drift, split/version cache invalidation, and unique-winner fail-open behavior.
- API-102 automatic hot reload now detaches the old listener and hook generation, then
  reconstructs configuration, bindings, and hooks without restarting YouTube.

## 1.0.0

The first release. Bound to exactly one YouTube build, `21.37.42` from Google Play:
thirty target Endpoints, no fallback resolvers. Tested on a Pixel 8 Pro with Vector
v2.2 and libxposed API 102. Production hooks and features are enabled, so the
signed release APK installs eleven hooks.

### Added

- Hide ads, a development port of part of Morphe's Hide Ads patch. Three switches:
  Hide sponsored banners, Hide video ads and Hide Premium promotions, all on by
  default.
- Sponsored Home feed cards are replaced with YouTube's own empty component. The
  sponsored feed Litho component method is bound with three helper Endpoints, and
  component identifiers are matched against Morphe's ad patterns. On device, the
  visible Home feed showed normal videos and no sponsored card.
- Ad items are kept out of the Shorts feed. The adapter's two item insertion methods
  and its ad predicate are bound, and an ad item is discarded before the adapter adds
  it. Twelve Shorts pages after a blocked ad still had working Like and Comments
  controls.
- The compact YouTube Premium offer view, the sponsored attribution banner and the two
  video ad construction methods are bound. Their hooks install on device; the visual
  effect of each is not yet observed. See `docs/HIDE_ADS_PORT_STATUS.md`.
- SponsorBlock. Segments of enabled categories are skipped as soon as playback reaches
  them. Ten switches: Enable SponsorBlock and one per category. As in Morphe, only
  Skip sponsors is on by default. Four player Endpoints are bound: the player
  controller constructor, which yields the seek receiver; the video stage, read after
  the original for the video ID; playback progress; and the seek call itself.
- Segments are fetched by hash prefix from the SponsorBlock API, on a worker thread
  and never on the playback path (ADR-038). The skip itself runs on the main thread.
  A `sponsor` segment was skipped on device with no `VerifyError`, crash or seek
  failure. See `docs/SPONSORBLOCK_PORT_STATUS.md`.
- A "YouTube Malbolge" row above Account in YouTube's own Settings, drawn with
  YouTube's row layout and the module's hellfire play icon. It is added after
  YouTube's screen builder returns, because the builder clears the screen and
  inflates it again (ADR-039).
- The module's settings page: all thirteen switches in two sections, in YouTube's
  dark colours with a back bar. It edits RemotePreferences through libxposed service
  102, and a change survives a process restart. The page is also on the launcher, for
  when the YouTube Settings row cannot be hooked. See `docs/SETTINGS_STATUS.md`.
- Hook compatibility check. Open YouTube once after installing; until the check
  succeeds, feature switches stay grey. The module app shows compatibility and a hook
  report to copy.
- Atomic hook installation. If any binding or hook fails, the controller rolls back
  every hook and the module app disables every switch.

### Changed

- Version is now `1.0.0`.
- README rewritten: features, compatibility, requirements, install, how it works,
  build and design.
- New record kinds for the Malbolge units: `SegmentSource`, `SettingsPage`, Endpoint
  class `CALL`, constructor and FIELD bindings, and the original four callback adapters.
- The backend reads a tab-separated plan (`build/generated/plan.tsv`) and emits
  `SeekPort`, `SegmentStore`, `SegmentFetch` and the settings classes when a Feature
  needs them. `generateMalbolgeModule` tracks every backend source file.
- ConfigItems may use more than one RemotePreferences group.
- Production hooks and features enabled in `target/current/target-release.lock.yml`.
  The release build now installs the eleven hooks that were previously only in the
  device test build.
- Release builds are signed from `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_ALIAS`,
  `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_PASSWORD`. A GitHub Actions workflow
  verifies `MANIFEST.sha256`, builds, signs and attaches the APK to the release for
  every `v*` tag.
- R8 no longer fails on the hidden `android.app.ActivityThread` class the generated
  diagnostics refer to.

### Removed

- `AUDIT_V4_DEEP_CHECK.md`, `MIGRATION_V3_TO_V4.md` and `RISKS_AND_OPEN_QUESTIONS.md`:
  v3 to v4 history and Phase 0 questions.
- The Phase 0 hot-reload probe, `phase0/probe/`. Its results stay in
  `phase0/HOT_RELOAD_RESULTS.md`; its source is in git history.
- The empty `source/00_boot`, `source/10_vector` and `source/60_packaging` areas.
  Each is created with its first Malbolge unit.

## Pre-release history

Architecture and characterization work before the first feature. Nothing in this
period installed a product hook.

### v4 Phase 0 device characterization — 2026-09-23

- selected YouTube `21.37.42` (Play, stable) as the exact reference artifact set;
  target lock is `ARTIFACT_SELECTED` (hashed, no bindings, production still disabled);
- added non-product API-102 probe `phase0/probe/` (ADR-037);
- ran HR-01..HR-10 on the characterization device (`phase0/HOT_RELOAD_RESULTS.md`):
  no lifecycle replay, per-handle replace/add/remove, 20x reload without duplicate
  listener, old classloader collected, reload veto, and **no framework rollback on a
  failed reload**;
- lifecycle contract: a new generation must self-fail-closed instead of throwing from
  `onHotReloaded`;
- configuration contract: listener callbacks arrive on Binder threads;
- manager boundary: keep one process-lifetime `XposedService`.

### v4 Phase 0 consistency audit — 2026-09-23

- made `contracts/lifecycle-hot-reload.md` the single authority for all runtime state
  machines; restored `OUT_OF_SCOPE`, `UNSUPPORTED_TARGET` and `RELOAD_REJECTED` branches
  and the independent-failure-only `DEGRADED` rule; blueprint now references it;
- removed duplicated toolchain/production pins from `phase0/STATUS.yml`,
  `ci/build-config.yml` and `toolchain/README.md`; deleted orphan
  `phase0/toolchain-baseline.json` (`toolchain/LOCKFILE` is the only pin authority);
- marked ADR-025 superseded by ADR-033 and reordered the ADR index;
- aligned reload vectors with HR-02..HR-10 and made partial multi-hook failure
  decisively `restart_required` until HR-07 evidence exists;
- added `testdata/callback-vectors/` required by the testing architecture;
- recorded characterization-device evidence (`phase0/DEVICE_OBSERVATIONS.md`): Vector
  v2.2 `88f8e1fa` confirmed, Play YouTube 21.37.42 hashes, signer rotation lineage;
- restricted persisted RemotePreferences values to framework-neutral types after
  observing a Vector-side `BadParcelableException` crash on a Serializable value;
- required other YouTube-scoped modules disabled during HR probes and baselines.

### v4 Phase 0 — 2026-09-23

- defined MBX-CLASSIC-REF/1 against Ben Olmstead's 1998 reference interpreter;
- made interpreter behavior authoritative over conflicting historical prose;
- prohibited ambient Malbolge input and `/` in project semantic-generator units;
- fixed ASCII/C-locale deterministic source normalization;
- retained exact raw reference-source hash as a controlled-acquisition Gate-A item;

Technology characterization:

- verified Vector stable v2.2 (`88f8e1f`);
- verified libxposed API 102.0.0 (`45e7c5c`);
- verified libxposed service 102.0.0;
- documented HookBuilder ID, HookHandle replace/unhook and hot-reload state surfaces;
- documented RemotePreferences injected and manager access paths;
- froze RemotePreferences as the one persistent configuration store;
- froze `onPackageReady` as normal target-ready lifecycle;
- made hot-reload correctness independent of package-callback replay behavior;
- added HR-01 through HR-10 device probe protocol;
- verified DexKit stable 2.2.0 (`ffa6c51`);
- pinned current Android build baseline: AGP 9.4.1, Gradle 9.6.0, JDK 17,
  compileSdk/targetSdk 37, Build Tools 36.0.0;
- added exact official libxposed R8 entry/resource adaptation contract;
- kept `autoHotReload=false` until device acceptance;
- retained zero product source/features/hooks.

### v4 audited revision — 2026-09-23

- completed architecture-wide deep audit;
- removed placeholder executable source;
- added exact target/binding/reload/testing/hardening contracts.

### v4 initial architecture baseline — 2026-09-23

- historical v4 temporarily used release-time-only binding; ADR-040 supersedes that decision.

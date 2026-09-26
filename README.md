# YouTubeMalbolge

An API-102 libxposed module for YouTube ad hiding and SponsorBlock whose feature behavior is authored in real Malbolge.

## Architecture

Raw feature programs under `source/70_features` emit binary MBP1 operations. The build validates them and compiles them ahead of time to JVM bytecode. The APK contains no Malbolge interpreter and no feature-handler dispatch table.

The Java backend is a generic host: hook context, member calls, Android UI, HTTPS/background work, persistent descriptor cache, DexKit resolution, diagnostics, and libxposed installation. Ad patterns, SponsorBlock categories, settings behavior, hook choices, and resolver scoring weights come from Malbolge output.

YouTube bindings resolve at runtime. Valid cache hits avoid DexKit; a changed installed base/split identity triggers one bounded DexKit pass and a uniqueness check. Missing or ambiguous bindings fail open. `target/current` is a reference fixture and descriptor fallback, not an exact-version allowlist. Future-version compatibility is attempted, not guaranteed.

API-102 hot reload removes the old hook generation and listener, then rebuilds bindings through the same cached/DexKit resolver without restarting YouTube.

## Features

- Player, feed, Shorts, attribution, and Premium-promotion ad suppression.
- SponsorBlock HTTPS segment loading and enabled-category skipping.
- One injected YouTube settings entry and manager controls backed by RemotePreferences.
- UID-checked compatibility diagnostics.

## Build and verify

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
python toolchain/check_purity.py
python -m unittest discover -s toolchain/tests -v
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

The build uses pinned Malbolge evaluator/generator provenance, ASM 9.9.1, DexKit 2.3.0, and libxposed API/service 102. Device compatibility still requires an actual rooted Android/Vector integration run; repository tests do not simulate injection.

Licensing and upstream attribution are in `LICENSE`, `NOTICE`, and `LICENSES/`.

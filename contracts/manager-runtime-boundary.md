# Manager / Injected Runtime Boundary

## Manager process

Owns:

- settings UI;
- RemotePreferences edits through libxposed service 102;
- one process-lifetime `XposedService` reference (the service helper binds once per
  process);
- target/module status display;
- bounded diagnostic display/export;
- reload failure messaging;
- license/privacy information.

Baseline manager is user-driven. It has no compatibility daemon and no background
resolver.

## Injected YouTube process

Owns:

- package/process gate and cached runtime resolution;
- remote config listener;
- immutable ConfigSnapshot;
- cached binding validation and bounded DexKit discovery on cache miss;
- Hook Controller;
- Endpoint pipelines;
- feature state/effects;
- bounded diagnostics.

DexKit runs only during cold resolution and closes after installation. SponsorBlock
transport runs on a daemon worker after a video-ID change. Hot callbacks perform none of
that work.

## Multi-process rule

Each allowed YouTube process is an independent lifecycle unit. API-102 hot reload rebuilds
module code and bindings inside each active scoped process.

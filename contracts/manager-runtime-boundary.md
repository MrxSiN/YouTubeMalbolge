# Manager / Injected Runtime Boundary

## Manager process

Owns:

- settings UI;
- RemotePreferences edits through libxposed service 102;
- one process-lifetime `XposedService` reference (the service helper binds once per
  process);
- target/module status display;
- bounded diagnostic display/export;
- explicit hot-reload request/control when framework service supports it;
- restart-required messaging;
- license/privacy information.

Baseline manager is user-driven. It has no compatibility daemon and no background
resolver.

## Injected YouTube process

Owns:

- exact target/process gate;
- remote config listener;
- immutable ConfigSnapshot;
- binding materialization;
- Hook Controller;
- Endpoint pipelines;
- feature state/effects;
- bounded diagnostics.

It does not run DexKit, network services, compatibility scans, or persistent storage
polling.

## Multi-process rule

Each allowed YouTube process is an independent lifecycle/reload unit.

Do not assume a framework hot reload is transactionally atomic across multiple Android
processes. Features requiring cross-process consistency must define a restart boundary
or explicit process-independent semantics.

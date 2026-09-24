# Vector v2.2 / libxposed API-102 Boundary

## Architectural role

Vector is the **only Xposed framework target**. The project does not maintain an
LSPosed-specific, legacy Xposed, or alternate backend.

The adapter surface is intentionally narrow:

```text
ModuleLifecycle
HookInstall
HookReplace
HookRemove
HookIdentity
RemotePreferencesAccess
FrameworkLog
HotReloadLifecycle
```

Feature semantics cannot access these directly.

## Framework facts adopted by the architecture

Vector v2.2 stable integrates libxposed API 102 and documents in-process module
hot reload plus atomic replacement of individual hookers.

The project therefore uses API-102 hook IDs as a runtime ABI.

## Hook identity

Each physical hook receives a stable opaque `HookAbiId`.

Rules:

- stable across hot-reload-compatible generations;
- not derived from semantic names in the shipped artifact;
- not remapped by release diversification within a reload epoch;
- unique per physical executable + hook phase/contract;
- changed only when compatibility semantics require a restart epoch.

## Exception mode

Production hooks use `PROTECTIVE` framework exception mode.

Project callbacks still implement their own semantic failure behavior; protective mode
is the final target-process safety boundary.

## Lifecycle use

Initial load owns target/process/config/binding validation and hook installation.

Hot reload owns only generation replacement. It MUST NOT silently pretend that the
whole target lifecycle has restarted.

The exact API-102 callback ordering and old-handle visibility are verified by the
architecture spike before `autoHotReload=true` is enabled.

## No framework capability leakage

Feature/Capability/Endpoint logic may depend only on project semantic contracts.

Only the Vector adapter may reference libxposed API types or HookHandle objects.

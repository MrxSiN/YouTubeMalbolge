# Vector v2.2 / libxposed API-102 Boundary

## Architectural role

Vector is the **only Xposed framework target**. The project does not maintain an
LSPosed-specific, legacy Xposed, or alternate backend.

The adapter surface is intentionally narrow:

```text
ModuleLifecycle
HookInstall
HookRemove
HookIdentity
RemotePreferencesAccess
FrameworkLog
```

Feature semantics cannot access these directly.

## Framework facts adopted by the architecture

The project uses libxposed API 102 hook IDs as a runtime ABI.

## Hook identity

Each physical hook receives a stable opaque `HookAbiId`.

Rules:

- stable within one generated module version;
- not derived from semantic names in the shipped artifact;
- not remapped by release diversification within a reload epoch;
- unique per physical executable + hook phase/contract;
- changed only when compatibility semantics require a restart epoch.

## Exception mode

Production hooks use `PROTECTIVE` framework exception mode.

Project callbacks still implement their own semantic failure behavior; protective mode
is the final target-process safety boundary.

## Lifecycle use

`onPackageReady` owns package/config/resolver initialization and hook installation.
`onHotReloading` transfers only Android/JDK objects, detaches listeners, and removes the
old generation. `onHotReloaded` cleans framework-reported old handles and rebuilds from
the saved target context. No project class crosses the generation boundary.

## No framework capability leakage

Feature/Capability/Endpoint logic may depend only on project semantic contracts.

Only the Vector adapter may reference libxposed API types or HookHandle objects.

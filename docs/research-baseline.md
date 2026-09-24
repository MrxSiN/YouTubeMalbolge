# v4 Phase-0 Research Baseline — 2026-09-23

## Primary sources

### Vector

- JingMatrix/Vector repository:
  https://github.com/JingMatrix/Vector
- Vector releases:
  https://github.com/JingMatrix/Vector/releases

Verified:

```text
stable = v2.2
release commit = 88f8e1f
API 102 hot reload = supported by stable release
```

Vector states Android 8.1–Android 17 Beta framework compatibility and requires a recent
Magisk or KernelSU environment with Zygisk.

### libxposed API

- https://github.com/libxposed/api
- https://github.com/libxposed/api/releases

Verified:

```text
latest stable API = 102.0.0
release commit = 45e7c5c
```

Relevant documented surfaces:

```text
onModuleLoaded
onPackageLoaded
onPackageReady
onHotReloading
onHotReloaded
HookBuilder.setId
HookHandle.getId
HookHandle.replaceHook
HookHandle.unhook
getRemotePreferences
PROP_CAP_REMOTE
```

Official module configuration documentation also defines `module.prop`, `java_init.list`,
`scope.list`, `autoHotReload`, and R8 resource adaptation.

### libxposed service

- https://github.com/libxposed/service
- https://github.com/libxposed/example

Verified:

```text
service artifact = io.github.libxposed:service:102.0.0
purpose = module app ↔ Xposed framework communication
```

The example demonstrates manager-side RemotePreferences and explicit hot reload requests.

### DexKit

- https://github.com/LuckyPray/DexKit
- https://github.com/LuckyPray/DexKit/releases

Verified:

```text
latest stable = 2.2.0
release commit = ffa6c51
```

DexKit 2.2 improves matcher/query performance and concurrent host-side bridge access.

### Android build toolchain

- https://developer.android.com/reference/tools/gradle-api
- https://developer.android.com/build/releases/agp-9-4-0-release-notes

Verified current stable baseline:

```text
AGP 9.4.1
Gradle 9.6.0
JDK 17
compile API 37 supported
SDK Build Tools 36.0.0
```

## Evidence labels

Architecture documents distinguish:

```text
FACT
RESEARCH FINDING
RECOMMENDATION
OPEN QUESTION
```

Current device-dependent questions are explicitly listed in
`PHASE0_TECH_CHARACTERIZATION.md`.

# Android Build Toolchain Baseline

Current stable toolchain selected on 2026-09-23:

```text
Android Gradle Plugin 9.4.1
Gradle 9.6.0
JDK 17
compileSdk 37
targetSdk 37
SDK Build Tools 36.0.0
```

Reasoning:

- AGP 9.4 is current stable family;
- Android's AGP API reference lists 9.4.1 as current stable;
- AGP 9.4 supports API 37;
- AGP 9.4 compatibility requires/defaults to Gradle 9.6.0 and JDK 17.

The project has no authored Java/Kotlin implementation source. AGP is packaging/build
infrastructure around generated classfiles/resources.

## `minSdk`

Not frozen yet.

Final formula:

```text
minSdk =
max(
  27,  # Vector Android 8.1 floor
  exact supported YouTube target requirement,
  generated manager/runtime Android API requirement
)
```

The target's actual requirement wins.

## R8/D8 versioning

R8/D8 are taken from the pinned AGP toolchain and archived through build provenance
rather than independently upgraded.

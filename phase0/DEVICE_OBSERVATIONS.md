# Phase-0 Device Observations — 2026-09-23

Characterization device only. This is **not** the low-end reference runtime environment
(`testdata/environment/reference-runtime.lock.yml` stays `UNBOUND`) and **not** a target
binding (`target/current/target-release.lock.yml` is `ARTIFACT_SELECTED`, not `BOUND`).

## Environment

| Item | Observed |
|---|---|
| Device | Pixel 8 Pro (`husky`), Tensor G3, arm64-v8a, zygote64 |
| Android | 17 / SDK 37, `CP3A.260905.009`, security patch 2026-08-05, verified boot green |
| Root | KernelSU (`ksud 3.1.2`) |
| Zygisk | Zygisk Next 1.5.0 (843-5217106) |
| Vector | `zygisk_vector` v2.2 (3080-`88f8e1fa`) — matches `toolchain/LOCKFILE` commit |
| Vector processes | `vectord` daemon, `org.matrix.vector.manager` |

## Installed YouTube (Play-installed, stock, no overlay mount)

| Item | Observed |
|---|---|
| versionName / versionCode | `21.37.42` / `1561296049` |
| minSdk / targetSdk | 32 / 37 |
| installer | `com.android.vending` (updated system app over `/product/app/YouTube` 21.18.164) |
| splits | base, config.arm64_v8a, config.en, config.xxhdpi |
| code-bearing DEX | base.apk `classes.dex` … `classes8.dex` |
| signing cert SHA-256 (current) | `5AAD2BEE6DB95D17E05A08D7D1E64C10A1511879154483916B6AE6C7FD9CB0C6` |
| signing scheme | APK signature scheme v3 with key-rotation history (2 past signatures) |

APK SHA-256:

```text
39a955c0e7695efdd9d3fef1d609aa9c82c3aecf118f75da5ae8017102b9211e  base.apk
0acbb158d341034f6dbc9fd8e8eea075ad8be96b38b834acef2aa406781ac75a  split_config.arm64_v8a.apk
3aa9cc3a9bd9e39bcb247a1e5da8045f30a1feb7ddd1d38ac0b333bd988aa12f  split_config.en.apk
2f185efd08cd832d751733ca796366b80f844a1a788841d4240364c03cf2571c  split_config.xxhdpi.apk
```

## Findings with architectural consequence

1. **Play served 21.37.42, not the 21.37.47 research candidate.** Confirms target
   selection must bind exact acquired artifacts, never a public tracker version.
   `21.37.42` is now the selected reference artifact set (`ARTIFACT_SELECTED`).
2. **Signer has v3 rotation lineage.** Target identity must compare the current signer
   and accept rotation lineage explicitly in the Target Release Manifest, not a single
   legacy certificate digest.
3. **RemotePreferences non-neutral values crash the writer.** Another module's manager
   repeatedly died with `BadParcelableException: ... ClassNotFoundException reading a
   Serializable object (name = J0.o)` from `IXposedService.updateRemotePreferences` via
   `RemotePreferences$Editor.apply`. An R8-renamed Serializable cannot be decoded by the
   framework. Resulting rule: `contracts/configuration.md` § Persisted value types.
4. **A third-party YouTube module (`app.morphe.youtube`) was active in YouTube scope** (since uninstalled by the owner).
   HR probes and performance baselines must run with every other YouTube-scoped module
   disabled; coexistence is a separate, later compatibility question.
5. Vector logs `dispatchPackageReady` per process, consistent with `onPackageReady` as
   the target-ready phase (ADR-031). Callback order under hot reload remains unmeasured.

## Not yet run

HR-01..HR-10, RemotePreferences listener thread/reconnect, classloader collection.
These require the non-product probe module (Gate A); no probe APK exists yet.

# Phase 0 Evidence Matrix

| Item | Status | Evidence class | Architectural consequence |
|---|---|---|---|
| Vector stable release | Verified | primary project release | pin v2.2 / `88f8e1f` |
| API 102 support | Verified | Vector release + libxposed API | API-102-only architecture |
| Hook IDs | Verified | libxposed API docs | stable HookAbiId |
| Hook replacement | Verified | libxposed API docs | per-handle atomic replacement |
| Hot reload veto | Verified | libxposed API docs/example | old generation may reject reload |
| Saved reload state | Verified | libxposed API docs/example | classloader-neutral ReloadEnvelope |
| Old hook handles | Verified | libxposed API docs/example | deterministic generation reconciliation |
| `onPackageReady` final loader | Verified | libxposed API docs | default normal target-ready phase |
| RemotePreferences injected access | Verified | libxposed API docs | no custom runtime IPC |
| RemotePreferences manager access | Verified | libxposed service/example | one persistent config truth |
| Module metadata files | Verified | libxposed configuration docs | generated META-INF/xposed contract |
| R8 `java_init.list` adaptation | Verified | libxposed README/docs | backend must adapt resource after obfuscation |
| Vector callback replay behavior | Verified on device: not replayed | HR-01 probe | reload design cannot rely on replay |
| Vector multi-hook transactionality | Verified on device: none, no rollback | HR-07 probe | generation self-fails-closed; unsafe multi-hook feature is restart-only |
| DexKit latest stable | Verified | primary project release | pin 2.2.0 / `ffa6c51` |
| Current Android build baseline | Verified | Android Developers | AGP 9.4.1 / Gradle 9.6 / JDK 17 / API 37 |
| Exact latest YouTube bytes | Selected | `21.37.42` hashed in `target/current/target-release.lock.yml` | ARTIFACT_SELECTED; BOUND needs Binding Laboratory |
| Vector v2.2 installed build | Verified on device | `88f8e1fa` on characterization device | LOCKFILE commit confirmed |
| Vector v2.2 API/service submodule SHAs | Verified | Vector release Git tree at `88f8e1f`; `.gitmodules` maps `xposed/libxposed` to API and `services/libxposed` to service | exact commits pinned in `toolchain/LOCKFILE` |
| RemotePreferences non-neutral values | Verified failure on device | Vector log, third-party module | neutral persisted value types only |
| YouTube signer rotation | Verified on device | v3 signature with past signatures | manifest must model signer lineage |

| Malbolge semantic authority | Verified | original interpreter + technical literature | interpreter behavior defines MBX-CLASSIC-REF/1 |
| Malbolge input/output discrepancy | Verified | original interpreter | interpreter wins; `<` output, `/` input |
| Reference interpreter public-domain declaration | Verified | original source | may be vendored after controlled acquisition |
| Exact reference source SHA-256 | Verified | raw `malbolge.c` at pinned TryItOnline commit; local SHA-256 | vendored in `toolchain/reference/`; evaluator and conformance still pending |

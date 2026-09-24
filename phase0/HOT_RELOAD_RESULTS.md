# API-102 Hot Reload Probe Results — 2026-09-23

Environment: characterization device in `phase0/DEVICE_OBSERVATIONS.md` (Pixel 8 Pro,
Android 17, KernelSU + Zygisk Next, Vector v2.2 `88f8e1fa`). Not the low-end reference
device. Probe: `phase0/probe/` (ADR-037), removed after Phase 0;
its source and condensed logs remain in git history before release 1.0.0.

Reloads were requested explicitly through libxposed service
`XposedService.hotReloadModule` with module `autoHotReload=false`. Automatic reload on
module update was not exercised.

| Probe | Result | Evidence | Observation |
|---|---|---|---|
| HR-01 callback order | PASS | hr-g2 | old `onHotReloading` then new `onHotReloaded` on the same thread; `onModuleLoaded`/`onPackageLoaded`/`onPackageReady` are **not** replayed |
| HR-02 saved state | PASS | hr-g2..g7 | `HotReloadingParam.getExtras()` Bundle and `setSavedInstanceState(Object[]{Integer, WeakReference})` arrive intact |
| HR-03 hook identity | PASS | hr-g2 | old handles carry the stable `setId` value; `replaceHook` returns a **new** handle object with the same ID |
| HR-04 replace atomicity | PASS | all | 4 caller threads, 0 `INVALID` values; each call saw exactly the old or new generation |
| HR-05 add/remove | PASS | hr-g2, hr-g3 | add installs once; `unhook` of an old handle restores original immediately; no zombie hook |
| HR-06 replacement failure | PASS (characterized) | hr-g4 | exception from `onHotReloaded` → service result `FAILED` with the exception message; target state `FAILED`; process stays alive |
| HR-07 partial multi-hook | **FAIL-OPEN BY FRAMEWORK** | hr-g4 | no rollback: new-generation hooks already installed stay active **and** unreplaced old-generation hooks keep running (observed `h1=g4`, `h2=g3` indefinitely) |
| HR-07 recovery | PASS | hr-g5 | next reload after `FAILED` receives **all** currently installed handles from both generations and can repair |
| Reload veto | PASS | hr-g7 | old `onHotReloading` returning `false` → `refused the hot reload`, result `FAILED` (message null); old generation keeps running |
| HR-08 listener handoff | PASS | hr-08 | 20 consecutive reloads all `SUCCEEDED`; one pref write produced exactly one listener callback |
| HR-09 classloader collection | PASS | hr-g2..g6, hr-08 | old generation ClassLoader weakly reachable only; collected after the first GC check in 24/24 successful reloads; after the failed g4 reload the g3 loader was not collected within the capture window (its H2 hooker was still installed) |
| HR-10 target mismatch | PRIMITIVES ONLY | hr-g3 | reload-time `unhook` works; the Target Gate policy itself needs the runtime kernel |

## RemotePreferences (injected side)

| Item | Result |
|---|---|
| framework properties | `0x7` (remote capability present) |
| manager `commit()` | `true` |
| listener thread | Binder thread (`binder:<pid>_N`), not main thread |
| write → listener latency | 0–13 ms (4 samples) |
| unregister during reload | effective; no duplicate callbacks after 20 reloads |
| service death / reconnect | NOT RUN |

## Service-helper finding

`XposedServiceHelper` delivers a bound service **once per process**; a second
`registerListener` in the same process is never called back. A manager must hold the
service for the process lifetime.

## Architectural consequences

1. Hot-reload correctness must not depend on lifecycle replay (ADR-035 confirmed).
2. Vector provides no reload transaction. A new generation that cannot complete
   reconciliation MUST fail closed itself: catch the failure, unhook every behavioral
   handle it can reach (old and new), and report `RESTART_REQUIRED`. Throwing out of
   `onHotReloaded` leaves a live mixed generation.
3. `RELOAD_MIXED_SAFE` stays unadmitted by default; HR-07 proves mixed generations persist.
4. Config listener callbacks arrive on Binder threads; snapshot publication must be a
   thread-safe atomic reference swap and never block that thread.

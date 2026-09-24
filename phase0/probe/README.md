# Phase-0 API-102 Characterization Probe

Non-product framework probe (ADR-037). It is never shipped, never scoped to YouTube and
contains no project feature behavior. Delete it once Gate A/C evidence is archived.

- `target/` — harmless app whose `ProbeTarget.h1/h2` are called from 4 threads.
- `module/` — API-102 module scoped only to the target; logs lifecycle, reconciles hooks
  by stable ID, and owns one RemotePreferences listener. `ProbeControlActivity` is the
  manager side (`status`, `write`, `reload`) over libxposed service 102.

Build generation N (JDK from Android Studio JBR):

```bash
./gradlew :module:assembleDebug :target:assembleDebug -PprobeGeneration=2 -PprobeHooks=H1,H2 -PprobeFail=
```

`probeFail` = hook name to throw before reconciling it, or `veto` to refuse reload.

Drive over adb:

```bash
adb shell am start -n io.github.mrxsin.ytmprobe/.ProbeControlActivity --es action reload
```

```bash
adb logcat -s YTMProbe YTMProbeTarget
```

Results: `../HOT_RELOAD_RESULTS.md`.

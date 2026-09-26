<div align="center">

<img src="app/src/main/res/drawable-nodpi/ytm_hellfire.png" width="120" alt="YouTube Malbolge">

# YouTube Malbolge

**Hide Ads and SponsorBlock for YouTube, as an Xposed module whose every feature is written in Malbolge.**

Open YouTube → **Settings** → **YouTube Malbolge**.

<br>

[![Release](https://img.shields.io/github/v/release/MrxSiN/YouTubeMalbolge?include_prereleases&color=CC0000&label=release&style=for-the-badge)](https://github.com/MrxSiN/YouTubeMalbolge/releases)
[![Downloads](https://img.shields.io/github/downloads/MrxSiN/YouTubeMalbolge/total?color=3DDC84&logo=android&logoColor=fff&style=for-the-badge)](https://github.com/MrxSiN/YouTubeMalbolge/releases)
[![YouTube](https://img.shields.io/badge/YouTube-21.38.130-CC0000?logo=youtube&logoColor=fff&style=for-the-badge)](#compatibility)
[![Bindings](https://img.shields.io/badge/endpoints-30%20bound-3DDC84?style=for-the-badge)](#compatibility)
[![Licence](https://img.shields.io/github/license/MrxSiN/YouTubeMalbolge?color=CC0000&style=for-the-badge)](LICENSE)

</div>

---

> [!NOTE]
> **Bound to exactly one YouTube build: `21.38.130` (Google Play, stable).** Thirty target
> Endpoints are bound for that build, with no fallback resolvers. Tested on a Pixel 8 Pro with
> Vector v2.2 and libxposed API 102. On any other YouTube version the module installs nothing.

## Why it works this way

Most modules are written in Java or Kotlin and find YouTube's obfuscated members at runtime. This
one is neither. Every feature, setting, Endpoint and binding is a unit of
[Malbolge](https://en.wikipedia.org/wiki/Malbolge) source. At build time a pinned, bounded
evaluator runs those units, and a generator turns what they describe into plain classfiles that R8
and D8 then shrink into the APK. Nothing in the module interprets anything on the device.

|  | |
|---|---|
| 🧭 **Where you already are** | One "YouTube Malbolge" row above Account in YouTube's own Settings, drawn with YouTube's row layout. |
| 🎯 **Exact target** | Each hook names one member of YouTube `21.38.130`. No runtime DexKit, no search, no guessing. |
| ⚛️ **All or nothing** | Hooks install as one transaction. If any binding or hook fails, every hook is rolled back and every switch goes grey. |
| 🔍 **Checks itself** | The module app shows compatibility and a copyable hook report after YouTube's first start. |
| 🔒 **One store** | Every switch lives in libxposed RemotePreferences, read by YouTube and written by the module app. |
| 🧾 **Reviewable** | Changes are reviewed as normalized module graph diffs, not as Malbolge text. |

---

## Features

<details open>
<summary><b>🚫 Hide ads</b></summary>
<br>

A development port of part of Morphe's Hide Ads patch.

| Switch | What it does | Default |
|---|---|:---:|
| **Hide sponsored banners** | Replaces sponsored Home feed cards with YouTube's own empty component, and hides the sponsored attribution banner. | On |
| **Hide video ads** | Skips the two methods YouTube uses to build a video ad, and keeps ad items out of the Shorts feed before its adapter adds them. | On |
| **Hide Premium promotions** | Hides the compact YouTube Premium offer view. | On |

On device, the visible Home feed showed no sponsored card, and twelve Shorts pages after a blocked ad
still had working Like and Comments controls. Shopping, merchandise, paid promotion, end screen and
fullscreen ad filters are not ported. See [`docs/HIDE_ADS_PORT_STATUS.md`](docs/HIDE_ADS_PORT_STATUS.md).

</details>

<details open>
<summary><b>⏭ SponsorBlock</b></summary>
<br>

Skips segments the [SponsorBlock](https://sponsor.ajay.app) community has marked, as soon as
playback reaches them.

| Switch | Default |
|---|:---:|
| **Enable SponsorBlock** | On |
| **Skip sponsors** | On |
| **Skip self-promotion** | Off |
| **Skip interaction reminders** | Off |
| **Skip intros** | Off |
| **Skip endcards and credits** | Off |
| **Skip previews and recaps** | Off |
| **Skip hooks and greetings** | Off |
| **Skip filler tangents** | Off |
| **Skip non-music in music videos** | Off |

Defaults match Morphe: only sponsors skip automatically. Segments are fetched by hash prefix, so
the full video ID never leaves the phone, and the request runs on a worker thread, never on the
playback path. There is no skip button yet; a category skips only when its switch is on. See
[`docs/SPONSORBLOCK_PORT_STATUS.md`](docs/SPONSORBLOCK_PORT_STATUS.md) and ADR-038.

</details>

<details open>
<summary><b>⚙️ Settings</b></summary>
<br>

| Row | What it does |
|---|---|
| **YouTube Malbolge** | A row above Account in YouTube Settings, with the module's icon. It opens the module's settings page. |
| **Settings page** | All thirteen switches in two sections, drawn in YouTube's dark colours with a back bar. Changes are written to RemotePreferences and survive a process restart. |

The settings page is also on the launcher, for the case where the YouTube Settings row itself
cannot be hooked. See [`docs/SETTINGS_STATUS.md`](docs/SETTINGS_STATUS.md) and ADR-039.

</details>

---

## Compatibility

YouTube renames its obfuscated classes in every release. This module does not try to keep up at
runtime: it is bound to one build and says so.

- **Exact target.** `target/current/target-release.lock.yml` records the hashes of YouTube
  `21.38.130` and its thirty bound Endpoints. Each Endpoint is a `BindingSpec` in
  `source/80_bindings/`, found with DexKit in the separate Binding Laboratory app. DexKit never
  ships in the module.
- **Atomic installation.** The controller installs every hook or none. A failed binding rolls back
  all hooks, and the module app disables every switch.
- **Hook check.** Open YouTube once after installing. Until the check succeeds, feature switches
  stay grey. The module app then shows compatibility and a hook report to copy into a bug report.
- **Logs.** `adb logcat -s YtmMalbolge`:

  ```
  I YtmMalbolge: module hooks installed
  I YtmMalbolge: segments loaded: 2
  I YtmMalbolge: segment skipped
  ```

---

## Requirements

| | |
|---|---|
| **Android** | 12L or later (API 32+), built against API 37 |
| **YouTube** | `com.google.android.youtube` `21.38.130`, from Google Play |
| **Device** | Tested on a Pixel 8 Pro |
| **Framework** | [Vector](https://github.com/JingMatrix/Vector) v2.2, or any framework implementing libxposed API 102 |
| **Root** | Only what your Xposed framework needs |

Built against the modern [libxposed API](https://github.com/libxposed/api)
(`io.github.libxposed:api` and `service` 102.0.0), not the legacy `de.robv.android.xposed` bridge.

## Install

```
1. Install the APK from Releases
2. Enable YouTube Malbolge in your Xposed manager, scoped to YouTube
3. Force-stop YouTube, then open it once to run the hook check
4. YouTube → Settings → YouTube Malbolge
```

---

## How it works

```
intent
  → contract / schema
  → Malbolge source units          source/
  → Canonical Module Graph
  → Logical Module Plan            build/generated/plan.tsv
  → exact Verified Target Bindings source/80_bindings/
  → generated classfiles
  → R8 / D8
  → Vector / libxposed API 102
```

The build runs the Malbolge evaluator (profile `MBX-CLASSIC-REF/1`, defined against Ben Olmstead's
1998 reference interpreter) and the unit validator over thirty-one source units, generates 29
runtime classfiles, and packages the single API-102 Xposed entry. Hooks install in
`onPackageReady`. Settings are read from RemotePreferences, whose listener callbacks arrive on
Binder threads.

What the module deliberately does not have: runtime DexKit, a cache or prewarm step, a native
control plane, a plan interpreter, fallback resolver chains, a second backend, or placeholder
source. `architecture/authority-map.yml` is the authority; the reasons are in `adr/`.

---

## Build

Set `JAVA_HOME` to a JDK 17 and have Python 3 on `PATH`, then:

```bash
./gradlew :app:assembleRelease
```

The release build installs the eleven hooks, because production is enabled in
`target/current/target-release.lock.yml`. It is signed when `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_ALIAS`, `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_PASSWORD` are set, and
unsigned otherwise. Pushing a `v*` tag builds, signs and attaches the APK to a GitHub release
(`.github/workflows/android.yml`). `-PdeviceTest=true` forces the hooks on in a debug build and
is rejected for release. Toolchain pins live in `toolchain/LOCKFILE`: AGP
9.4.1, Gradle 9.6, JDK 17, API 37. `MANIFEST.sha256` holds the hash of every tracked source file.

## Design

```
source/
  20_semantics/    Endpoints: what each hook point means
  30_config/       ConfigItems stored in RemotePreferences
  40_manager/      the settings page and its entry
  50_diag/         hook compatibility diagnostics
  70_features/     Features and their Effects
  80_bindings/     exact 21.38.130 BindingSpecs
toolchain/         evaluator, validator, backend, Binding Laboratory
contracts/         lifecycle, configuration, binding and testing contracts
architecture/      authority map
adr/               decision records
target/            the exact target lock
```

Read in this order: `PHASE0_TECH_CHARACTERIZATION.md`, `PROJECT_MALBOLGE_XPOSED_YOUTUBE_BLUEPRINT.md`,
`phase0/EVIDENCE_MATRIX.md`, `phase0/HOT_RELOAD_PROBE_PROTOCOL.md`, `source/README.md`,
`architecture/authority-map.yml`.

---

<div align="center">

**GNU General Public License v3.0** · see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE)

Hide Ads and SponsorBlock are ported from Morphe [patches](https://github.com/MorpheApp/morphe-patches); attribution is in `NOTICE`.

</div>

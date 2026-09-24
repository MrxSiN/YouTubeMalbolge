# Settings status

Decision record: [ADR-039](../adr/ADR-039.md).

## Malbolge units

| Unit | Records |
| --- | --- |
| `source/40_manager/manager_settings.mal` | One "YouTube Malbolge" entry and a page with Hide ads (3 items) and SponsorBlock (10 items) |
| `source/70_features/settings_entry.mal` | Feature `settings.entry`, unguarded Effect `inject_settings_entry` |
| `source/20_semantics/endpoint_settings_entry.mal` | `settings_root` (OBSERVE, after original) and screen access CALL Endpoints |
| `source/20_semantics/endpoint_preference_api.mal` | Twelve androidx.preference CALL Endpoints |
| `source/80_bindings/binding_settings_root.mal` | `own.run()V`, field `own.a`, `faw.p()` |
| `source/80_bindings/binding_preference_fields.mal` | Context/Intent/layout fields and one constructor (with the row layout resource name) |
| `source/80_bindings/binding_preference_methods.mal` | Key, title, summary, order, icon-space, icon, add, find |

## Target facts (exact 21.37.42)

- Root settings fragment `owp` (tag `owp`) inflates `xml/settings_fragment_cairo`.
- `own.run()` calls `PreferenceScreen.removeAll()` and re-inflates after the list adapter
  exists, so anything added earlier is lost; the entry is added after it returns.
- A hook on `owp.e(PreferenceScreen)` (onCreateAdapter) never fired: ART inlined it into
  `faw.r()`. The builder `own.run()` is only reached through `Runnable`.
- androidx.preference class names are kept; members are obfuscated
  (`Q` setTitle, `n` setSummary, `M` setOrder, `L` setKey, `K` setIconSpaceReserved,
  `J` setIcon, `ai` addPreference, `l` findPreference; fields `j` context, `s` intent,
  `A` layout resource).
- YouTube's explicit launch of the module Activity is permitted (`BAL_ALLOW_VISIBLE_WINDOW`).

## Device validation — 2026-09-24

Pixel 8 Pro, Vector v2.2, YouTube 21.37.42, `-PdeviceTest=true` build:

- Settings shows one "YouTube Malbolge" row above "Account", using YouTube's row layout.
- Tapping the row starts `io.github.mrxsin.ytmalbolge/.generated.ManagerActivity`.
- The manager binds the service, shows current values, and a toggle survived a manager
  process restart (written to RemotePreferences). The test toggle was restored.

## Redesign validation — 2026-09-24

- The YouTube Settings accessibility tree contains one "YouTube Malbolge" option and no separate Hide ads or SponsorBlock options.
- The settings page displays all 13 switches in two sections with YouTube-like dark colors, a back bar, row spacing, and blue enabled switches.
- A toggle survived a manager process restart and was restored to its original value. Back returns to YouTube Settings.

## Icon and module identity — 2026-09-24

- The APK supplies the hellfire play icon and a module description for Vector.
- The YouTube Settings row loads that same image from the module APK. The row and its manager page were checked on the Pixel 8 Pro after reinstalling the device test APK.
- Release resource optimization renames the launcher drawable, so the build also packages the same source image at a stable Java resource path for the injected row.
- The Phase-0 probe is independent characterization code, scoped only to its probe target app; YouTube Malbolge does not require it.

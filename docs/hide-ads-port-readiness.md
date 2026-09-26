# Hide Ads port readiness (2026-09-23)

Status: **historical pre-migration analysis**. ADR-040 and the executable Malbolge AOT
implementation supersede the freeze/gate statements below.

## Upstream reference

Morphe `main` commit `92dd0ef86d12e1806152b454486269983b8db139`:

- [YouTube HideAdsPatch.kt](https://github.com/MorpheApp/morphe-patches/blob/92dd0ef86d12e1806152b454486269983b8db139/patches/src/main/kotlin/app/morphe/patches/youtube/ad/HideAdsPatch.kt)
- [YouTube ad fingerprints](https://github.com/MorpheApp/morphe-patches/blob/92dd0ef86d12e1806152b454486269983b8db139/patches/src/main/kotlin/app/morphe/patches/youtube/ad/Fingerprints.kt)
- [YouTube AdsFilter.java](https://github.com/MorpheApp/morphe-patches/blob/92dd0ef86d12e1806152b454486269983b8db139/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/components/AdsFilter.java)
- [Morphe NOTICE](https://github.com/MorpheApp/morphe-patches/blob/92dd0ef86d12e1806152b454486269983b8db139/NOTICE)

The upstream patch modifies YouTube bytecode and resources and calls a Java extension.
This project uses Vector hooks and compiled Malbolge behavior. Upstream injection
positions and fingerprints are research evidence, not reusable runtime bindings.

## Smallest semantic slices

| Slice | Upstream behavior | Candidate semantic boundary |
|---|---|---|
| General ad components | Filters Litho identifiers and paths, with context exceptions; hides attribution views | Component decision plus separate view action |
| Video ads | Suppresses two player ad entry points; changes request/client-context decisions | Player decisions plus request transform |
| Premium promotions | Filters statement banners; suppresses measured Premium offer view | Element transform plus view decision |
| Shopping and promotions | Filters merchandise, product cards, player popup panels, end-screen store banner, paid-promotion labels | Separate component, panel, collection, and view decisions |

These are candidate boundaries. Do not create one broad hook or one mutable global
filter. Keep each feature's configuration and effects within its own declaration.
Admit a shared capability only if it meets `contracts/capability-policy.md`.

Preserve upstream distinctions during design:

- General ads and video ads have separate switches.
- Video ad request suppression depends on the ad whitelist being empty. Player-level
  suppression can still inspect the current channel. A request built before channel
  identity exists cannot apply a per-channel exception.
- Component filtering has explicit exceptions for comments, home video context,
  recent library shelf, and related video context.
- Premium statement banners distinguish doodles from Premium promotions.
- Several upstream effects change layout or collection insertion. Filtering only
  component data could leave empty space or alter unrelated elements.

Before any product port, specify typed Endpoint contracts and exact effects. Prove
each Endpoint's single BindingSpec unique against locked YouTube bytes. HOT effects
must read precomputed ConfigSnapshot state; they cannot scan DEX, access storage,
call Binder, log, or interpret Malbolge. Preserve feature independence: adding a
slice must not edit the central bootstrap or Hook Controller. Keep generated Java
entry count at one. Review upstream GPLv3 and NOTICE obligations before shipping
derived code.

## Device evidence

`D:\Android\platform-tools\adb.exe` reached a rooted Pixel 8 Pro running Android 17.
Installed `com.google.android.youtube` reports `21.37.42` (`1561296049`). Its
`base.apk` SHA-256 is
`39a955c0e7695efdd9d3fef1d609aa9c82c3aecf118f75da5ae8017102b9211e`,
matching `target/current/target-release.lock.yml`. YouTube process was running.
This is the existing high-end characterization device, not the required low-end
reference device. Device identity does not prove any ad Endpoint binding.

## Next acceptance work

1. Complete remaining Gate A evidence, including low-end rooted reference device,
   Malbolge reference evaluator/conformance, RemotePreferences reconnection, and
   classfile/R8 smoke artifact.
2. Freeze and test Gate B semantics and Hook Controller; prove one unique binding
   on exact YouTube bytes.
3. Implement one semantic slice in Malbolge, generate its adapter, then run
   endpoint vectors and device acceptance. Expand only after that slice passes.

No app or module was modified or installed during this assessment.

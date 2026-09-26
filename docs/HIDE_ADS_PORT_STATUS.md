# Hide Ads port status

Upstream authority: Morphe patches commit
`92dd0ef86d12e1806152b454486269983b8db139`,
`patches/.../youtube/ad/HideAdsPatch.kt`, `Fingerprints.kt`, and
`extensions/.../youtube/patches/components/AdsFilter.java`.

The source patch changes YouTube DEX and calls a bundled Java extension. This project
uses an Xposed module generated from Malbolge records; each physical method or resource
access therefore needs a resolver fingerprint/fallback update and device validation.

| Upstream behavior | This port |
| --- | --- |
| Compact Premium offer `onMeasure` | Bound, generated hook installs on device; visual effect unobserved |
| Premium statement banner proto | Not bound |
| Sponsored attribution banner view (`Lasel.c()V`, resource `ad_attribution`) | Bound, but the observed sponsored feed card does not call this method |
| Sponsored feed Litho components (`Lvay.a(...)Lhbp;` reference fixture) | Three helper Endpoints and structural runtime resolution. A reference-device probe identified the component relationship. The MBP1 program owns all filter patterns and returns YouTube's empty component on a match. |
| Sponsored Shorts feed items | Bound to the adapter's two item insertion methods (`Lasgh.H` and `Lasgh.I`) and its ad predicate (`Laqak.Q`). The development build discards an ad item before the adapter adds it. Device inspection found ordinary Shorts with view type `6` and an ad with view type `10004`; the latter follows the bound predicate branch. An ADB probe logged blocked items and found Like and Comments controls on twelve subsequent Shorts pages. |
| Video ad loader and player bytes methods | Bound, generated hooks install; ad playback effect unobserved |
| Video ad request OS name and channel whitelist | Not bound |
| Shopping, merchandise, self sponsor, paid promotion, player popup, end screen filters | Not bound |
| Fullscreen ads dependency | Not bound |
| User settings UI | Implemented; "Hide sponsored banners" controls both attribution visibility and feed filtering through RemotePreferences |

Production is enabled in `target/current/target-release.lock.yml` for release 1.0.0;
the release build activates eleven generated hooks. The current device check covers
the visible Home feed and Shorts adapter items; other ad surfaces need separate validation. This is not a
complete Hide Ads port.

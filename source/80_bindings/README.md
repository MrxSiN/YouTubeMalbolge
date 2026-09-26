# BindingSpecs

Ten Malbolge units provide thirty BindingSpecs for exact YouTube `21.38.130`.
Each Endpoint has one binding. DexKit validation and the verified set are in
`phase0/BINDING_LAB_RESULTS.md` and `target/current/verified-binding-set.json`.

`binding_sponsorblock_player.mal` provides the four SponsorBlock BindingSpecs
(player controller constructor, video stage, playback progress constructor, seek).

`binding_settings_root.mal`, `binding_preference_fields.mal` and
`binding_preference_methods.mal` bind the settings screen builder and the androidx.preference
members, including the target row layout resource name. The row icon comes from
the module APK.

`binding_shorts_ad_feed.mal` binds two Shorts adapter item insertion methods
and the ad predicate for the exact target.

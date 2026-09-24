# Features

`hide_ads_premium_offer.mal` and `hide_ads_video_ads.mal` emit two Features and
three Effects. The development hooks installed on the bound device; production
hooks and features remain disabled by the target lock.

`hide_ads_shorts_ad.mal` declares two insertion Effects guarded by `hide_video_ads`.
They discard ad items before the Shorts adapter adds them.

`sponsorblock_skip_segments.mal` emits the SponsorBlock Feature, three Effects
(`capture_receiver`, `observe_video_id`, `skip_segments`) and its `SegmentSource`.

`settings_entry.mal` emits the `settings.entry` Feature whose unguarded Effect adds the
module section to YouTube's settings screen.

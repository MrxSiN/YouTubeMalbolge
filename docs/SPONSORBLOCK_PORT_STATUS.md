# SponsorBlock port status

Upstream authority: Morphe patches commit
`92dd0ef86d12e1806152b454486269983b8db139`,
`patches/.../youtube/layout/sponsorblock/SponsorBlockPatch.kt`,
`patches/.../youtube/video/information/VideoInformationPatch.kt`,
`patches/.../youtube/video/videoid/Fingerprints.kt`, and
`extensions/shared-youtube/.../sponsorblock/requests/SBRequester.java`.

The network decision is [ADR-038](../adr/ADR-038.md).

## Malbolge units

| Unit | Records |
| --- | --- |
| `source/20_semantics/endpoint_sponsorblock_player.mal` | Endpoints `player_controller`, `video_stage`, `playback_progress`, `player_seek` |
| `source/30_config/config_sponsorblock.mal` | `sponsorblock_enabled` and nine `sponsorblock_skip_<category>` ConfigItems (group `sponsorblock`) |
| `source/70_features/sponsorblock_skip_segments.mal` | Feature, three Effects, one `SegmentSource` |
| `source/80_bindings/binding_sponsorblock_player.mal` | Four BindingSpecs for exact `21.37.42` |

Defaults match Morphe: only `sponsor` skips automatically. Morphe's other categories
default to manual skip or ignore; without a skip button they do not skip here unless
their ConfigItem is enabled.

## Bindings (exact 21.37.42)

| Endpoint | Member | Upstream fingerprint | Role |
| --- | --- | --- | --- |
| `player_controller` | `Lartc;-><init>(…35 params…)V` | `PlayerInitFingerprint` | capture seek receiver |
| `video_stage` | `Ljko;->h(Laqdx;)V`, field `h` | `VideoIdBackgroundPlayFingerprint` | read video ID after original |
| `playback_progress` | `Laqdy;-><init>(JJJJJJJZLjava/lang/String;)V`, arg 0 | `PlayerControllerSetTimeReferenceFingerprint` | position in ms |
| `player_seek` | `Lartc;->ar(JLbkku;)Z`, `SEEK_SOURCE_UNKNOWN` | `SeekFingerprint` | seek call |

`aqdy` argument 8 is the client playback nonce (16 characters), not the video ID; this
was observed on device and is why `video_stage` exists.

## Device validation — 2026-09-24

Pixel 8 Pro, Vector v2.2, stock YouTube `21.37.42`, `-PdeviceTest=true` build.
Video `AdkShdDaivc` has one `sponsor` segment `[19.768, 41.574]`:

```text
00:06:49.937 I YtmMalbolge: module hooks installed
00:06:59.804 I YtmMalbolge: segments loaded: 2
00:07:19.452 I YtmMalbolge: segment skipped
```

Playback continued from about 41.5 s. The request ran on a worker thread and the skip
ran on the main thread. No `VerifyError`, crash, or seek failure was logged.

## Not ported

Skip/voting/create-segment buttons, seekbar segment drawing, time-without-segments
text, toasts, skip-count tracking, minimum segment duration, custom API URL, channel
whitelist, MDX (cast) seeking, and a settings UI. Settings are RemotePreferences only.

# Premium offer binding laboratory — 2026-09-23

Exact target: stock Play YouTube `21.37.42` (`1561296049`), base APK SHA-256
`39a955c0e7695efdd9d3fef1d609aa9c82c3aecf118f75da5ae8017102b9211e`.
The APK was pulled through adb and matched `target/current/target-release.lock.yml`.

DexKit 2.2.0 ran in the separate `toolchain/bindinglab` app on the rooted Pixel 8 Pro.
The Xposed product APK does not package DexKit or its native library. Query:
declaring class `CompactYpcOfferModuleView`, method `onMeasure`, parameters `(int,int)`,
return `void`, modifiers `PROTECTED|FINAL`.

Observed result:

```text
count=1 dex=8
candidate=Lcom/google/android/apps/youtube/app/red/presenter/CompactYpcOfferModuleView;->onMeasure(II)V
modifiers=20 superclass=Landroid/view/ViewGroup;
opcodeCount=124
tail=[move-result, add-int/2addr, if-le, iget-object, invoke-virtual,
      move-result, add-int, add-int/2addr, add-int/2addr, add-int/2addr,
      invoke-virtual, return-void]
```

Independent `dexdump` inspection of `classes4.dex` found exactly one declaring class
with this descriptor. Its method has 227 16-bit code units. This matches the upstream
`GetPremiumViewFingerprint` class, signature, and trailing opcode pattern at pinned
Morphe commit `92dd0ef86d12e1806152b454486269983b8db139`.

The [verified binding set](../target/current/verified-binding-set.json) stores
compact descriptors and validation digests. A development APK installed the
Premium hook on the exact target; the offer view itself was not observed, so
visual hiding remains unverified.

After enabling the development APK in Vector's embedded manager and force-stopping
YouTube, logcat showed:

```text
D/VectorModuleManager: Loading module io.github.mrxsin.ytmalbolge
I/YtmMalbolge: premium offer hook installed
```

DexKit also matched exactly one method for each of Morphe's
`LoadVideoAdsFingerprint` and `PlayerBytesAdLayoutFingerprint` string sets:

```text
count=1 candidate=Lacgq;->t(Ljava/util/List;)V modifiers=17 superclass=Ljava/lang/Object; opcodeCount=487
count=1 candidate=Lacpe;->b(Lakgh;)V modifiers=1 superclass=Ljava/lang/Object; opcodeCount=251
```

The nine-class development build installs all four hooks on the exact target.
Logcat showed `I/YtmMalbolge: hide ads hooks installed` and no `VerifyError`
or installation failure. No Premium offer or video-ad playback was available
to confirm the user-visible effects. The channel whitelist behavior remains
unimplemented.

The sponsored attribution binding was then verified by direct `dexdump` inspection of
the pinned base APK. `Lasel` extends `Larrh` (`FrameLayout`), and its unique `c()V`
method has 60 code units, inflates the sponsored layout, resolves resource
`0x7f0b00bf` (`ad_attribution`) with `View.findViewById`, and stores the result. The
generated hook hides the `Lasel` container before the original method runs, removing
the card and its layout space. The characterization phone is currently credential-
locked after the clean Vector reload, so the final screenshot check is pending device
unlock.

## SponsorBlock player bindings — 2026-09-24

`adb shell am start -n io.github.mrxsin.ytmalbolge.bindinglab/.BindingLabActivity
--es apk <base.apk> --es search sponsorblock-player` matched exactly one member each:

```text
player-controller candidate=Lartc;-><init>(Luog;…;Larcd;)V modifiers=65537 opcodeCount=116
player-seek       candidate=Lartc;->ar(JLbkku;)Z modifiers=17 opcodeCount=629
video-stage       candidate=Ljko;->h(Laqdx;)V modifiers=49 opcodeCount=32
playback-progress candidate=Laqdy;-><init>(JJJJJJJZLjava/lang/String;)V modifiers=65537 opcodeCount=12
```

They correspond to Morphe's `PlayerInitFingerprint`, `SeekFingerprint`,
`VideoIdBackgroundPlayFingerprint`, and the constructor called by
`PlayerControllerSetTimeReferenceFingerprint`. `Lbkku;` is the seek-source enum whose
first constant is `SEEK_SOURCE_UNKNOWN`. Device results are in
`docs/SPONSORBLOCK_PORT_STATUS.md`.

## Settings entry bindings — 2026-09-24

`--es search settings-root` matched exactly one member each:

```text
settings-builder candidate=Lown;->run()V modifiers=17 opcodeCount=1035
preference-api   Q(CharSequence)V, n(CharSequence)V, M(I)V, L(String)V on androidx.preference.Preference
preference-api   ai(Preference)V, l(CharSequence)Preference on androidx.preference.PreferenceGroup
```

`own.run()` is the only `run()` that calls `PreferenceGroup.af()` (removeAll) and uses
`xml/settings_fragment_cairo` (`0x7f180027`). Field and constructor bindings, and the
`K`/`J` members, were confirmed with `dexdump`; see `docs/SETTINGS_STATUS.md`.

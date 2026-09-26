# Runtime Environment Contract

The module targets modern libxposed API 102 and the Android range declared by Gradle.
Framework compatibility claims are not device evidence for this module.

YouTube compatibility is determined at runtime from package/version/base/split identity,
validated cached descriptors, and Malbolge-defined fingerprints. The checked-in target
is a reference fixture. Compatible updates may resolve automatically; redesigns that no
longer produce one safe candidate disable only the affected group.

Release support still requires physical-device checks for startup, playback, feed,
Shorts, settings, SponsorBlock, cold discovery, warm cache, and update invalidation.

# Runtime Environment Contract

## Upstream framework envelope

Current Vector documentation states support for Android 8.1 through Android 17 Beta and
requires a recent Magisk or KernelSU environment with Zygisk enabled.

That is a **framework capability envelope**, not this module's support promise.

## Module support promise

A module release supports only the frozen **Reference Runtime Environment** used for
acceptance plus environments explicitly validated as equivalent.

Reference environment records:

```text
device model / SoC
Android version/build fingerprint
root implementation/version
Zygisk implementation/version
Vector exact release/build
YouTube exact Target Release Manifest
```

## Android minSdk policy

Do not set module `minSdk` merely to Vector's lowest supported Android version.

At architecture freeze:

```text
module minSdk = max(Vector requirement, exact supported YouTube target requirement,
                    project manager/runtime API requirement)
```

This follows the latest-YouTube-only policy and avoids advertising unsupported old
Android/YouTube combinations.

## Android 17 caveat

Upstream support claims do not replace project acceptance testing. Framework/device/ROM
bugs can exist within the advertised range, so release support is based on the reference
environment and test evidence rather than the broad README range alone.

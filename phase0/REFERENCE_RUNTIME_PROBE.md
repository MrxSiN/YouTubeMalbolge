# Reference Runtime Environment Selection Protocol

The framework README's broad compatibility range is not the project release claim.

Select one low-end rooted device for architectural acceptance.

Record:

```text
device model
SoC
RAM
Android version
build fingerprint
Magisk or KernelSU version
Zygisk implementation/version
Vector release + package/module hash
YouTube Target Release Manifest digest
```

The same device becomes the baseline for:

- hook overhead;
- configuration publication;
- hot reload;
- classloader collection;
- startup overhead;
- retained heap.

A high-end device may be added as a comparison but cannot replace the low-end baseline.

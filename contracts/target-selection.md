# Compatibility policy

The module supports dynamically resolved installed YouTube builds; it does not promise automatic compatibility with every future build.

`target/current` is the reference regression fixture used for differential tests and fallback descriptors. Runtime compatibility is determined per installed base/split identity by validated resolution, not by an exact version or APK hash allowlist. Any unresolved or ambiguous feature group fails open.

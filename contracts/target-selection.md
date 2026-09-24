# Target Selection Policy

The phrase “latest supported YouTube version” is resolved by a release-engineering
policy, not by an APK website string.

## Selection rule

A production release supports exactly one **Reference Target Artifact Set**:

```text
package = com.google.android.youtube
channel = stable by default
version name/code
approved signing certificate set
base APK
all code-bearing split APKs
code-bearing DEX digests
explicit allowed process names
```

Beta/dev channels are excluded unless the project owner explicitly selects them for
that release.

## Public trackers

Public APK indexes are discovery hints only. They are not authoritative for:

- staged Play rollout;
- device-specific split selection;
- signing-certificate variant;
- installed artifact bytes.

The binding laboratory must acquire and hash the actual reference artifacts used for
release validation.

## Split identity

Target identity MUST include every split containing executable code relevant to
bindings.

Pure resource/density/language splits need not invalidate compatibility unless a
Feature explicitly binds to their content.

## Update policy

A new YouTube release replaces the current target lock after validation.

Historical target locks/evidence may remain privately for regression research but are
never shipped and never used as runtime fallbacks.

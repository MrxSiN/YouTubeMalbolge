# Target Binding Contract

Target discovery is a release-engineering activity, never a target-process service.

```text
Malbolge-originated BindingSpec
+ exact Reference Target Artifact Set
+ DexKit 2.2 build laboratory
→ candidate set
→ hard structural/evidence predicates
→ post-resolution validation
→ uniqueness proof
→ Verified Target Binding Set
```

## One implementation path

Each Endpoint has exactly one authoritative BindingSpec/pipeline.

Allowed inside that pipeline:

- multiple required strings/literals;
- type/signature constraints;
- caller/callee evidence;
- field relationships;
- negative evidence;
- hard post-resolution validators.

Prohibited:

- Strategy A → Strategy B → Strategy C fallback resolver chain;
- accept-first-candidate behavior;
- “best score wins” without uniqueness proof;
- runtime scanning when build-time binding failed.

A score may order investigation candidates, but production acceptance is boolean:
**exactly one candidate must satisfy the complete acceptance contract**.

## Build-only DexKit

DexKit 2.2 is used off the target hot path. Its metadata/cross-platform capabilities
allow the binding laboratory to operate without requiring the injected YouTube process.

No DexKit library/native binary is packaged for runtime use.

## Runtime materialization

Release contains only compact verified descriptors and validation digests.

At target startup:

1. exact target identity passes;
2. descriptor is materialized with the target ClassLoader;
3. cheap shape/staticness/declaring-type checks pass;
4. Hook Controller may install.

Failure disables the dependent Endpoint/Feature or the whole module when bootstrap
critical. It never starts a runtime resolver.

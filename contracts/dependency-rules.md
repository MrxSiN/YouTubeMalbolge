# Dependency Rules

Canonical direction:

```text
Feature
 → Capability / Semantic Endpoint
 → Effect
 → generated endpoint pipeline
 → TargetBinding
 → target member adapter
 → Vector/libxposed adapter
```

Allowed side dependencies:

```text
Feature → ConfigView
Feature → FeatureState
Feature → DiagnosticSink
Manager → ConfigStore / framework service
Binding laboratory → DexKit
```

Forbidden:

```text
Feature → Vector/libxposed
Feature → DexKit
Feature → obfuscated YouTube symbols
Feature → reflection Method/Field
Feature → raw SharedPreferences/storage
Feature → global service locator
Feature → release-hardening machinery
```

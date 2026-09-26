# Dependency rules

```text
Malbolge program → MBP1 generic ABI → AOT JVM bytecode → generic host capabilities
resolver policy → cached runtime resolver → validated Executable/Field → libxposed
```

Conventional code must not contain feature IDs, ad patterns, category policy, setting-specific branches, handler maps, or target behavior decisions. It may contain reusable reflection/DexKit, networking, scheduling, UI, cache, diagnostics, and libxposed adapters.

# CLAUDE.md — Audited v4 Maintainer Guide

`AGENTS.md` and `architecture/authority-map.yml` are authoritative.

Reason from:

```text
intent
→ contract/schema
→ Malbolge source (only after freeze)
→ Canonical Module Graph
→ Logical Module Plan
→ exact Verified Target Binding Set
→ generated classfiles
→ R8/D8
→ Vector/API-102 runtime
```

Never reconstruct project intent from obfuscated YouTube names.

For reviews, prefer normalized CMG/LMP semantic diffs over raw Malbolge text.

Do not reintroduce runtime DexKit, cache/prewarm, native control plane, custom plan VM,
fallback resolver chains, alternate backend, or placeholder executable source.

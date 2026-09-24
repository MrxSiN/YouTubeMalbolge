# Release Hardening Contract

Goal: statically expensive to understand; operationally ordinary.

## Baseline hardening

Use standard R8 full optimization/minification and allow it to perform whole-program
shrinking, inlining, class merging, repackaging, and symbol obfuscation.

Project-controlled pre-R8 representation choices may include:

- generated opaque physical class/member names;
- semantic class regrouping;
- non-ABI numeric ID remapping;
- deterministic table ordering;
- constant-bank sharding;
- encoding of cold binding descriptor data;
- removal of source/debug/local metadata;
- private-only R8/provenance maps.

## No post-R8 semantic DEX rewriting

The baseline architecture does not rewrite optimized DEX after R8 to create custom
control-flow transformations.

Reason: the Android optimizer already performs layout/code optimizations and Android
documentation warns that tools modifying R8 output can regress runtime performance.

If a future hardening technique requires post-R8 rewriting, it needs:

1. a separate ADR;
2. verifier coverage;
3. debug/release semantic-equivalence proof;
4. low-end performance proof;
5. reproducibility proof.

## Stable ABI exception

HookAbiId is stable within a hot-reload epoch and is excluded from random remapping that
would break old/new generation correspondence.

## Never harden by runtime hostility

Forbidden:

- debugger killing;
- ptrace battles;
- emulator sabotage;
- root/Vector/LSPosed hiding;
- analysis-tool detection crashes;
- self-modifying executable code;
- remote code;
- persistent integrity polling.

## Security reality

Obfuscation is a work-factor mechanism, not confidentiality. Secrets required by the
client at runtime are assumed recoverable by a determined analyst.

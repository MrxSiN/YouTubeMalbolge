# Diagnostics Contract

Diagnostic record:

```text
diag_code
opaque semantic_id
lifecycle_transition
reason_code
small_numeric_context
```

Categories:

```text
BOOT TARGET CONFIG BIND HOOK RELOAD CALLBACK COMPAT
```

Rules:

- bounded storage only;
- no per-frame logging;
- no account/session/auth/watch-history/user-content payloads;
- callback failure should preserve original host behavior where safe and disable the
  failing effect/endpoint for process lifetime rather than retry forever.

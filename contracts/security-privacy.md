# Security and Privacy

Baseline runtime:

- scope only `com.google.android.youtube`;
- no module telemetry;
- no runtime network required by architecture; opt-in feature requests follow
  ADR-038 (HTTPS, hash-prefix, off the HOT path, no identifiers);
- no remote executable payloads;
- no account/token/cookie/auth-header collection;
- no persistent integrity polling;
- no anti-debugger/process sabotage;
- no root/Vector hiding;
- no self-modifying executable code.

The manager should request no unrelated Android permissions.

Encoded release constants are obfuscation, not secret storage.

# Malbolge Frontend Conformance

No executable Malbolge architecture stubs are included.

Before Gate A closes, add a pinned reference corpus containing:

- accepted classic/reference programs with exact output/state expectations;
- malformed source;
- deterministic step-limit case;
- deterministic output-limit case;
- invalid emission-frame cases;
- two-clean-host replay hashes.

Frontend conformance is against the pinned reference evaluator, not a lexical smoke rule.

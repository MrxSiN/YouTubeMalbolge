# Config Schema v1

Fields:

```text
schema_version
items[]
defaults
validation_rules
migration_edges
safe_fallback
```

Each item declares exactly one persisted `value_type` from the neutral set in
`contracts/configuration.md` (`boolean|int|long|float|String|Set<String>`). A schema that
declares any other persisted type fails validation.

Persistent configuration is manager-owned.

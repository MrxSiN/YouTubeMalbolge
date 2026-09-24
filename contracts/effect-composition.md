# Effect Composition

Generated endpoint pipeline:

```text
schema mapping
→ PRE_TRANSFORM
→ DECISION
→ ORIGINAL / declared replacement
→ POST_TRANSFORM
→ OBSERVATION / EVENT
```

Allowed composition policies:

- `EXCLUSIVE`
- `ORDERED_TRANSFORM`
- `FIRST_NON_ABSTAIN`
- `BOOLEAN_AND`
- `BOOLEAN_OR`
- `BITSET_UNION`
- `OBSERVE_ALL`

`LAST_WINS` is not a default policy. Mutating effects without an explicit compatible
composition policy are rejected at semantic validation.

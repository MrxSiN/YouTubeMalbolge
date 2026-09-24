# Feature Model

A Feature is a semantic product unit, not a hook class.

Required fields:

```text
feature_id
intent
required_capabilities[]
required_endpoints[]
config_items[]
effects[]
state_owner
process_scope
performance_class
reload_class
diagnostic_identity
```

No feature is active in the v4 architecture baseline.

A normal post-freeze feature addition should add its own Feature/Effect declarations and
tests without modifying central bootstrap, Hook Controller, Vector adapter, compiler
backend, or a global registry switch.

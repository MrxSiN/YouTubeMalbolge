# Semantic Endpoint ABI

Operation classes:

```text
EVENT
OBSERVATION
DECISION
TRANSFORM
ACTION
```

Each endpoint contract declares:

```text
endpoint_id
meaning
operation_class
input_schema
output_schema
nullability
phase
thread_context
reentrancy
frequency
lifetime
allowed_side_effects
mutation_permissions
failure_behavior
composition_policy
ordering_constraints
performance_class
reload_class
process_scope
binding_owner
```

Feature code never receives raw hook argument arrays, reflection members, Vector hook
handles, DexKit results, or obfuscated symbol names.

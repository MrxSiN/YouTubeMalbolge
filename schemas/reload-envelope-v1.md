# ReloadEnvelope v1

A cross-generation state envelope.

```text
schema_version
reload_epoch
target_manifest_digest
config_generation
config_value_blob
feature_state_records[]
diagnostic_counters[]
```

Every payload type must be explicitly whitelisted as classloader-neutral.

No project implementation object, callback, thread, HookHandle, or frontend/runtime
service object may be embedded.

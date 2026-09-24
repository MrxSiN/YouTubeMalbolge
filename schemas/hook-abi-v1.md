# Hook ABI v1

```text
hook_abi_id
endpoint_id
phase
reload_epoch
```

`hook_abi_id` is opaque but stable across hot-reload-compatible generations.
It is not subject to per-release random remapping within the epoch.

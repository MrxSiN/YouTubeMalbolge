# Reload State v1

Allowed reload classes:

```text
RELOAD_ATOMIC_ENDPOINT
RELOAD_MIXED_SAFE
RESTART_REQUIRED
```

Transferred state must be host-neutral and must not retain arbitrary old-generation
project objects/classloaders.

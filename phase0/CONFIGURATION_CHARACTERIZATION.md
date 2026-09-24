# RemotePreferences Characterization

## One store, two access surfaces

The architecture distinguishes access surface from source of truth.

### Injected process

The modern Xposed wrapper exposes:

```text
getRemotePreferences(name)
```

The result is SharedPreferences-compatible and can register a change listener.

The runtime reads it only in COLD/WARM configuration work, validates values and publishes
an immutable ConfigSnapshot.

### Manager app

The service interface exposes:

```text
XposedService.getRemotePreferences(name)
```

The manager edits the same framework-managed preference namespace.

## Required framework capability

Injected-process remote access is conditioned by the framework remote capability
(`PROP_CAP_REMOTE`).

For a v4 production release, missing remote capability means:

```text
configuration service unavailable
→ do not create another persistence/IPC path
→ use safe defaults or fail closed according to config criticality
```

## Pending device probes

Measure:

- listener callback thread;
- callback coalescing/order under rapid edits;
- service death and rebind behavior;
- manager edit visibility latency in injected process;
- listener unregister behavior during hot reload;
- behavior if preferences change while reload is in progress.

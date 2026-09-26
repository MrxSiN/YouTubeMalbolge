# Configuration Contract

## One persistent source of truth

Canonical persistent configuration is the framework-managed RemotePreferences namespace.

There are two access surfaces, not two stores.

### Manager access

```text
manager UI
→ libxposed service 102
→ XposedService.getRemotePreferences(group)
→ edit
```

The manager surface is generated from the `SettingsPage` record (ADR-039); YouTube's
settings entry only launches it.

### Injected-process access

```text
XposedModule/XposedInterfaceWrapper.getRemotePreferences(group)
→ SharedPreferences-compatible remote view
→ listener / low-frequency read
→ parse/migrate/validate
→ immutable ConfigSnapshot
→ atomic publication
→ HOT readers
```

Injected access requires the framework remote capability (`PROP_CAP_REMOTE`).

There is no second local preference database, broadcast fallback, custom Binder protocol,
or polling fallback.

## Persisted value types

Persisted RemotePreferences values are restricted to framework-neutral
SharedPreferences types:

```text
boolean | int | long | float | String | Set<String>
```

Serializable, Parcelable, project classes and any R8-renamable type are forbidden. The
framework daemon deserializes values without the module classloader, so such values fail
(`BadParcelableException` / `ClassNotFoundException` observed under Vector v2.2 — see
`phase0/DEVICE_OBSERVATIONS.md`). Structured settings are encoded as versioned String
values described by the config schema.

Manager writes use an explicit commit whose failure is caught and surfaced as a manager
diagnostic. A failed write never crashes the manager and never publishes partial state.

## Snapshot rules

- HOT callbacks never access RemotePreferences directly;
- HOT callbacks never perform Binder/storage work;
- snapshot contains only runtime-ready compact values;
- invalid update is not published;
- current process retains last-known-good snapshot;
- missing/invalid config at fresh startup yields safe defaults;
- publication is whole-snapshot atomic;
- generation increases monotonically.

## Listener threading

Measured under Vector v2.2: preference-change callbacks arrive on Binder threads
(`BINDER_POSSIBLE`), 0–13 ms after the manager commit. The listener validates and
publishes by atomic reference swap only; it never blocks the Binder thread on host or
UI work.

## Lifetime

One listener is attached during package-ready initialization. API-102 reload unregisters
the old listener before installing the new generation, preventing duplicate callbacks.

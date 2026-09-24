# Vector/API-102 Characterization Contract

## Framework selection

```text
Vector: stable v2.2
release commit: 88f8e1f
module API: libxposed 102.0.0
```

Canary builds are excluded from the production architecture.

## Module API dependency

Backend dependency semantics:

```text
libxposed API 102.0.0 = compile-only
```

The API implementation comes from Vector at runtime.

## Service dependency

Manager-side dependency semantics:

```text
libxposed service 102.0.0 = packaged manager dependency
```

It is used for framework communication, RemotePreferences, framework state and explicit
hot-reload requests.

## Hook interface subset

Project adapter exposes no more than:

```text
install(executable, hookId, priority, exceptionMode, interceptor)
replace(oldHandle, interceptor)
remove(handle)
id(handle)
frameworkInfo()
remotePreferences(name)
log(event)
```

This is conceptual architecture, not source code.

## Error distinction

Framework failures (`XposedFrameworkError`, `HookFailedError`) are not ordinary Feature
errors.

Feature semantic exceptions may be contained by protective mode/circuit rules.

Framework errors are diagnostic framework faults and must not silently trigger alternate
hook implementations.

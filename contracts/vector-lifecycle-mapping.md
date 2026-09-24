# Vector/libxposed Lifecycle Mapping

## `onModuleLoaded`

Process-level framework attachment and environment capture only.

Do not resolve target members or install normal product hooks here.

## `onPackageLoaded`

Provides package information and the default ClassLoader before the final
AppComponentFactory-ready boundary.

Use only for an Endpoint explicitly classified:

```text
availability_phase = PACKAGE_LOADED
```

It is not a second general initialization path.

## `onPackageReady`

Provides the final package ClassLoader and AppComponentFactory-ready state.

This is the canonical normal target-ready boundary for v4.

Responsibilities:

1. verify first/relevant YouTube package event;
2. capture final ClassLoader;
3. finish target/process identity validation;
4. initialize validated ConfigSnapshot;
5. materialize Verified Target Bindings;
6. install `PACKAGE_READY` hooks through Hook Controller;
7. enter READY.

## `onHotReloading`

Old generation:

- quiesce architecture-owned mutable work;
- unregister preference listener;
- build classloader-neutral ReloadEnvelope;
- return false if safe transfer is impossible;
- otherwise approve reload.

## `onHotReloaded`

New generation:

- restore neutral state;
- revalidate target identity;
- rebuild configuration/listener state;
- inspect old hook handles;
- reconcile physical hooks by stable HookAbiId;
- enter READY only after validation.

## Callback replay rule

The architecture MUST NOT depend on normal lifecycle callbacks being replayed after hot
reload.

Whether Vector v2.2 replays any such callback in practice is measured by Phase-0 device
probe HR-01. Correctness cannot depend on the result.

## `LATE_SIGNAL`

Late Endpoints install only from an already-bound semantic signal.

No polling, broad ClassLoader hook, reflection scan or target-process DexKit is allowed.

## Entry detachment

v4 does not detach its only entry while hot reload is enabled, because that would make
lifecycle ownership ambiguous and can prevent future reload callbacks.

## System server

No system-server path exists for this YouTube-only project.

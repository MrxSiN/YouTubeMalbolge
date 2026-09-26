# Vector/libxposed Lifecycle Mapping

## `onPackageReady`

Provides the final package ClassLoader and AppComponentFactory-ready state.

This is the canonical normal target-ready boundary for v4.

Responsibilities:

1. verify first/relevant YouTube package event;
2. capture final ClassLoader;
3. initialize the resolver identity and configuration snapshot;
4. validate cached descriptors or run bounded DexKit fallback;
5. bind and install each Malbolge-defined feature group transactionally;
6. close DexKit and report `ready` or `degraded`.

The generated entry uses no legacy Xposed API.

## `onHotReloading` / `onHotReloaded`

The old generation saves the target `ApplicationInfo` and `ClassLoader`, unregisters
RemotePreferences listeners, unhooks its handles, and disables its snapshot. The new
generation removes any remaining old handles and reruns the same resolver/install path.
Normal package callbacks are not assumed to replay.

## System server

No system-server path exists for this YouTube-only project.

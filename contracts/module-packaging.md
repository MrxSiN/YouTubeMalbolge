# Vector/libxposed Module Packaging Contract

These resources are generated at build time.

## Xposed metadata

Generated APK MUST contain:

```text
META-INF/xposed/java_init.list
META-INF/xposed/module.prop
META-INF/xposed/scope.list
```

Baseline MUST NOT generate `META-INF/xposed/native_init.list`.

## `module.prop`

Phase-0 hot-reload acceptance passed, so generated metadata is:

```properties
minApiVersion=102
targetApiVersion=102
staticScope=true
exceptionMode=protective
autoHotReload=true
```

## `java_init.list`

libxposed supports listing Java entries. v4 intentionally generates exactly one entry
to keep lifecycle/hot-reload ownership singular.

The generated entry:

- extends `io.github.libxposed.api.XposedModule`;
- exposes the required public no-arg constructor;
- is allowed to be optimized/obfuscated;
- is adapted in `java_init.list` after R8 renaming.

Official shrinker behavior to preserve:

```proguard
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
```

The backend emits equivalent rules; this text is a contract, not authored application
logic.

## `scope.list`

Exactly:

```text
com.google.android.youtube
```

No wildcard or broader static scope.

## Dependency packaging

```text
io.github.libxposed:api:102.0.0
```

is compile-only and must not be bundled as the framework implementation.

The manager may package:

```text
io.github.libxposed:service:102.0.0
```

for framework communication.

## Android component policy

Baseline:

- one normal manager Activity;
- no project background Service;
- no project ContentProvider;
- no project BroadcastReceiver;
- no runtime network requirement;
- no native library owned by the project.

Any added component requires a concrete requirement + ADR.

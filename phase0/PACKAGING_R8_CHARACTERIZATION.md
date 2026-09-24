# Packaging and R8 Characterization

## Generated Xposed resources

Release APK contract:

```text
META-INF/xposed/java_init.list   required
META-INF/xposed/module.prop      required
META-INF/xposed/scope.list       project-required static scope
```

Project baseline has no `native_init.list`.

## `module.prop`

Architecture values:

```properties
minApiVersion=102
targetApiVersion=102
staticScope=true
exceptionMode=protective
autoHotReload=false
```

`autoHotReload=true` is a post-acceptance release switch.

## Entry count

libxposed can represent multiple Java entries. v4 deliberately chooses exactly one
project entry to keep lifecycle/hot-reload ownership singular.

This is a KISS architecture rule, not a claim that libxposed forbids multiple entries.

## R8 contract

Backend-generated shrinker policy must semantically implement the official libxposed
guidance:

```proguard
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
```

The exact text may be generated, but behavior is fixed.

## API packaging

`io.github.libxposed:api:102.0.0` must not be bundled into the APK as a runtime
implementation.

Manager service dependency is separate from the compile-only module API boundary.

## Artifact tests

CI later verifies:

- one entry line after R8;
- entry class exists;
- public no-arg constructor exists;
- scope is exactly YouTube;
- module.prop values exact;
- no native entry;
- no bundled DexKit;
- no bundled project native runtime;
- no project source/provenance.

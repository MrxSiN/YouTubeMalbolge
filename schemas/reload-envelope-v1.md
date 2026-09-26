# ReloadEnvelope v1

API-102 `savedInstanceState` carries one `Object[2]`:

1. target `android.content.pm.ApplicationInfo`;
2. target `java.lang.ClassLoader`.

Project classes, hook handles, listeners, configuration snapshots, and feature state are
not transferred. Framework-provided old handles are cleanup input only. Invalid shape
unhooks old handles, disables behavior, and reports failure.

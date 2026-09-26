# Lifecycle Contract

```text
package ready
→ package guard
→ configuration snapshot
→ resolver-cache validation
→ narrow DexKit query if needed
→ structural fallback if needed
→ bind complete feature group
→ install group transactionally
→ READY | DEGRADED
```

An unchanged installed APK/split identity uses cached descriptors and does not create
DexKit. A changed identity clears the cache. Missing, invalid, below-threshold, or tied
candidates install no hook.

Groups are ordered by the Malbolge-authored startup class `EARLY_REQUIRED`, `NORMAL`,
then `LAZY_SAFE`. All members in one group bind before its first hook installs. A group
failure unhooks that group's already-installed handles and records `DEGRADED`; other
groups continue. Infrastructure failure before group isolation disables all snapshots
and reports `failed`.

Settings updates publish a new boolean snapshot through the RemotePreferences listener.
Hook topology is generation-lifetime. Before API-102 reload, the old entry saves only
`ApplicationInfo` and the target `ClassLoader`, unregisters its preference listener,
unhooks every project hook, closes DexKit, and disables its snapshot. The new entry
unhooks any framework-reported remnants, rebuilds configuration and bindings, then
installs one clean generation. Invalid state or failed binding remains fail-open.

This deliberately permits a short unhooked interval instead of relying on a nonexistent
multi-hook framework transaction. Target APK updates invalidate the descriptor cache by
version and base/split file identity; normal package restart then resolves the new build.

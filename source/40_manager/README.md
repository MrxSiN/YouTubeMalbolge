# Manager source area

Malbolge units implementing the manager semantics.

`manager_settings.mal` declares the `SettingsPage`: one YouTube settings entry
and the manager screen, which lists every ConfigItem and writes RemotePreferences
through libxposed service 102 (ADR-039). The companion Malbolge diagnostic
policy in `source/50_diag` governs the compatibility report and toggle gate.

# Configuration source area

`config_policy.mal` is an executable MBP1 policy program containing every storage
group, key, and boolean default. The generated runtime publishes immutable snapshots;
the MBP1 UI model exposes each key exactly once and writes RemotePreferences.

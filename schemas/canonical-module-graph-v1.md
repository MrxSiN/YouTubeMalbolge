# Canonical Module Graph v1

CMG is a canonical serialized graph containing typed nodes:

`Module`, `Function`, `Feature`, `Capability`, `Endpoint`, `Effect`, `BindingSpec`,
`TargetBinding`, `ConfigItem`, `RuntimeState`, `LifecycleHandler`, `DiagnosticEvent`,
`Component`, `Resource`.

Requirements:

- stable private semantic IDs;
- deterministic ordering/encoding;
- explicit references;
- no implicit source-order semantics;
- no physical YouTube names in Feature nodes;
- no Vector/DexKit objects in Feature nodes.

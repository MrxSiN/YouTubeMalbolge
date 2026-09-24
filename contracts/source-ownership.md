# Future Malbolge Source Ownership Map

Development source is authorized as of 2026-09-23. Open gates remain release gates.

Future ownership:

| Area | Sole responsibility |
|---|---|
| `00_boot` | generated entry semantics, exact target/process gates, process lifecycle requests |
| `10_vector` | thin API-102 adapter, Hook Controller, reload/config transport |
| `20_semantics` | Endpoint mapping, effect pipeline, binding materialization, admitted capabilities |
| `30_config` | config schema validation/migration/snapshot construction |
| `40_manager` | manager settings/status/diagnostics/reload-control semantics |
| `50_diag` | bounded diagnostic/circuit/export semantics |
| `60_packaging` | package/scope/layout/provenance semantic declarations |
| `70_features` | post-freeze product Feature/Effect units |
| `80_bindings` | post-target-selection build-only BindingSpecs |

An area's directory under `source/` is created with its first Malbolge unit;
`00_boot`, `10_vector` and `60_packaging` have none yet.

One source area cannot redefine another area's authoritative state/metadata.

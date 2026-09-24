# State Ownership Contract

| State | Owner | Mutability |
|---|---|---|
| process lifecycle | Process Coordinator | serialized state machine |
| target identity | Target Gate | immutable per process |
| TargetBindingSet | Binding Materializer | immutable |
| physical hook handles | Hook Controller | controlled mutation |
| runtime configuration | Config Publisher | immutable snapshots, atomic reference |
| feature-owned state | declared FeatureState owner | contract-specific |
| endpoint circuit state | Endpoint Runtime | bounded atomic state |
| diagnostics buffer | Diagnostic Sink | bounded |
| reload transfer envelope | Reload Coordinator | one-generation handoff |
| binding evidence/history | build laboratory | build-only/private |

No subsystem may mutate another owner's state by reaching through internal objects.

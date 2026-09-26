# Architecture Ownership

| Truth | Authority |
|---|---|
| Feature and hook behavior | executable raw Malbolge MBP1 programs |
| Configuration, UI model, diagnostics policy | executable raw Malbolge programs |
| Resolver fingerprints and scoring weights | raw Malbolge BindingSpecs and resolver policy |
| Physical runtime resolution | generic cached DexKit bridge |
| Hook install/remove | generated generic Hook Controller |
| Persistent configuration | libxposed RemotePreferences |
| Runtime configuration | generated immutable boolean snapshot |
| Generated JVM code | deterministic generic AOT compiler |
| ART hook mechanics | libxposed API 102 |
| Host implementation | external and untrusted |

`target/current` supplies regression fixtures and cheap exact-descriptor hints. It is not
a runtime version allowlist or semantic authority.

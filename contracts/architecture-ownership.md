# Architecture Ownership

| Truth | Single authority |
|---|---|
| Project executable behavior | Malbolge source |
| Semantic identity/relationships | Canonical Module Graph |
| Graph validity | independent semantic validator |
| Endpoint discovery criteria | Malbolge-originated BindingSpec |
| Current physical YouTube binding | Verified Target Binding Set |
| Supported runtime target | Target Release Manifest |
| Hook install/replace/remove | Hook Controller |
| Persistent configuration | manager/config store |
| Runtime configuration | immutable ConfigSnapshot |
| Mutable feature state | declared FeatureState owner |
| Release physical layout | Release Layout Plan |
| Source→artifact mapping | private provenance graph |
| ART hook mechanics | Vector/libxposed |
| Host implementation | YouTube, treated as external/untrusted |

No other component may independently redefine these truths.

# v3 → v4 Audited Subsystem Disposition

This is an architectural critique, not an assessment of whether v3 implementation
worked.

| v3 subsystem | Classification | v4 disposition |
|---|---|---|
| Malbolge-only executable authorship | KEEP | preserve; define exact frontend profile |
| no runtime Malbolge interpretation | KEEP | hard invariant |
| Canonical Semantic IR | KEEP BUT SIMPLIFY | one Canonical Module Graph + normalized review projection |
| Destructive behavioral Release IR | REDESIGN | representation-only Release Layout Plan |
| generated native control plane | REMOVE | unjustified duplicate runtime/control representation |
| custom startup plan VM | REMOVE | generated ordinary initialization is simpler |
| multi-representation executable output | REMOVE | one project runtime path: classfile→R8/D8→DEX |
| semantic feature/hook contracts | KEEP/REDESIGN | typed Semantic Endpoint ABI |
| numeric capabilities everywhere | KEEP BUT SIMPLIFY | sparse capability admission |
| runtime DexKit | REMOVE | DexKit is build/compatibility-lab only |
| resolver fallback strategies A/B/C | REMOVE | one authoritative BindingSpec; independent evidence only |
| weighted candidate scoring as acceptance | REDESIGN | hard validation + uniqueness; score may only prioritize analysis |
| exact-build compatibility cache | REMOVE | verified exact-target binding shipped with release |
| manager-side prewarming | REMOVE | manager no longer reverse-engineers YouTube |
| package-update compatibility daemon | REMOVE | release engineering owns target updates |
| exact build identity | KEEP/STRENGTHEN | cryptographic reference artifact set + code-bearing splits |
| process policy | KEEP/STRENGTHEN | explicit process allowlist in Target Release Manifest |
| one physical shared hook | KEEP | Hook Controller owns one target/phase hook |
| dispatcher callback list | REDESIGN | compile Endpoint effect pipeline directly |
| numeric priority bands | REDESIGN | semantic effect order; framework priority is not feature order |
| immutable configuration snapshot | KEEP/STRENGTHEN | canonical RemotePreferences → atomic ConfigSnapshot |
| restart-to-apply default | REDESIGN | hot config by default; restart only for declared lifecycle/epoch boundaries |
| module manager UI | KEEP BUT SIMPLIFY | config/status/diagnostics/reload control only |
| manager compatibility screen | KEEP BUT SIMPLIFY | show release target/binding status; do not resolve |
| compatibility matrix/history in runtime | REMOVE | latest-only; history private/test-only |
| Morphe behavior migration intake | DEFER | use only as feature research after architecture freeze |
| update-survival runtime model | REDESIGN | release-time rebind to new exact target, no runtime rediscovery |
| AI maintenance packet | KEEP BUT SIMPLIFY | CMG diff + contracts + private provenance + target evidence |
| source numeric IDs | KEEP BUT SIMPLIFY | stable private semantic IDs; physical ABI IDs generated separately |
| semantic diff | KEEP | normalized CMG/LMP diff |
| deterministic release diversification | KEEP BUT SIMPLIFY | representation-only; never HookAbiId within reload epoch |
| constant/anchor vaults | REDESIGN | no runtime resolver anchors; encode only shipped cold binding data |
| native ABI indirection | REMOVE | no project native control plane |
| cold-path control-flow distortion | DEFER/REMOVE BASELINE | rely on R8; custom post-R8 distortion prohibited baseline |
| private provenance graph | KEEP | mandatory, private, reproducible |
| bounded diagnostics ring | KEEP | opaque IDs, privacy contract, no hot logging |
| performance HOT/WARM/COLD | KEEP/STRENGTHEN | add no-Binder/no-project-JNI/no-member-lookup invariants |
| release seed/reproducibility record | KEEP | seed cannot influence behavioral semantics |
| debug/release personalities | KEEP BUT SIMPLIFY | same semantics; release representation differs |
| broad framework-hook prohibition | KEEP | explicit invariant |
| anti-debug/environment sabotage prohibition | KEEP | explicit invariant |
| fallback on ambiguity | KEEP AS PROHIBITION | ambiguity blocks release/fails closed |
| testing/release checklist | REDESIGN | dedicated architecture test matrix and freeze gates |

## Overall v3 evaluation

**Architectural score: 6.5 / 10.**

Strong decisions:

- build-time Malbolge, never runtime interpretation;
- semantic boundary between feature intent and obfuscated target members;
- fail-closed ambiguity;
- immutable runtime configuration;
- strong HOT-path restrictions;
- one shared physical hook concept;
- private provenance/reproducibility;
- explicit rejection of hostile anti-analysis.

Largest weaknesses:

- several subsystems existed mainly to increase static-analysis cost rather than solve
  runtime requirements;
- native control plane + custom plan VM + multi-representation output duplicated
  responsibilities Vector/ART/DEX already handle;
- runtime DexKit/cache/prewarming conflicted with the new latest-only target policy;
- multiple resolver strategies conflicted with the requested single canonical path;
- release transformation was allowed too close to behavioral semantics;
- lifecycle/hot-reload ownership was not designed around API 102;
- too many duplicated sources of metadata/compatibility truth.

Missing foundations now added in v4:

- exact Vector/API-102 packaging and hook ABI boundary;
- classloader-neutral reload state model;
- authoritative config transport;
- target-selection policy;
- one-resolver uniqueness model;
- canonical authority map;
- independent CMG validation;
- complete testing/validation architecture;
- hard architecture-freeze gates;
- R8 boundary and no-post-R8 baseline;
- explicit state ownership.

# Runtime binding contract

Each Endpoint has Malbolge-derived fingerprints plus a checked-in fallback descriptor.

Resolution tiers are: package/process gate; validated persistent cache; narrow DexKit query; structural fallback; fail open. Candidate weights and threshold come from `source/60_resolver/resolver_policy.mal`; per-member access, parameter count, superclass, descriptor shape, and opcode-count evidence comes from raw Malbolge binding units. A unique winner must pass hard reflection validation. Ties install nothing.

One DexKit bridge is shared for a cold resolution pass and closed after installation. Installed callbacks never access it. Cache hits never open DexKit. The fallback fixture is tried cheaply but does not restrict supported YouTube versions.

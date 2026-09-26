# Program composition

One endpoint has at most one physical libxposed hook. If multiple behaviors need that endpoint, their MBP1 operations are composed at build time with explicit order and stack compatibility. Mutating conflicts are rejected; registration order is never semantic policy.

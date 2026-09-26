# Trust Boundaries

1. Raw Malbolge is accepted only after pinned evaluation, MBX2 digest verification,
   MBP1 structural validation, bounds checks, and graph validation.
2. Generated review text is non-authoritative; only raw `.mal` participates in builds.
3. Checked-in target descriptors are hints and regression fixtures, never unchecked
   runtime authority.
4. Resolver cache entries are trusted only when the full installed-target/source identity
   matches and reflected descriptor shape, access, and hierarchy revalidate.
5. DexKit candidates require hard validation, minimum score, and a unique best score.
6. Android, libxposed, DexKit, HTTP, JSON, and YouTube are external mechanisms, not
   feature-semantic authorities.
7. Diagnostic broadcasts accept only the installed target package UID.
8. Obfuscation is not a secrecy boundary.

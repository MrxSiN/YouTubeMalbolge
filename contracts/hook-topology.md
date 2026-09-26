# Hook topology

Hook Controller solely owns libxposed handles. Programs declare EARLY_REQUIRED, NORMAL, or LAZY_SAFE startup class. At package-ready, groups install in that order; no current group is LAZY_SAFE.

Each physical executable has at most one module hook. Installation is idempotent. Binding completes before any handle in that group installs. Failure rolls back only that group's handles, records degraded status, and continues independent groups. Original app behavior proceeds.

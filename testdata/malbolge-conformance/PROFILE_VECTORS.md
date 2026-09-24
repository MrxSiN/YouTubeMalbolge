# MBX-CLASSIC-REF/1 Required Conformance Vectors

No executable project source is included here.

The future frontend acceptance suite must cover:

```text
valid source with whitespace normalization
invalid position-sensitive instruction
source > memory capacity
source < 2 logical words rejected by project profile
A/C/D zero initialization
memory fill using reference op
j instruction
i instruction
rotate instruction
p/crazy instruction
< output instruction
/ input instruction in reference-conformance mode
EOF => A=59048
v halt
xlat2 self-mutation
C wrap 59048→0
D wrap 59048→0
non-operation decoded runtime value
deterministic C-locale whitespace behavior
project semantic-unit rejection of /
instruction-budget exhaustion
output-budget exhaustion
two-host deterministic replay
```

Reference-conformance mode may exercise `/` solely to verify evaluator equivalence.
Project-authored semantic-generator mode rejects it.

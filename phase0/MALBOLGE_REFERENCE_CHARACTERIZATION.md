# MBX-CLASSIC-REF/1 — Malbolge Reference Characterization
## Phase-0 semantic freeze

**Status:** semantics defined; exact reference source vendored and hashed; evaluator still pending.

## 1. Normative authority

`MBX-CLASSIC-REF/1` follows the behavior of Ben Olmstead's original 1998 C interpreter.

When a prose description and the original interpreter disagree, **the interpreter wins**.

This is justified by:

- the original interpreter itself, which identifies Ben Olmstead and places the
  implementation in the public domain;
- later technical literature on Malbolge, which notes that the accompanying prose is
  incomplete and has a mismatch with the interpreter, and therefore follows the
  interpreter.

The project does not define a new Malbolge dialect merely to make compilation easier.

---

## 2. Machine model

Canonical machine:

```text
word width:          10 trits
word values:         0 .. 59048
memory words:        59049
registers:           A, C, D
initial A/C/D:       0
code + data memory:  shared
```

The frontend implementation may use wider host integers, but every Malbolge-visible
machine word is constrained to the reference value domain.

---

## 3. Source loading

The source loader follows reference behavior with deterministic environment constraints.

### Source character domain

Project source files are ASCII.

Whitespace accepted by the profile is the C-locale ASCII whitespace set:

```text
HT LF VT FF CR SPACE
```

Whitespace is discarded before logical source positions are assigned.

All non-whitespace project source bytes MUST be printable ASCII:

```text
33 .. 126
```

This intentionally excludes the reference implementation's accidental handling of
non-printable/non-ASCII file bytes. Such behavior is not needed by this project and is
not a supported source form.

### Position-sensitive source validity

For the logical non-whitespace source character `x` at position `i`:

```text
decoded = xlat1[(x - 33 + i) % 94]
```

The decoded character MUST be one of:

```text
j i * p < / v o
```

Otherwise the unit is invalid.

### Maximum program length

At most 59049 logical source words may be loaded.

The project will impose a much smaller toolchain unit-size budget before implementation,
but that is a build-resource limit rather than a language semantic change.

### Memory completion

After source load, remaining memory words are generated exactly by repeated reference
`op(previous_word, second_previous_word)` behavior.

The profile prohibits source units too short to initialize this operation safely.
Therefore project Malbolge units MUST contain at least two logical source words.

This removes the original C implementation's undefined one-word edge case from the
accepted project-source profile.

---

## 4. Runtime instruction semantics

Instruction dispatch uses:

```text
xlat1[(mem[C] - 33 + C) % 94]
```

when `mem[C]` is printable ASCII.

Instruction meanings follow the original interpreter:

```text
j   D = mem[D]
i   C = mem[D]
*   A = mem[D] = rotate-right-one-trit(mem[D])
p   A = mem[D] = op(A, mem[D])
<   output A
/   input one byte into A; EOF => 59048
v   halt
other decoded value => no semantic operation
```

The `<` and `/` directions follow the **reference interpreter**, even where historical
prose descriptions reverse them.

After the instruction body, the current instruction location is transformed through
`xlat2`, then `C` and `D` are incremented modulo 59049, matching reference behavior.

---

## 5. Crazy operation

The `op(x, y)` function is the original tritwise Malbolge operation.

The frontend conformance suite MUST contain exhaustive or mathematically complete
coverage proving equivalence for the entire reachable 10-trit input domain.

An implementation may optimize the operation but not alter its result.

---

## 6. Deterministic build-time I/O profile

Ambient stdin/stdout is not accepted as a build dependency.

### Input instruction

For project-authored semantic-generator units:

```text
the `/` input instruction is prohibited
```

Reason:

- project executable semantics should be self-contained Malbolge source;
- target bindings/configuration/build metadata enter the compiler/linker through typed
  external graph inputs, not hidden stdin;
- forbidding `/` removes nondeterministic/ambient build input;
- it keeps project behavior genuinely Malbolge-authored without a second behavioral
  configuration language feeding the program.

A future need for deterministic compile-time input would require an ADR and a revised
source profile version.

### Output instruction

`<` emits:

```text
A mod 256
```

as one output byte.

The emitted byte stream is interpreted only by the deterministic framed semantic-record
decoder.

No terminal/newline translation is permitted in the build frontend.

---

## 7. Deterministic environment

The reference semantics are reproduced in a host-independent evaluator.

Required environmental rules:

```text
locale:               fixed C locale
source encoding:      ASCII
newline normalization: frontend reads bytes; accepted CR/LF are whitespace
integer arithmetic:   exact project-defined unsigned arithmetic, not host UB
clock:                unavailable
randomness:           unavailable
filesystem discovery: unavailable to Malbolge unit
network:              unavailable
environment variables: unavailable
ambient stdin:        unavailable
```

The evaluator may be implemented in a conventional toolchain language; it is external
compiler infrastructure, not project behavior.

---

## 8. Execution budgets

Language semantics are not changed by build limits.

Before Gate A closes, the toolchain lock MUST pin:

```text
max source bytes / unit
max logical source words / unit
max executed instructions / unit
max output bytes / unit
max semantic records / unit
```

Budget exhaustion is a deterministic build failure.

No unit may run without an instruction budget.

---

## 9. Output protocol boundary

Malbolge output is not itself the CMG.

Pipeline:

```text
Malbolge output bytes
→ framed-record decoder
→ typed unit records
→ semantic linker
→ deterministic CBOR CMG
→ independent CMG validator
```

The framed decoder must reject:

- invalid magic/version;
- truncated frame;
- excessive count/length;
- unknown mandatory record type;
- duplicate export;
- invalid content digest;
- trailing data where prohibited.

This ensures malformed Malbolge output cannot become trusted semantic state.

---

## 10. Reference artifact provenance

Current documentary reference:

```text
Ben Olmstead original 1998 interpreter
public-domain declaration in source
archival/mirror source: TryItOnline/malbolge malbolge.c
```

The exact raw source checksum is pinned in `toolchain/LOCKFILE`:

```text
fe29a717f9f684d6cc81d5c63273d446d9c65fec73e62164538514d5737b07a6
```

The raw source is vendored at `toolchain/reference/malbolge.c`, acquired from
TryItOnline/malbolge commit `b08698709872be370c050a825f4f3c0b224e0be8`.
The evaluator binary remains unpinned.

A checksum derived from rendered HTML is explicitly unacceptable.

---

## 11. Conformance requirements

Before first executable project source:

1. vendor/pin the exact reference source;
2. record SHA-256;
3. build or independently implement the evaluator;
4. verify reference test programs;
5. verify source validity behavior;
6. verify full instruction semantics;
7. verify `xlat2` mutation;
8. verify register wrap behavior;
9. verify `op` equivalence;
10. verify EOF behavior;
11. verify project-profile rejection of `/`;
12. verify project-profile rejection of <2-word units;
13. verify deterministic replay on two clean hosts;
14. verify execution/output budget failures.

Only then may the first real `.mal`/`.mbg` unit be committed.

---

## 12. Architectural consequence

`MBX-CLASSIC-REF/1` is now precise enough to design against without pretending the
compiler already exists.

The remaining reference-source hash is a supply-chain pinning task, not a semantic
architecture ambiguity.

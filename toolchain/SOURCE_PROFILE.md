# MBX-CLASSIC-REF/1

`MBX-CLASSIC-REF/1` is the project's deterministic build-time Malbolge profile.

## Normative semantics

Semantic authority:

```text
Ben Olmstead original 1998 reference interpreter behavior
```

When historical prose and the interpreter differ, interpreter behavior is authoritative.

## Machine

```text
memory words = 59049
word range   = 0..59048
word width   = 10 trits
registers    = A/C/D
initial A/C/D = 0
```

## Project source restrictions

Project units:

- ASCII source only;
- C-locale ASCII whitespace only;
- at least two logical source words;
- position-valid source instructions only;
- `/` input instruction prohibited;
- deterministic output bytes only;
- no ambient/environmental inputs.

These restrictions create a reproducible **profile** of the reference language; they do
not introduce alternative instruction semantics.

## Loader/execution

Source validation, memory completion, dispatch, instruction self-mutation, register
wrapping, rotate, crazy operation, output behavior, halt behavior and EOF semantics must
match the original interpreter.

The historical input/output disagreement is resolved in favor of the original
interpreter:

```text
< = output
/ = input
```

Project units prohibit `/`, but conformance testing still verifies the reference
instruction semantics.

## Frontend isolation

Malbolge unit cannot observe:

```text
clock
random
network
filesystem discovery
environment variables
host locale
ambient stdin
release-layout seed
debug/release personality
```

## Output

```text
Malbolge output bytes
→ framed typed records
→ CMG fragments
```

The CMG is subsequently serialized with deterministic CBOR and independently validated.

## Reference hash

The raw reference source is vendored and hashed in `toolchain/LOCKFILE`.
`malbolge-reference-evaluator-sha256` remains `UNFROZEN` until a matching evaluator
artifact is built and verified. Rendered web content is not accepted as a hashing source.

See `phase0/MALBOLGE_REFERENCE_CHARACTERIZATION.md`.

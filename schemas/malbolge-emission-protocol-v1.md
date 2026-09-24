# Malbolge Emission Protocol v1

Canonical framed unit output is length-delimited and deterministic.

Logical fields:

```text
magic
protocol_version
unit_id
imports[]
exports[]
record_count
records[]
payload_sha256
```

The transport encoding itself is frozen together with the reference evaluator before
implementation. The resulting CMG is serialized canonically as deterministic CBOR.

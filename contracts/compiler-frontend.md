# Malbolge Frontend and Compiler Contract

## Source profile

`MBX-CLASSIC-REF/1` remains reserved until one exact reference evaluator artifact/hash is
pinned.

No project executable source may be committed before that Gate-A item closes.

## Deterministic evaluator inputs

Behavioral Malbolge units may receive only declared canonical inputs.

Forbidden implicit inputs:

- clock;
- randomness;
- filesystem enumeration;
- network;
- environment variables;
- host locale;
- machine identity;
- release layout seed.

## Unit protocol

Each unit evaluates independently and emits framed typed records with:

```text
format_version
unit_id
imports[]
exports[]
record_count
records[]
content_digest
```

Malformed frame or budget overflow is build failure.

## Canonical semantic representation

CMG is serialized with deterministic CBOR (RFC 8949 deterministic encoding).

Normalized JSON is generated for human/AI review only.

Independent validator reparses the serialized CMG bytes and must not trust frontend
in-memory objects.

## Single executable backend

```text
Validated LMP
→ generated JVM classfiles
→ AGP 9.4.1 build
→ R8/D8
→ DEX/APK
```

Pinned host tools:

```text
Gradle 9.6.0
JDK 17
compileSdk 37
targetSdk 37
Build Tools 36.0.0
```

No generated Java/Kotlin/Smali source is used.

No parallel direct-DEX project backend exists.

R8/D8 are the versions delivered by the pinned AGP build.

## Semantic review

Every behavior change produces:

- normalized CMG diff;
- Logical Module Plan diff;
- binding diff if target integration changes;
- release-layout diff only for physical representation changes.

# API-102 Hot Reload Device Probe Protocol

These are controlled framework-characterization experiments, not product hooks.

## Reference probe target

Use a dedicated harmless test package/process or a non-product test executable.

Do not use production YouTube feature logic for framework characterization.

Disable every other module scoped to the probe target before running HR probes.

## HR-01 callback order

Record sequence:

```text
old onModuleLoaded
old onPackageLoaded
old onPackageReady
old onHotReloading
new onHotReloaded
<any replayed callbacks?>
```

Acceptance:

- exact observed order archived;
- architecture remains correct whether normal callbacks replay or not.

## HR-02 saved state

Old generation stores primitives/string/Bundle-safe neutral values.

New generation verifies exact recovery.

Reject project-class instances.

## HR-03 hook identity

Install one hook with stable ID.

After update:

- enumerate old handles;
- verify ID is preserved/readable;
- replace;
- verify exactly one active hook.

## HR-04 replace atomicity

Run concurrent target invocations during replacement.

Accept only if each invocation observes either old or new single-hook implementation, not
an invalid half-state.

## HR-05 add/remove

Generation N:
`H1`

Generation N+1:
`H1 + H2`

Generation N+2:
`H2`

Verify no duplicate or zombie hook remains.

## HR-06 replacement failure

Deliberately make new generation unable to construct one replacement.

Determine:

- callback result;
- old hook state;
- process stability;
- whether a clean restart is required.

Architecture policy remains fail closed/restart required unless proven stronger.

## HR-07 multi-hook partial replacement

Two independent hooks H1/H2.

Cause H2 replacement to fail after H1 succeeds.

Record exact state.

This test determines whether any multi-hook feature may be `RELOAD_MIXED_SAFE`.

## HR-08 listener handoff

Old generation owns one RemotePreferences listener.

Reload repeatedly 20 times.

Acceptance:

```text
exactly one active listener
no duplicate update callbacks
```

## HR-09 classloader collection

After successful reload and GC pressure, verify old generation ClassLoader becomes
collectible.

Failure blocks automatic hot reload.

## HR-10 target mismatch on module update

Old generation target manifest matches.

New generation manifest deliberately does not.

Expected v4 behavior:

```text
new generation does not install product behavior
old incompatible behavioral hooks are retired where framework semantics allow
state = RESTART_REQUIRED / FAIL_CLOSED
```

Exact framework behavior is archived.

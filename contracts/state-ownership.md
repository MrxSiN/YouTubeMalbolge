# State Ownership Contract

| State | Owner |
|---|---|
| installed target identity and descriptor cache | generic RuntimeResolver |
| physical hook handles and failed-group set | generated HookController |
| runtime settings snapshot | generated ConfigSnapshot |
| AOT program object slots | generated StateSlots |
| asynchronous compact interval table | generated generic table bridge |
| diagnostic snapshot | module-app SharedPreferences receiver |

Hot callbacks read only generated in-memory state. They do not perform DexKit, network,
disk, JSON parsing, RemotePreferences access, or Malbolge interpretation.

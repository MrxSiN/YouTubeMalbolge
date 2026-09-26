# Third-party provenance

- **JingMatrix/Vector v2.2 (`88f8e1f`)** — external Xposed runtime framework target.
- **libxposed API 102.0.0 (`45e7c5c`)** — modern module API, compile-only boundary.
- **libxposed service 102.0.0** — manager/framework communication and RemotePreferences.
- **DexKit 2.3.0** — bounded runtime DEX resolution and compatibility-laboratory dependency.
- **Android R8/D8/AGP toolchain** — external compiler/optimizer/packaging infrastructure.

No third-party component is treated as a project-owned implementation language.
DexKit's packaged native libraries are used only during cold cache-miss resolution and are closed before hooks run.

# Target Release Manifest v1

```text
schema_version
package_name
release_channel
version_name
version_code
approved_signer_sha256[]
base_apk_identity
code_split_identities[
  split_name
  apk_sha256
  dex_entry_digests[]
]
binding_schema_version
verified_binding_set_digest
allowed_process_names[]
hot_reload_epoch
```

Rules:

- package must equal `com.google.android.youtube`;
- no version ranges;
- no wildcard signer;
- runtime compares the current (APK Signature Scheme v3) signing certificate; past
  rotation-lineage certificates match only when explicitly listed;
- no wildcard process;
- include all code-bearing split artifacts relevant to bindings;
- resource-only splits are identity-relevant only when a feature explicitly depends on
  their content;
- `hot_reload_epoch` changes when old/new Hook ABI or binding semantics cannot coexist.

# Release Layout Plan v1

Allowed fields control representation only:

```text
symbol_naming_seed
non_abi_id_map
table_order
constant_bank_layout
cold_data_encoding
class_grouping_hints
debug_metadata_policy
```

Forbidden: any operation that adds/removes/reorders semantic Effects or changes
configuration/lifecycle behavior.

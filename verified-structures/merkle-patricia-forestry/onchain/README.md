# onchain: CCL MPF proofs checked by Aiken

This Aiken project runs proofs produced by CCL's `MpfTrie` through the on-chain library
[`aiken-lang/merkle-patricia-forestry`](https://github.com/aiken-lang/merkle-patricia-forestry) 2.1.0
(see `aiken.toml` / `aiken.lock`).

- `lib/ccl_vectors.ak` is **generated** by `AikenVectorsTest` (MPF module tests). For each present key it checks
  `has`, `delete`, `update` and `insert` with the same proof (the insert recipe of the Aiken README); for each
  absent key it checks `miss` and `insert`. Vectors cover the fruits trie, the empty and single-leaf tries, seeded
  random tries and proofs that end in a `Fork` step.
- `validators/mpf_validator.ak` is a small hand-written `has` example.

## Running

```sh
# from the repository root: fail if the generated file is stale
./gradlew :verified-structures:merkle-patricia-forestry:test --tests '*AikenVectorsTest*'

# regenerate after an intended change in proof generation
AIKEN_VECTORS_REGENERATE=1 ./gradlew :verified-structures:merkle-patricia-forestry:test --tests '*AikenVectorsTest*'

# run every vector through Aiken (aiken v1.1.23)
cd verified-structures/merkle-patricia-forestry/onchain && aiken check
```

CI runs both steps in the `mpf-aiken` job of `.github/workflows/build.yml`.

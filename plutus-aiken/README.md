## plutus-aiken

Aiken-aware companion to the CCL blueprint annotation processor. Ships
prebuilt Java representations of the CIP-57 schemas emitted by Aiken's
standard library, plus a `BlueprintTypeRegistry` that the annotation
processor discovers via Java's `ServiceLoader` to resolve those schemas to
the prebuilt classes (avoiding duplicate generated copies across blueprints).

### Supported Aiken stdlib

Targets the modern Aiken standard library: **stdlib v3.x — verified against
3.0 and 3.1**. The v3.1.0 release adds two new types (`InsertStrategy`
and `Fold2`), but both are Aiken function-type aliases
(`fn(...) -> ...`) — function types are not serializable to PlutusData
and therefore never appear in CIP-57 blueprint definitions, so the
v3.0 shared-type schemas registered by `AikenBlueprintTypeRegistry`
cover v3.1 blueprints unchanged.

Older stdlib versions (v1 / v2) are not supported. Blueprints compiled
against them must be re-emitted with a modern Aiken compiler before this
processor will resolve their shared types.

### Module contents

- `com.bloxbean.cardano.client.plutus.aiken.blueprint.std` — prebuilt Java
  types for Aiken stdlib v3.x shared schemas: `Address`, `PaymentCredential`,
  `StakeCredential`, `OutputReference`, `IntervalBound`, `IntervalBoundType`,
  `ValidityRange`, and `ByteArrayWrapper`-based hash types
  (`VerificationKeyHash`, `ScriptHash`, `PolicyId`, `AssetName`, `Hash`,
  `DataHash`, `Signature`, `Script`, `VerificationKey`).
- `com.bloxbean.cardano.client.plutus.aiken.annotation` —
  `@AikenStdlib` annotation and `AikenStdlibVersion` enum (currently
  `V3` only; kept as a source-compat marker and extension point).
- `com.bloxbean.cardano.client.plutus.aiken.blueprint.registry` —
  `AikenBlueprintTypeRegistry`, registered via `META-INF/services`.

### Further reading

The user-facing guide lives next door:
[`annotation-processor/docs/04-shared-types-and-plutus-aiken.md`](../annotation-processor/docs/04-shared-types-and-plutus-aiken.md).

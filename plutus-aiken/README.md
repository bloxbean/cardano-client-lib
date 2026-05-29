## plutus-aiken

Aiken-aware companion to the CCL blueprint annotation processor. Ships
prebuilt Java representations of the CIP-57 schemas emitted by Aiken's
standard library, plus a `BlueprintTypeRegistry` that the annotation
processor discovers via Java's `ServiceLoader` to resolve those schemas to
the prebuilt classes (avoiding duplicate generated copies across blueprints).

### Supported Aiken stdlib

Targets the modern Aiken standard library: **stdlib v3.x — verified
against 3.0 and 3.1** (no new shared types in 3.1).

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
  `@AikenStdlib` annotation and `AikenStdlibVersion` enum. Source-compat
  marker only (no-op for the processor); kept as an extension point for
  future stdlib versions.
- `com.bloxbean.cardano.client.plutus.aiken.blueprint.registry` —
  `AikenBlueprintTypeRegistry`, registered via `META-INF/services`.

### Further reading

The user-facing guide lives next door:
[`annotation-processor/docs/04-shared-types-and-plutus-aiken.md`](../annotation-processor/docs/04-shared-types-and-plutus-aiken.md).

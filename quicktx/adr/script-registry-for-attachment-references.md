# Script Registry for Attachment References

**Status**: Proposed
**Date**: 2026-06-17
**Issue**: TBD
**Modules**: `quicktx`

## 1. Context

QuickTx YAML can currently keep signers and policies out of documents through runtime registries such as `SignerRegistry`.

Script witness material is different. Several QuickTx intents still require script bytes in YAML or runtime script objects:

- `ScriptValidatorAttachmentIntent` requires a runtime `PlutusScript` or YAML `cbor_hex` plus `version`.
- `NativeScriptAttachmentIntent` requires a runtime `NativeScript` or YAML `script_hex`.
- `PaymentIntent` can embed output reference script bytes through `script_ref_bytes`.

This creates large YAML documents and forces tools to serialize script bytes even when applications already hold scripts at runtime.

There is also an existing `ScriptSupplier`, but it serves a narrower purpose:

- It resolves Plutus scripts by hash for on-chain reference-script handling.
- It is hash-oriented, not logical-name-oriented.
- It returns only `PlutusScript`, not native scripts.
- It is used after reference inputs or inputs expose a `referenceScriptHash`.

Therefore, `ScriptSupplier` should not become the only user-facing runtime script abstraction for TxPlan YAML.

## 2. Decision

Add a QuickTx `ScriptRegistry` for runtime script material and allow script attachment intents to resolve scripts using either:

- `script_ref`: a logical runtime reference URI, such as `validator://mint-nft`;
- `script_hash`: the content hash of the script.

Example:

```yaml
version: "1.0"
transaction:
  - tx:
      from_ref: account://alice
      scripts:
        - type: validator
          role: mint
          script_ref: validator://mint-nft
        - type: native_script
          script_hash: f711cc44f1611e6784f13ca21a0863ed2923d065d4493ef24246ea46
```

The term `script_ref` in this ADR means a runtime registry reference, not a Cardano on-chain reference script. On-chain reference scripts remain represented by reference inputs, `script_ref_bytes`, `referenceScriptHash`, and the existing `ScriptSupplier` / `ReferenceScriptResolver` flow.

## 3. API Changes

Add a QuickTx-level registry:

```java
public interface ScriptRegistry {
    Optional<Script> resolve(String ref);

    default Optional<Script> resolveByHash(String scriptHash) {
        return Optional.empty();
    }
}
```

Add a default implementation:

```java
public class DefaultScriptRegistry implements ScriptRegistry {
    public DefaultScriptRegistry addScript(String ref, Script script);
    public DefaultScriptRegistry addPlutusScript(String ref, PlutusScript script);
    public DefaultScriptRegistry addNativeScript(String ref, NativeScript script);
    public DefaultScriptRegistry withScriptSupplier(ScriptSupplier scriptSupplier);
}
```

`DefaultScriptRegistry` indexes scripts by:

- explicit `ref`;
- computed script hash, using `Script.getPolicyId()` / `Script.getScriptHash()`.

If a `ScriptSupplier` is configured, `resolveByHash(scriptHash)` may fall back to `scriptSupplier.getScript(scriptHash)`. Because `ScriptSupplier` returns only `PlutusScript`, this fallback only supports Plutus scripts. Native scripts must be present in the registry unless a future generalized supplier is introduced.

Add configuration to `QuickTxBuilder.TxContext`:

```java
TxContext withScriptRegistry(ScriptRegistry registry)
```

Optionally add convenience composition overloads:

```java
TxContext compose(TxPlan plan, SignerRegistry signerRegistry, ScriptRegistry scriptRegistry)
```

## 4. YAML Field Changes

Add `script_ref` and `script_hash` to `ScriptValidatorAttachmentIntent`:

```yaml
scripts:
  - type: validator
    role: spend
    script_ref: validator://spend
```

```yaml
scripts:
  - type: validator
    role: mint
    script_hash: 5c17ca2cb0ed76c8c6345f7db447d1c8f260e032cd2fb8267a17aa3e
```

Add `script_ref` and `script_hash` to `NativeScriptAttachmentIntent`:

```yaml
scripts:
  - type: native_script
    script_ref: native://policy
```

```yaml
scripts:
  - type: native_script
    script_hash: f711cc44f1611e6784f13ca21a0863ed2923d065d4493ef24246ea46
```

## 5. Resolution Flow

Resolution should happen in `QuickTxBuilder.TxContext._build()` after the effective `ScriptRegistry` is known and before `tx.verifyData()` / `tx.complete()`.

For each `ScriptValidatorAttachmentIntent` or `NativeScriptAttachmentIntent`:

1. Resolve `${...}` variables in `script_ref` or `script_hash` through the normal TxPlan variable path.
2. If neither field exists, keep current runtime-script or embedded-script behavior.
3. If either field exists, require a configured `ScriptRegistry`; otherwise throw `TxBuildException`.
4. Resolve by `script_ref` or `script_hash`.
5. For hash resolution, verify the resolved script's computed hash exactly equals the requested hash.
6. Require the expected script kind:
   - validator attachment requires `PlutusScript`;
   - native script attachment requires `NativeScript`.
7. Inject the resolved runtime script into the intent before validation and build execution.

YAML loading must remain pure. `TxPlan.from(yaml)` should not require a registry, backend, or script supplier.

## 6. Validation Rules

Each attachment intent must use exactly one script source.

For `ScriptValidatorAttachmentIntent`:

- runtime `PlutusScript`;
- `cbor_hex` plus `version`;
- `script_ref`;
- `script_hash`.

For `NativeScriptAttachmentIntent`:

- runtime `NativeScript`;
- `script_hex`;
- `script_ref`;
- `script_hash`.

Fail fast for:

- blank `script_ref`;
- blank or non-hex `script_hash`;
- missing `ScriptRegistry`;
- unresolved `script_ref`;
- unresolved `script_hash`;
- hash mismatch after resolution;
- wrong script kind;
- ambiguous combinations such as `script_ref` plus `cbor_hex`, or `script_hash` plus `script_hex`.

## 7. Serialization Rules

When an intent was created with `script_ref`, `toYaml()` should preserve `script_ref` and should not emit derived `cbor_hex`, `version`, or `script_hex`.

When an intent was created with `script_hash`, `toYaml()` should preserve `script_hash` and should not emit derived script bytes.

When an intent was created from a runtime script without a ref or hash, keep existing behavior and emit script bytes.

When an intent was created from embedded YAML script bytes, keep existing behavior and round-trip those fields.

Do not serialize any private keys or signing material.

## 8. Relationship to ScriptSupplier

`ScriptSupplier` should remain the low-level API for resolving Plutus scripts by script hash, especially for on-chain reference-script workflows.

`ScriptRegistry` may use `ScriptSupplier` internally as a fallback for `script_hash` lookup:

```java
ScriptRegistry registry = new DefaultScriptRegistry()
    .addScript("validator://mint", localMintScript)
    .withScriptSupplier(scriptSupplier);
```

This keeps YAML and Java APIs consistent while preserving existing backend-driven reference-script behavior.

When `QuickTxBuilder` has no explicit `ScriptRegistry` but does have a context-level or backend-level `ScriptSupplier`, it may treat that supplier as an implicit hash-only registry for `script_hash` lookups. This implicit path only supports Plutus scripts because `ScriptSupplier` is Plutus-only. Logical `script_ref` values still require an explicit `ScriptRegistry`; with only a supplier configured, unresolved logical refs fail as unresolved refs rather than as a missing-registry error.

Do not change `ScriptSupplier` in the first implementation. It is Plutus-only today and changing its return type would be a broader public API change.

## 9. Scope

Initial implementation:

- Add a typed script reference value:

```java
ScriptRef.ref(String ref)
ScriptRef.hash(String scriptHash)
```

- Add `ScriptRegistry` and `DefaultScriptRegistry`.
- Add `script_ref` and `script_hash` to `ScriptValidatorAttachmentIntent`.
- Add `script_ref` and `script_hash` to `NativeScriptAttachmentIntent`.
- Add `QuickTxBuilder.TxContext.withScriptRegistry(...)`.
- Add typed Java overloads on `Tx`, for example:
  - `attachSpendingValidator(ScriptRef scriptRef)`;
  - `attachMintValidator(ScriptRef scriptRef)`;
  - `attachCertificateValidator(ScriptRef scriptRef)`;
  - `attachRewardValidator(ScriptRef scriptRef)`;
  - `attachProposingValidator(ScriptRef scriptRef)`;
  - `attachVotingValidator(ScriptRef scriptRef)`;
  - `attachNativeScript(ScriptRef scriptRef)`.

Use explicit factories instead of `ScriptRef.of(String)` so callers must choose whether the value is a logical reference or a script hash. Although `ScriptRef` overlaps with Cardano reference-script terminology, the explicit `ref(...)` and `hash(...)` factories make the Java call sites clear.

Out of scope for the first implementation:

- Direct `script_ref` or `script_hash` on `ScriptMintingIntent`.
- Direct refs on script spend, staking, or governance intents.
- A generalized backend script supplier that supports both native and Plutus scripts.
- Changing existing on-chain reference-script resolution behavior.

## 10. Future Work

A later ADR can introduce intent-level script references for Plutus operations, such as:

- `mint_validator_ref` / `mint_validator_hash` on `ScriptMintingIntent`;
- `spending_validator_ref` on script collection intents;
- certificate, reward, proposal, and voting validator refs.

That design must cover policy id derivation, witness attachment, reference inputs, redeemer defaults, execution units, and script versioning explicitly.

## 11. Tests

Add focused unit tests for:

- YAML parse of validator attachment with `script_ref`;
- YAML parse of validator attachment with `script_hash`;
- YAML parse of native script attachment with `script_ref`;
- YAML parse of native script attachment with `script_hash`;
- TxPlan round-trip preserves refs and hashes without serializing script bytes;
- build resolves Plutus validator refs and attaches witness scripts;
- build resolves native script refs and attaches witness scripts;
- `script_hash` resolution verifies hash equality;
- `script_hash` falls back to `ScriptSupplier` for Plutus scripts;
- missing registry fails;
- unknown ref/hash fails;
- wrong script kind fails;
- ambiguous script sources fail;
- existing embedded script YAML still works.

## 12. Consequences

Positive:

- TxPlan YAML can avoid embedded script bytes for common runtime-managed scripts.
- Users can choose stable logical names (`script_ref`) or content-addressed lookup (`script_hash`).
- Existing `ScriptSupplier` remains useful without becoming the primary YAML registry abstraction.
- Native and Plutus attachment workflows get consistent ref-based ergonomics.

Tradeoffs:

- QuickTx gains another registry abstraction.
- `script_ref` needs careful documentation because Cardano already has on-chain reference scripts.
- `ScriptSupplier` fallback is Plutus-only until a broader script-supplier API exists.

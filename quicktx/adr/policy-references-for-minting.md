# Policy References for Minting

**Status**: Proposed
**Date**: 2026-06-17
**Issue**: https://github.com/bloxbean/cardano-client-lib/issues/630
**Modules**: `quicktx`, `transaction-spec`

## 1. Context

QuickTx supports URI-style references through `SignerRegistry` so YAML documents can refer to runtime-held accounts, wallets, and policies without embedding secrets:

```java
SignerRegistry registry = new DefaultSignerRegistry()
    .addAccount("account://alice", alice)
    .addPolicy("policy://nft", policy);
```

The account and wallet references are usable from TxPlan YAML through fields such as `from_ref`, `fee_payer_ref`, `collateral_payer_ref`, and `context.signers`.

Policy references are only partially usable today. A policy binding can create a policy signer through `signerFor("policy")`, but minting intents cannot use the same reference to obtain the native policy script or policy id. Native minting YAML must still embed `script_hex` and `script_type`, then separately add a policy signer ref in `context.signers`.

Current behavior:

- `MintingIntent` requires a runtime `Script` or YAML fields `script_hex` plus `script_type`.
- `ScriptMintingIntent` carries `policyId`, assets, redeemer, and optional receiver/output datum. If not using a reference script path, the mint validator must be attached separately through `ScriptValidatorAttachmentIntent`.
- `DefaultSignerRegistry.addPolicy(...)` stores a `Policy`, but `SignerBinding` exposes only `signerFor(...)`, `asWallet()`, and `preferredAddress()`.

This makes minting asymmetric with the clean ref-based payment flow. A YAML tool can keep payment keys out of YAML, but native minting still needs policy script bytes duplicated in the document even when runtime already registered `policy://nft`.

## 2. Decision

Add `policy_ref` support to native `MintingIntent`.

```yaml
version: "1.0"
transaction:
  - tx:
      from_ref: account://minter
      intents:
        - type: minting
          policy_ref: policy://nft
          assets:
            - name: ExampleToken
              value: 1
          receiver: addr_test1...
```

When `QuickTxBuilder` composes a `TxPlan` with a `SignerRegistry`, `policy_ref` resolves to a registered `Policy`. The resolved policy supplies:

- the native policy script used by `MintingIntent`;
- the policy id derived from that script;
- the policy signer added to the transaction context using `SignerScopes.POLICY`.

YAML authors should not need to repeat:

```yaml
context:
  signers:
    - ref: policy://nft
      scope: policy
```

for the common native minting case. Explicit signer refs remain allowed, and duplicate policy signers should be de-duplicated or otherwise harmless.

## 3. API Changes

Extend `SignerBinding` with a backward-compatible default method:

```java
default Optional<Policy> asPolicy() {
    return Optional.empty();
}
```

`BasicSignerBinding.fromPolicy(policy)` returns the registered policy from `asPolicy()`.

This avoids a second registry abstraction for the immediate native-policy use case and does not break custom `SignerBinding` implementations. A custom registry that wants to support `policy_ref` must return a binding whose `asPolicy()` is present and whose `signerFor(SignerScopes.POLICY)` returns the policy signer.

Add `policyRef` to `MintingIntent`:

```java
@JsonProperty("policy_ref")
private String policyRef;
```

Add fluent Java helpers, for example:

```java
Tx mintAssetRef(String policyRef, Asset asset, String receiver)
Tx mintAssetRef(String policyRef, List<Asset> assets, String receiver)
```

Exact overload naming can follow local API style, but it should be clear that the policy is resolved at composition/build time.

## 4. Resolution Flow

Resolution should happen in `QuickTxBuilder.TxContext._build()` after the effective `SignerRegistry` is known and before `tx.verifyData()` / `tx.complete()` applies intents.

For each `MintingIntent` with `policy_ref`:

1. Resolve `${...}` variables in `policy_ref` through the normal TxPlan variable path.
2. Require a configured `SignerRegistry`; otherwise throw `TxBuildException`.
3. Resolve the ref; missing refs throw `TxBuildException`.
4. Require `binding.asPolicy()`; bindings without policy material throw `TxBuildException`.
5. Set the intent's runtime script from `policy.getPolicyScript()`.
6. Add `binding.signerFor(SignerScopes.POLICY)` to the TxContext signer set once per unique policy ref.

The resolver should not parse or require the registry during `TxPlan.from(yaml)`. Loading YAML should remain a pure document operation; missing runtime refs should fail only when the transaction is built.

## 5. Validation Rules

For native `MintingIntent`, exactly one policy source should be used:

- runtime `script`;
- `script_hex` plus `script_type`;
- `policy_ref`.

If `policy_ref` appears together with `script_hex`, `script_type`, or a runtime script, fail fast with a clear ambiguity error.

`policy_ref` must not be blank after variable resolution.

If a policy ref resolves to a binding that can sign as `policy` but cannot expose `asPolicy()`, fail with a message that the policy script is unavailable. A signer alone is not enough for native minting because the native script must be present in the transaction witness set.

## 6. Serialization Rules

When an intent was created with a `policy_ref`, `toYaml()` should preserve `policy_ref` and should not emit derived `script_hex` / `script_type`.

When an intent was created from a runtime `Script` without a ref, keep existing behavior and emit `script_hex` / `script_type`.

When an intent was created from YAML with `script_hex` / `script_type`, keep existing behavior and round-trip those fields.

Do not serialize policy keys. `policy_ref` is intentionally a runtime binding, not a secret-export mechanism.

## 7. ScriptMintingIntent Scope

Do not add `policy_ref` to `ScriptMintingIntent` in the first implementation.

Reasoning:

- `DefaultSignerRegistry.addPolicy(...)` stores `transaction.spec.Policy`, which is a native-script policy object.
- Plutus minting uses a `PlutusScript`, redeemer, execution units, and possibly reference inputs. A native `Policy` binding cannot supply those semantics.
- Existing `ScriptMintingIntent` supports a policy-id/reference-script path, and non-reference-script usage already needs a separate validator attachment intent.

A future ADR can introduce a generalized script reference model, such as `validator_ref`, `mint_validator_ref`, or a `ScriptRegistry`, for Plutus minting. That design should cover witness attachment, reference-script inputs, redeemer/ex-unit defaults, and script versioning explicitly instead of overloading native `policy_ref`.

## 8. Alternatives Considered

### Require `context.signers` Plus `script_hex`

This is the current behavior. It works, but it duplicates public policy script bytes in YAML and forces tools to know how to serialize native scripts even when runtime already has the `Policy`.

Decision: reject as the only option. Keep it for backward compatibility.

### Add A Separate `PolicyRegistry`

A dedicated registry would avoid expanding `SignerBinding`, but it would force applications to wire two registries for one concept: the same `policy://nft` reference must provide both script material and signing capability.

Decision: reject for native policy refs. Add `SignerBinding.asPolicy()` as the minimal compatible extension.

### Add `policy_ref` To Both Minting Intent Types Now

This would satisfy the broadest reading of the issue, but it hides important differences between native policy minting and Plutus minting. For Plutus scripts, the policy id alone is not enough, and a `Policy` object is the wrong carrier.

Decision: reject for the initial change. Native minting gets `policy_ref`; Plutus script references require a separate design.

## 9. Implementation Plan

1. Extend `SignerBinding`.
   - Add default `asPolicy()`.
   - Return `Optional.of(policy)` from `BasicSignerBinding` when backed by a `Policy`.

2. Extend `MintingIntent`.
   - Add `policyRef` with YAML property `policy_ref`.
   - Accept `policy_ref` in validation as a valid policy source.
   - Reject ambiguous combinations with script fields.
   - Resolve variables for `policy_ref`.
   - Preserve `policy_ref` in serialization.

3. Resolve refs in `QuickTxBuilder`.
   - Detect `MintingIntent` instances with `policyRef`.
   - Resolve through the effective registry.
   - Inject `policy.getPolicyScript()` into the intent before `tx.complete()`.
   - Add the policy signer once per unique ref.
   - Fail clearly when registry, binding, policy, script, or signer is missing.

4. Add Java API helpers.
   - Add fluent methods on `Tx` for policy-ref native minting.
   - Keep `ScriptTx` deprecated; do not expand deprecated APIs unless needed for binary/source compatibility.

5. Add tests.
   - YAML parse of native minting with `policy_ref` and no `script_hex`.
   - TxPlan round-trip preserves `policy_ref`.
   - Build with registry resolves policy script and signer.
   - Build without registry fails.
   - Unknown policy ref fails.
   - Binding with signer but no `asPolicy()` fails.
   - `policy_ref` plus `script_hex` / `script_type` fails.
   - Variable resolution in `policy_ref`.
   - Duplicate refs do not add duplicate signers or produce duplicate witness issues.

6. Update docs.
   - Add a TxPlan YAML example for native minting with `policy_ref`.
   - Explain that `policy_ref` is for native `Policy`-backed minting.
   - Point Plutus minting users to validator attachment or reference-script workflows until a script-reference ADR exists.

## 10. Consequences

Positive:

- Native minting YAML can stay secret-free and avoid embedded policy script hex.
- `DefaultSignerRegistry.addPolicy(...)` becomes useful for complete YAML minting, not only signer lookup.
- YAML tools can express "mint under `policy://nft`" end to end.
- Existing `script_hex` / `script_type` YAML remains valid.

Tradeoffs:

- `SignerBinding` gains one policy-specific method.
- `QuickTxBuilder` must resolve intent-level refs, not only context-level refs.
- Native and Plutus minting ref support will be intentionally asymmetric until the Plutus script-reference design is added.

Out of scope:

- Plutus `ScriptMintingIntent` policy/script references.
- Exporting or serializing policy keys.
- Changing existing `context.signers` behavior.

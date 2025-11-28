---
title: "QuickTx API"
description: "Declarative transaction building API"
sidebar_position: 1
---

# QuickTx API

QuickTx is a declarative transaction builder layered on top of the Composable Functions API. It keeps common flows concise while still letting you plug in custom UTXO selection, evaluators, script suppliers, and verifiers.

Use QuickTx when you want the happy path to be short and readable, but still need escape hatches for unusual inputs (custom UTXO selection), script fee evaluation, or reference scripts. Behind the scenes, QuickTx constructs a composable-function chain for you, applies balancing/fees, signs, and submits—while exposing hooks to override key steps.

## Key Features

- Declarative, readable transaction description
- Payments, NativeScript mint/burn, Plutus script spending/minting, staking, governance
- Compose multiple `Tx`/`ScriptTx` into one transaction
- Per-tx overrides for UTXO selection, reference scripts, serialization era, and verifiers

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-quicktx
- **Depends on**: core, core-api, backend

## Constructing the Builder

```java
// Common: derive all dependencies from the backend
BackendService backendService = ...;
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

// Advanced: supply dependencies manually
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
ProtocolParamsSupplier paramsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
TransactionProcessor txProcessor = new DefaultTransactionProcessor(backendService.getTransactionService());
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, paramsSupplier, txProcessor);
```

## Usage Examples

### Simple Payment

```java
Account sender = ...;
String receiver1 = "...";
String receiver2 = "...";

Tx tx = new Tx()
        .payToAddress(receiver1, Amount.ada(1.5))
        .payToAddress(receiver2, Amount.ada(2.0))
        .from(sender.baseAddress());

TxResult result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait(msg -> log.info(msg));
```

### Native Token Minting (NativeScript)

```java
NativeScript policy = ...; // NativeScript policy
Asset asset = Asset.builder()
        .name("MyToken")
        .value(BigInteger.valueOf(1_000))
        .build();

Tx tx = new Tx()
        .mintAssets(policy, asset, receiverAddress)
        .from(sender.baseAddress());

TxResult result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .complete();
```

### Plutus Minting

```java
PlutusScript mintingScript = ...;
Asset asset = Asset.builder()
        .name("MyScriptToken")
        .value(BigInteger.valueOf(100))
        .build();

ScriptTx tx = new ScriptTx()
        .mintAsset(mintingScript, asset, PlutusData.unit(), receiverAddress)
        .from(sender.baseAddress());

TxResult result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .complete();
```

### Staking

```java
String stakeAddress = "..."; // base or reward address
String poolId = "...";

Tx delegateTx = new Tx()
        .delegateTo(stakeAddress, poolId)
        .from(sender.baseAddress());

Tx withdrawTx = new Tx()
        .withdraw(stakeAddress, BigInteger.valueOf(1_000_000)) // in lovelace
        .from(sender.baseAddress());
```

### Metadata

```java
Metadata metadata = MessageMetadata.create()
        .add("Transaction message");

Tx tx = new Tx()
        .payToAddress(receiver1, Amount.ada(1.0))
        .payToAddress(receiver2, Amount.ada(2.0))
        .attachMetadata(metadata)
        .from(sender.baseAddress());

TxResult result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait();
```

## API Reference (Essentials)

### Tx (native transactions)

- Payments: `payToAddress(...)`, `payToContract(...)` (datum hash/inline datum, reference scripts)
- Native mint/burn: `mintAssets(NativeScript policy, List<Asset> assets [, String receiver])`
- Staking: `delegateTo(String stakeAddress, String poolId)`, `withdraw(String rewardAddress, BigInteger amount [, String receiver])`
- Metadata: `attachMetadata(Metadata metadata)`
- Sender: `from(String address | Wallet wallet)`; change via `withChangeAddress(String)`
- Governance and other intents: stake registration/deregistration, pool registration/update/retire, governance actions (DRep/committee registration, votes), treasury donation, and more (see `Tx` methods)

### ScriptTx (Plutus-aware)

- Script inputs: `collectFrom(...)` variants with redeemers/datums
- Plutus minting: `mintAsset(PlutusScript script, List<Asset> assets, PlutusData redeemer [, String receiver, PlutusData outputDatum])`
- Attach validators: `attachSpendingValidator / attachMintValidator / ...`
- Change with inline datum: `withChangeAddress(String, PlutusData)`

### QuickTxBuilder.compose(...)

`compose(AbstractTx... txs)` returns a `TxContext`:

```java
TxContext ctx = quickTxBuilder.compose(tx1, tx2)
        .withSigner(SignerProviders.signerFrom(sender))
        .withUtxoSelectionStrategy(new LargestFirstUtxoSelectionStrategy(utxoSupplier))
        .validFrom(slotFrom)
        .validTo(slotTo);

Transaction unsigned = ctx.build();
Transaction signed   = ctx.buildAndSign();
TxResult submitted   = ctx.complete();
TxResult confirmed   = ctx.completeAndWait(); // returns TxStatus (SUBMITTED/CONFIRMED/TIMEOUT/FAILED)
```

You can also rebuild from a serialized plan (`TxPlan`) via `compose(TxPlan plan [, SignerRegistry registry])` to replay stored transactions with context (fee payer, validity, signers, etc.).

Execution flow:
- Build one or more `Tx`/`ScriptTx` describing intent.
- `compose(...)` wraps them in a `TxContext`.
- Add signers, optional strategy/evaluator/era/reference scripts.
- Call `build`/`buildAndSign`/`complete`/`completeAndWait`.

### Balancing, Fees, and Collateral

- Fees and change are calculated during `build/complete`
- Script cost evaluation is performed automatically; failures can be tolerated with `ignoreScriptCostEvaluationError(true)`
- Collateral is auto-selected for script transactions unless you set explicit inputs via `withCollateralInputs(...)`

### Customization

- UTXO selection: `withUtxoSelectionStrategy(...)`
- Reference scripts for fee accuracy: `withReferenceScripts(...)`
- Serialization era: `withSerializationEra(Era era)` (Conway by default)
- Verifiers and inspection: `withVerifier(...)`, `withTxInspector(...)`

### Additional Context Controls

- Fee payer and collateral payer: `feePayer(...)`, `collateralPayer(...)` (or wallet variants)
- Required signers: `withRequiredSigners(Address... | byte[]...)`
- Validity window: `validFrom(slot)`, `validTo(slot)`
- Merge outputs: `mergeOutputs(boolean)` (deduplicate outputs to same address)
- Script cost tolerance: `ignoreScriptCostEvaluationError(boolean)`
- Duplicate witness cleanup: `removeDuplicateScriptWitnesses(boolean)` for multi-asset/script-heavy txs
- UTXO search mode: `withSearchUtxoByAddressVkh(boolean)` when backend supports addr_vkh lookups
- Custom transforms: `preBalanceTx(...)`, `postBalanceTx(...)` to inject custom `TxBuilder` steps
- Collateral override: `withCollateralInputs(TransactionInput...)`
- Reference-based resolution (Signers Registry): `Tx.fromRef(...)`, `feePayerRef(...)`, `collateralPayerRef(...)`, `withSignerRef(...)` when using a `SignerRegistry`

### When to choose QuickTx vs. Composable Functions

- Choose QuickTx if you want minimal code for common flows and a single entrypoint for build + sign + submit + wait.
- Choose Composable Functions directly if you need to handcraft every step, reuse builders outside QuickTx, or integrate with highly customized pipelines.

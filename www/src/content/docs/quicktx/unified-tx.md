---
title: "Unified Tx API"
description: "ScriptTx merged into Tx — one class for all transaction types"
---

> **Preview feature**: available in 0.8.0 previews. See the [latest preview](/start-here/installation/#versions). APIs may change before the stable release.

## What Changed

In previous versions, building transactions required choosing between two classes:

- **`Tx`** — for regular payments, native token minting, staking, and governance
- **`ScriptTx`** — for Plutus script inputs, Plutus minting, and validator attachment

In the 0.8.0 previews, **all `ScriptTx` operations are also available directly on `Tx`**, and `ScriptTx` is deprecated.
It isn't deprecated in the stable 0.7.x releases, where it's still how you build script transactions, and it still works in the previews.

## Why

- **No more class-choice burden** — you don't need to decide upfront whether your transaction involves scripts
- **Mix operations freely** — combine script inputs, Plutus minting, native payments, and staking in a single `Tx`
- **Simple migration** — replace `new ScriptTx()` with `new Tx()` and set the fee payer

## New Methods on Tx

### Script Input Collection

```java
// Collect from script UTXOs with redeemer and optional datum
tx.collectFrom(utxo, redeemer, datum)
tx.collectFrom(utxoList, redeemer, datum)
tx.collectFrom(utxo, redeemer)
tx.collectFrom(utxoList, redeemer)

// Predicate-based script UTXO selection
tx.collectFrom(scriptAddress, utxo -> utxo.getAmount().stream().anyMatch(a -> a.getUnit().equals(tokenUnit)), redeemer, datum)

// Filter-based script UTXO selection
tx.collectFrom(scriptAddress, filterSpec, redeemer, datum)
```

### Reference Inputs

```java
// Read reference inputs (no spending)
tx.readFrom(utxo1, utxo2)
tx.readFrom(transactionInput)
tx.readFrom(txHash, outputIndex)
```

### Plutus Minting

```java
// Mint with Plutus script (singular: mintAsset)
tx.mintAsset(plutusScript, asset, redeemer)
tx.mintAsset(plutusScript, assets, redeemer, receiver)
tx.mintAsset(plutusScript, assets, redeemer, receiver, outputDatum)

// Mint via reference script (by policy ID)
tx.mintAsset(policyId, asset, redeemer)
tx.mintAsset(policyId, assets, redeemer, receiver)
```

Note: Native script minting continues to use `mintAssets` (plural), while Plutus minting uses `mintAsset` (singular).

### Validator Attachment

```java
tx.attachSpendingValidator(plutusScript)
tx.attachMintValidator(plutusScript)
tx.attachCertificateValidator(plutusScript)
tx.attachRewardValidator(plutusScript)
tx.attachProposingValidator(plutusScript)
tx.attachVotingValidator(plutusScript)
```

### Change Address with Datum

```java
tx.withChangeAddress(address, inlineDatum)
tx.withChangeAddress(address, datumHash)
```

### Script-Protected Stake Operations

```java
// Deregister with redeemer
tx.deregisterStakeAddress(stakeAddr, redeemer)

// Delegate with redeemer
tx.delegateTo(stakeAddr, poolId, redeemer)

// Withdraw with redeemer
tx.withdraw(rewardAddr, amount, redeemer)
tx.withdraw(rewardAddr, amount, redeemer, receiver)
```

### Script-Protected Governance Operations

```java
tx.registerDRep(credential, anchor, redeemer)
tx.unRegisterDRep(credential, refundAddress, refundAmount, redeemer)
tx.updateDRep(credential, anchor, redeemer)
tx.createProposal(govAction, returnAddress, anchor, redeemer)
tx.createVote(voter, govActionId, vote, anchor, redeemer)
tx.delegateVotingPowerTo(address, dRep, redeemer)
```

## Usage Examples

### Script Input with Payment

```java
Tx tx = new Tx()
        .collectFrom(scriptUtxo, redeemer)
        .attachSpendingValidator(spendingScript)
        .payToAddress(receiver, Amount.ada(10))
        .from(senderAddress);

TxResult result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait();
```

### Reference Inputs

```java
Tx tx = new Tx()
        .collectFrom(scriptUtxo, redeemer)
        .readFrom(referenceUtxo)           // Reference input for inline datum/script
        .payToAddress(receiver, Amount.ada(5))
        .from(senderAddress);
```

### Plutus Minting

```java
Asset token = Asset.builder()
        .name("MyPlutusToken")
        .value(BigInteger.valueOf(100))
        .build();

Tx tx = new Tx()
        .mintAsset(mintingScript, token, PlutusData.unit(), receiverAddress)
        .from(senderAddress);

TxResult result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait();
```

### Mixed Operations in One Transaction

```java
// Combine native payment, Plutus minting, and script input in a single Tx
Tx tx = new Tx()
        .payToAddress(receiver1, Amount.ada(5))              // Native payment
        .mintAsset(mintScript, token, PlutusData.unit())     // Plutus minting
        .collectFrom(scriptUtxo, spendRedeemer)              // Script input
        .attachSpendingValidator(spendingScript)
        .from(senderAddress);
```

## Migration Guide

Replace `new ScriptTx()` with `new Tx()`. Calls with a redeemer keep the same names and arguments. Calls to `collectFrom` without a redeemer are different: `Tx` has no `collectFrom(Utxo)`, and `Tx.collectFrom(List<Utxo>)` adds regular inputs rather than script inputs, so pass a redeemer when you spend from a script.

### Before (0.7.x)

```java
ScriptTx scriptTx = new ScriptTx()
        .collectFrom(scriptUtxo, redeemer, datum)
        .payToAddress(receiver, Amount.ada(10))
        .attachSpendingValidator(validator);

quickTxBuilder.compose(scriptTx)
        .feePayer(senderAddress)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait();
```

### After (0.8.0)

```java
Tx tx = new Tx()
        .collectFrom(scriptUtxo, redeemer, datum)
        .payToAddress(receiver, Amount.ada(10))
        .attachSpendingValidator(validator)
        .from(senderAddress);

quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(sender))
        .completeAndWait();
```

`.from(senderAddress)` sets the sender, who also pays the fee. You can also keep `.feePayer(senderAddress)` on the builder, which still works for a script transaction.

## How Detection Works

You don't need to configure anything. When `QuickTxBuilder` processes a `Tx`, it automatically detects whether the
transaction involves scripts by checking `hasScriptIntents()` — a data-driven inspection of the transaction's
intentions list. If any script-related intents are present (script inputs, Plutus minting, or any operation with a redeemer),
the builder applies script-specific processing (collateral selection, execution unit evaluation, etc.) automatically.

## Comparison

| Aspect | Before (0.7.x) | After (0.8.0) |
|--------|----------------|---------------|
| Class choice | `Tx` for regular, `ScriptTx` for scripts | `Tx` for everything |
| Script inputs | `ScriptTx.collectFrom(utxo, redeemer)` | `Tx.collectFrom(utxo, redeemer)` |
| Plutus minting | `ScriptTx.mintAsset(script, asset, redeemer)` | `Tx.mintAsset(script, asset, redeemer)` |
| Validator attachment | `ScriptTx.attachSpendingValidator(script)` | `Tx.attachSpendingValidator(script)` |
| Mixing operations | Compose separate `Tx` + `ScriptTx` | Single `Tx` with all operations |
| Backward compatibility | N/A | `ScriptTx` still works (deprecated) |

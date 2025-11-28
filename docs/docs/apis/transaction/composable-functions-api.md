---
title: "Composable Functions API"
description: "Flexible transaction building using composable functions"
sidebar_position: 2
---

# Composable Functions API

Composable Functions expose functional building blocks (`TxBuilder`, `TxOutputBuilder`, `TxInputBuilder`, `TxSigner`, etc.) that you can chain to assemble transactions of any complexity. QuickTx is built on top of these primitives.

Think of each function as a tiny, single-responsibility transformer. You wire them together in order—create outputs, derive the necessary inputs, add metadata, balance/fee, then sign—to produce a complete transaction. Because each step is explicit, this style is ideal when you need fine-grained control or want to slot in custom logic alongside the built-ins.

## Key Features

- Chainable, functional style with `andThen(...)`/`and(...)`
- Reusable helpers for inputs, outputs, metadata, fees, balancing, signing
- Customizable: implement your own builders and plug them into the chain

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-core
- **Depends on**: core-api, backend

## Usage Example (Simple Transfer Flow)

This mirrors the getting-started simple transfer guide.

```java
// 1) Define outputs
Output output1 = Output.builder()
        .address(receiverAddress1)
        .assetName(LOVELACE)
        .qty(adaToLovelace(10))
        .build();

Output output2 = Output.builder()
        .address(receiverAddress2)
        .assetName(LOVELACE)
        .qty(adaToLovelace(20))
        .build();

MessageMetadata metadata = MessageMetadata.create()
        .add("First transfer transaction");

// 2) Build a TxBuilder chain
TxBuilder txBuilder = output1.outputBuilder()
        .and(output2.outputBuilder())
        .buildInputs(createFromSender(senderAddress, senderAddress))  // TxOutputBuilder -> TxBuilder
        .andThen(metadataProvider(metadata))
        .andThen(balanceTx(senderAddress, 1));

// 3) Build and sign
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
ProtocolParamsSupplier paramsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());

Transaction signedTx = TxBuilderContext
        .init(utxoSupplier, paramsSupplier)
        .buildAndSign(txBuilder, signerFrom(senderAccount));
```


### How the chain executes

1) `outputBuilder().and(...)` collects intended outputs.  
2) `buildInputs(...)` inspects those outputs (and mint info, if any) and selects the needed inputs + change.  
3) `metadataProvider(...)` attaches aux data.  
4) `balanceTx(...)` finalizes fees and balances change.  
5) `buildAndSign(...)` applies the signer to the composed transaction.

Because each step is a function, you can swap any link in the chain with a custom implementation—for example, replacing input selection with a bespoke UTXO picker or adding an extra fee calculator.

## Core Interfaces (Essentials)

- `TxBuilder` (functional interface): `(TxBuilderContext ctx, Transaction txn) -> void`, chain with `andThen(...)`.
- `TxOutputBuilder`: `accept(TxBuilderContext ctx, List<TransactionOutput> outputs)`; combine with `and(...)`; convert to `TxBuilder` via `buildInputs(...)`.
- `TxInputBuilder`: `apply(TxBuilderContext ctx, List<TransactionOutput> outputs) -> Result(inputs, changeOutputs)`.
- `TxSigner`: `sign(TxBuilderContext ctx, Transaction txn) -> Transaction`; compose with `andThen(...)`.

### When to reach for composable functions

- You need deterministic, ordered control over how inputs/outputs are shaped and balanced.
- You want to reuse the same chain across multiple flows (e.g., embed in a service) with occasional custom swaps.
- You plan to extend the library with custom builders (e.g., specialized fee calculator, metadata injector, or UTXO selection policy) without changing QuickTx.

## Helper Classes (selected)

- `OutputBuilders`: `createFromOutput(Output output)` and more.
- `InputBuilders`: `createFromSender(String sender, String changeAddress)`, HD wallet variants, datum/inline datum aware.
- `AuxDataProviders`: metadata helpers (e.g., `metadataProvider`).
- `FeeCalculators`, `MinFeeCalculators`, `ChangeOutputAdjustments`, `BalanceTxBuilders`: fee/min-ADA/change handling.
- `TransactionSigners`: `signerFrom(Account account)` and combinators.

## Context and Execution

- `TxBuilderContext.init(UtxoSupplier, ProtocolParamsSupplier|ProtocolParams)`: creates the execution context.
- `build(TxBuilder)`: returns unsigned `Transaction`.
- `buildAndSign(TxBuilder, TxSigner)`: returns signed `Transaction`.
- `build(Transaction txn, TxBuilder)`: transforms an existing `Transaction`.
- Per-context options: `mergeOutputs(...)`, `withSearchUtxoByAddressVkh(...)`, `withSerializationEra(Era era)`, `setUtxoSelectionStrategy(...)`, `setUtxoSelector(...)`, `withTxnEvaluator(...)`, `withCostMdls(...)`.

## Custom Composable Function

```java
public class CustomOutputBuilder implements TxOutputBuilder {
    @Override
    public void accept(TxBuilderContext ctx, List<TransactionOutput> outputs) {
        // Add a custom output or mutate outputs
    }
}

TxBuilder txBuilder = new CustomOutputBuilder()
        .buildInputs(createFromSender(sender, sender))
        .andThen(balanceTx(sender, 1));
```

## Best Practices

- Order functions logically: outputs → inputs → metadata/aux data → fee/balance → signing.
- Reuse provided helpers where possible; extend with custom builders for special logic.
- Keep TxBuilder chains deterministic; handle exceptions from custom functions.
- When mixing with QuickTx, you can embed custom `TxBuilder` steps via QuickTx hooks (`preBalanceTx`, `postBalanceTx`).

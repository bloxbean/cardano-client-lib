---
title: "Composable Functions API"
description: "Flexible transaction building using composable functions"
sidebar_position: 2
---

# Composable Functions API

The Composable Functions API provides a balance between simple interface and flexibility. Using out-of-box composable functions, you can achieve any complexity and at the same time, you can write your own composable functions to customize the behavior during transaction building.

## Key Features

- **Modular Design**: Build transactions using reusable functions
- **Customizable**: Create your own composable functions
- **Flexible**: Handle complex transaction scenarios
- **Composable**: Chain functions together for complex logic

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-core
- **Dependencies**: core-api, backend

## Usage Examples

### Basic Payment Transaction

The following example shows how to build a basic payment transaction using composable functions, including input selection, output creation, metadata, fee calculation, and signing.

```java
TxBuilder txBuilder = new TxBuilder()
        .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
        .andThen(OutputBuilders.createFromOutput(Output.builder()
                .address(receiverAddress)
                .value(Value.builder()
                        .coin(Amount.ada(1.5).getQuantity())
                        .build())
                .build()))
        .andThen(AuxDataProviders.metadataProvider(metadata))
        .andThen(FeeCalculators.feeCalculator(senderAddress, 2))
        .andThen(ChangeOutputAdjustments.adjustChangeOutput(senderAddress))
        .andThen(MinFeeCalculators.minFeeCalculator(2, Set.of(senderAddress)))
        .andThen(TransactionSigners.signerFrom(account));
```

### Token Minting

The following example shows how to build a token minting transaction using composable functions, including multi-asset creation and minting logic.

```java
TxBuilder txBuilder = new TxBuilder()
        .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
        .andThen(OutputBuilders.createFromOutput(Output.builder()
                .address(receiverAddress)
                .value(Value.builder()
                        .coin(Amount.ada(1.5).getQuantity())
                        .multiAssets(MultiAsset.builder()
                                .policyId(policyId)
                                .assets(Assets.builder()
                                        .asset(Asset.builder()
                                                .name(assetName)
                                                .value(BigInteger.valueOf(1000))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build()))
        .andThen(MintCreators.mintCreator(asset))
        .andThen(FeeCalculators.feeCalculator(senderAddress, 2))
        .andThen(ChangeOutputAdjustments.adjustChangeOutput(senderAddress))
        .andThen(MinFeeCalculators.minFeeCalculator(2, Set.of(senderAddress)))
        .andThen(TransactionSigners.signerFrom(account));
```

### Custom Composable Function

The following example shows how to create a custom composable function and integrate it into the transaction building process.

```java
public class CustomOutputBuilder implements TxOutputBuilder {
    @Override
    public TxBuilder apply(TxBuilderContext context, TxBuilder txBuilder) {
        // Custom logic here
        return txBuilder;
    }
}

// Use custom function
TxBuilder txBuilder = new TxBuilder()
        .andThen(new CustomOutputBuilder())
        .andThen(TransactionSigners.signerFrom(account));
```

### Advanced Transaction with Multiple Functions

The following example shows how to build a complex transaction using multiple composable functions.

```java
TxBuilder txBuilder = new TxBuilder()
        .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
        .andThen(OutputBuilders.createFromOutput(Output.builder()
                .address(receiverAddress)
                .value(Value.builder()
                        .coin(Amount.ada(2.0).getQuantity())
                        .build())
                .build()))
        .andThen(MintCreators.mintCreator(asset))
        .andThen(AuxDataProviders.metadataProvider(metadata))
        .andThen(FeeCalculators.feeCalculator(senderAddress, 2))
        .andThen(ChangeOutputAdjustments.adjustChangeOutput(senderAddress))
        .andThen(MinFeeCalculators.minFeeCalculator(2, Set.of(senderAddress)))
        .andThen(TransactionSigners.signerFrom(account));

// Build and submit transaction
Transaction transaction = txBuilder.build();
```

## API Reference

### TxBuilder Class

Main class for building transactions using composable functions.

#### Constructor
```java
// Create new transaction builder
public TxBuilder()
```

#### Methods

##### buildInputs(TxInputBuilder inputBuilder)
Sets the input builder function.

```java
public TxBuilder buildInputs(TxInputBuilder inputBuilder)
```

##### andThen(TxOutputBuilder outputBuilder)
Adds an output builder function.

```java
public TxBuilder andThen(TxOutputBuilder outputBuilder)
```

##### andThen(TxAuxDataProvider auxDataProvider)
Adds an auxiliary data provider function.

```java
public TxBuilder andThen(TxAuxDataProvider auxDataProvider)
```

##### andThen(TxFeeCalculator feeCalculator)
Adds a fee calculator function.

```java
public TxBuilder andThen(TxFeeCalculator feeCalculator)
```

##### andThen(TxSigner signer)
Adds a transaction signer function.

```java
public TxBuilder andThen(TxSigner signer)
```

##### build()
Builds the final transaction.

```java
public Transaction build()
```

### InputBuilders Class

Utility class for creating input builder functions.

#### Methods

##### createFromSender(Address sender, Address changeAddress)
Creates an input builder that selects UTXOs from the sender.

```java
public static TxInputBuilder createFromSender(Address sender, Address changeAddress)
```

### OutputBuilders Class

Utility class for creating output builder functions.

#### Methods

##### createFromOutput(Output output)
Creates an output builder from a specific output.

```java
public static TxOutputBuilder createFromOutput(Output output)
```

### FeeCalculators Class

Utility class for creating fee calculator functions.

#### Methods

##### feeCalculator(Address sender, int txSizeUnit)
Creates a fee calculator with specified parameters.

```java
public static TxFeeCalculator feeCalculator(Address sender, int txSizeUnit)
```

### TransactionSigners Class

Utility class for creating transaction signer functions.

#### Methods

##### signerFrom(Account account)
Creates a signer from an account.

```java
public static TxSigner signerFrom(Account account)
```

## Composable Functions Specification Details

### Function Composition
- **Chainable**: Functions can be chained using `andThen()` method
- **Modular**: Each function handles a specific aspect of transaction building
- **Customizable**: Custom functions can be created and integrated

### Built-in Functions
- **InputBuilders**: Handle UTXO selection and input creation
- **OutputBuilders**: Create transaction outputs
- **AuxDataProviders**: Add metadata and auxiliary data
- **FeeCalculators**: Calculate transaction fees
- **ChangeOutputAdjustments**: Handle change output creation
- **MinFeeCalculators**: Calculate minimum fees
- **TransactionSigners**: Sign transactions

### Custom Function Interface
All composable functions implement specific interfaces:
- `TxInputBuilder`
- `TxOutputBuilder`
- `TxAuxDataProvider`
- `TxFeeCalculator`
- `TxSigner`

## Best Practices

1. **Chain Functions Properly**: Order functions logically (inputs → outputs → fees → signing)
2. **Use Built-in Functions**: Leverage existing functions when possible
3. **Create Custom Functions**: Implement custom logic for specific requirements
4. **Handle Errors**: Always handle exceptions from function composition
5. **Test Thoroughly**: Test complex function chains thoroughly

## Integration Examples

### With QuickTx
```java
// Convert composable function chain to QuickTx
TxBuilder txBuilder = new TxBuilder()
    .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
    .andThen(OutputBuilders.createFromOutput(output))
    .andThen(TransactionSigners.signerFrom(account));

// Use with QuickTx
Tx quickTx = new Tx()
    .payToAddress(receiverAddress, Amount.ada(1.0))
    .from(senderAddress);
```

### With Custom Functions
```java
// Create custom fee calculator
public class CustomFeeCalculator implements TxFeeCalculator {
    @Override
    public TxBuilder apply(TxBuilderContext context, TxBuilder txBuilder) {
        // Custom fee calculation logic
        return txBuilder;
    }
}

// Use in transaction building
TxBuilder txBuilder = new TxBuilder()
    .andThen(new CustomFeeCalculator())
    .andThen(TransactionSigners.signerFrom(account));
```

For more information about Composable Functions, refer to the [Composable Functions documentation](https://github.com/bloxbean/cardano-client-lib).

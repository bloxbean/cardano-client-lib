---
title: "QuickTx API"
description: "Declarative transaction building API"
sidebar_position: 1
---

# QuickTx API

The QuickTx API is a declarative-style transaction builder that strikes a balance between simplicity and flexibility. It's built upon the Composable Functions API and offers a streamlined experience, supporting an extensive range of transactions.

## Key Features

- **Declarative Style**: Build transactions using simple, readable methods
- **Comprehensive Support**: Supports payment, minting, staking, and governance transactions
- **Flexible**: Handles complex transaction scenarios
- **Easy Integration**: Works seamlessly with backend services

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-quicktx
- **Dependencies**: core, core-api, backend

## Usage Examples

### Simple Payment Transaction

The following example shows how to create a simple ADA payment transaction using the QuickTx API.

```java
Tx tx = new Tx()
        .payToAddress(receiverAddress, Amount.ada(1.5))
        .from(senderAddress);

Result`<String>` result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

### Token Minting

The following example shows how to mint a new token using a Plutus script with the QuickTx API.

```java
Tx tx = new Tx()
        .mintAsset(mintingScript, List.of(asset), PlutusData.unit(), receiverAddress)
        .from(senderAddress);

Result`<String>` result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

### Multi-Output Transaction

The following example shows how to create a transaction with multiple outputs using the QuickTx API.

```java
Tx tx = new Tx()
        .payToAddress(receiver1, Amount.ada(1.0))
        .payToAddress(receiver2, Amount.ada(2.0))
        .from(senderAddress);

Result`<String>` result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

### Staking Operations

The following example shows how to perform staking operations including delegation and reward withdrawal using the QuickTx API.

```java
// Delegate to stake pool
Tx tx = new Tx()
        .delegateTo(account, poolId)
        .from(senderAddress);

// Withdraw rewards
Tx tx = new Tx()
        .withdrawRewards(account, Amount.ada(1.0))
        .from(senderAddress);
```

### Complex Transaction with Metadata

The following example shows how to create a complex transaction with multiple outputs and metadata.

```java
// Create transaction with multiple outputs and metadata
CBORMetadata metadata = new CBORMetadata()
    .put(BigInteger.valueOf(674), "Transaction message");

Tx tx = new Tx()
        .payToAddress(receiver1, Amount.ada(1.0))
        .payToAddress(receiver2, Amount.ada(2.0))
        .attachMetadata(metadata)
        .from(senderAddress);

Result`<String>` result = quickTxBuilder.compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);
```

## API Reference

### Tx Class

Main class for building transactions with the QuickTx API.

#### Constructor
```java
// Create new transaction
public Tx()
```

#### Methods

##### payToAddress(Address address, Amount amount)
Adds a payment output to the transaction.

```java
public Tx payToAddress(Address address, Amount amount)
```

##### mintAsset(PlutusScript script, List`<Asset>` assets, PlutusData redeemer, Address to)
Adds asset minting to the transaction.

```java
public Tx mintAsset(PlutusScript script, List`<Asset>` assets, PlutusData redeemer, Address to)
```

##### delegateTo(Account account, String poolId)
Adds stake delegation to the transaction.

```java
public Tx delegateTo(Account account, String poolId)
```

##### withdrawRewards(Account account, Amount amount)
Adds reward withdrawal to the transaction.

```java
public Tx withdrawRewards(Account account, Amount amount)
```

##### attachMetadata(CBORMetadata metadata)
Attaches metadata to the transaction.

```java
public Tx attachMetadata(CBORMetadata metadata)
```

##### from(Address address)
Sets the sender address for the transaction.

```java
public Tx from(Address address)
```

### QuickTxBuilder Class

Builder class for composing and executing transactions.

#### Constructor
```java
// Create with UTXO supplier
public QuickTxBuilder(UtxoSupplier utxoSupplier)
```

#### Methods

##### compose(Tx tx)
Composes a transaction for building.

```java
public QuickTxComposer compose(Tx tx)
```

##### utxoSelectionStrategy(UtxoSelectionStrategy strategy)
Sets custom UTXO selection strategy.

```java
public QuickTxBuilder utxoSelectionStrategy(UtxoSelectionStrategy strategy)
```

### QuickTxComposer Class

Handles transaction composition and signing.

#### Methods

##### withSigner(Signer signer)
Adds a signer to the transaction.

```java
public QuickTxComposer withSigner(Signer signer)
```

##### completeAndWait(Consumer`<String>` callback)
Completes and submits the transaction.

```java
public Result`<String>` completeAndWait(Consumer`<String>` callback)
```

## QuickTx Specification Details

### Transaction Building Process
1. **Transaction Creation**: Create Tx object with desired operations
2. **Composition**: Use QuickTxBuilder to compose the transaction
3. **Signing**: Add signers to the transaction
4. **Submission**: Complete and submit to the network

### Supported Operations
- **Payment Transactions**: ADA and native token transfers
- **Asset Minting**: Create new tokens using Plutus scripts
- **Stake Operations**: Delegation and reward withdrawal
- **Metadata**: Attach transaction metadata

### Fee Calculation
- **Automatic**: Fees calculated automatically during composition
- **Configurable**: Can be customized using fee calculators

## Best Practices

1. **Use Appropriate Methods**: Choose the right method for your transaction type
2. **Handle Errors**: Always handle Result objects for error checking
3. **Optimize UTXO Selection**: Use custom strategies for complex scenarios
4. **Validate Inputs**: Ensure addresses and amounts are valid before building
5. **Test Transactions**: Test on testnet before mainnet deployment

## Integration Examples

### With CIP25 (NFT Metadata)
```java
// Create CIP25 NFT metadata
CIP25NFT nftMetadata = CIP25NFT.create()
    .name("MyNFT")
    .image("https://example.com/image.png")
    .description("An NFT minted with QuickTx");

// Mint NFT with metadata
Tx tx = new Tx()
    .mintAsset(mintingScript, List.of(nftAsset), PlutusData.unit(), receiverAddress)
    .attachMetadata(nftMetadata)
    .from(senderAddress);
```

### With Custom UTXO Selection
```java
// Create custom UTXO selection strategy
UtxoSelectionStrategy customStrategy = new CustomUtxoSelectionStrategy(utxoSupplier);

// Use with QuickTx
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier)
    .utxoSelectionStrategy(customStrategy);

Tx tx = new Tx()
    .payToAddress(receiverAddress, Amount.ada(10))
    .from(senderAddress);
```

For more information about QuickTx, refer to the [QuickTx documentation](https://github.com/bloxbean/cardano-client-lib).

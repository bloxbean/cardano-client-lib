---
description: QuickTx - The recommended high-level API for building and submitting Cardano transactions with minimal boilerplate
sidebar_label: Overview
sidebar_position: 1
---

# QuickTx API Overview

QuickTx is the **recommended high-level API** for building and submitting Cardano transactions in Cardano Client Lib. It provides a fluent, type-safe interface that handles the complexity of transaction construction while maintaining full access to underlying capabilities.

## Why QuickTx?

QuickTx was designed to address common pain points in Cardano transaction building:

✅ **Minimal Boilerplate** - Build complex transactions with just a few lines of code  
✅ **Type Safety** - Compile-time verification prevents common errors  
✅ **Fluent Interface** - Readable, chainable method calls  
✅ **Comprehensive Coverage** - Supports all transaction types (payments, minting, staking, governance, smart contracts)  
✅ **Error Handling** - Built-in validation and clear error messages  
✅ **Production Ready** - Optimized for performance and reliability  

## Quick Example

Here's how easy it is to build and submit a transaction with QuickTx:

```java
// Create a simple payment transaction
Tx payment = new Tx()
    .payToAddress("addr1...", Amount.ada(10))
    .payToAddress("addr2...", Amount.ada(5))
    .attachMetadata(MessageMetadata.create().add("Payment for services"))
    .from(senderAddress);

// Submit and wait for confirmation
Result<String> result = quickTxBuilder
    .compose(payment)
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait();

if (result.isSuccessful()) {
    System.out.println("Transaction confirmed: " + result.getValue());
}
```

## Core Components

### Transaction Types

QuickTx provides specialized transaction classes for different use cases:

| Class | Purpose | Use Cases |
|-------|---------|-----------|
| **[Tx](./api-reference.md#tx-class---simple-transactions)** | Simple transactions | Payments, minting, staking, governance |
| **[ScriptTx](./api-reference.md#scripttx-class---smart-contract-interactions)** | Smart contract interactions | DeFi, dApps, complex validation logic |

### Builder and Orchestration

| Component | Purpose |
|-----------|---------|
| **[QuickTxBuilder](./api-reference.md#quicktxbuilder-class---orchestration)** | Transaction orchestration and submission |
| **[TxContext](./api-reference.md#txcontext-class---configuration--execution)** | Configuration and execution control |

## Transaction Types Comparison

### Simple Transactions (Tx)

Perfect for standard operations:

```java
// Multi-recipient payment
Tx payment = new Tx()
    .payToAddress(recipient1, Amount.ada(10))
    .payToAddress(recipient2, Amount.ada(5))
    .from(sender);

// Native token minting
Tx minting = new Tx()
    .mintAssets(policy, asset, receiver)
    .from(minter);

// Staking operations
Tx staking = new Tx()
    .registerStakeAddress(account)
    .delegateTo(account, poolId)
    .from(account.baseAddress());
```

### Smart Contract Transactions (ScriptTx)

For interacting with Plutus contracts:

```java
// Contract interaction
ScriptTx contractCall = new ScriptTx()
    .collectFrom(contractUtxo, redeemer)
    .payToContract(contractAddress, amount, newDatum)
    .attachSpendingValidator(validator);

// NFT minting with Plutus script
ScriptTx nftMint = new ScriptTx()
    .mintAsset(nftPolicy, nft, mintRedeemer, collector)
    .attachMetadata(nftMetadata);
```

## Key Features

### Atomic Multi-Operations

Combine multiple operations in a single atomic transaction:

```java
// Register stake address, delegate, and send payment atomically
Tx multiOp = new Tx()
    .registerStakeAddress(account)
    .delegateTo(account, poolId)
    .payToAddress(friend, Amount.ada(25))
    .from(account.baseAddress());
```

### Flexible Configuration

Rich configuration options for production use:

```java
Result<String> result = quickTxBuilder
    .compose(transaction)
    .feePayer(treasuryAddress)                    // Custom fee payer
    .validTo(getCurrentSlot() + 7200)             // 2-hour validity window
    .withSigner(SignerProviders.signerFrom(user)) // Multiple signers
    .withSigner(SignerProviders.signerFrom(treasury))
    .mergeOutputs(true)                           // Optimize outputs
    .completeAndWait(Duration.ofMinutes(5));      // Custom timeout
```

### Built-in Error Handling

Comprehensive error handling with actionable messages:

```java
Result<String> result = quickTxBuilder.compose(tx).withSigner(signer).complete();

if (result.isSuccessful()) {
    String txHash = result.getValue();
    // Handle success
} else {
    String error = result.getResponse();
    if (error.contains("InsufficientFunds")) {
        // Handle insufficient funds
    } else if (error.contains("ScriptFailure")) {
        // Handle script execution error
    }
    // Handle other errors
}
```

## Getting Started

### 1. Setup QuickTxBuilder

```java
// Using Blockfrost backend
BFBackendService backendService = new BFBackendService(
    "https://cardano-mainnet.blockfrost.io/api/v0/",
    "your_project_id"
);

QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
```

### 2. Create Your First Transaction

```java
// Create account
Account account = Account.createFromMnemonic(Networks.mainnet(), mnemonic);

// Build transaction
Tx firstTx = new Tx()
    .payToAddress("addr1...", Amount.ada(1))
    .from(account.baseAddress());

// Submit transaction
Result<String> result = quickTxBuilder
    .compose(firstTx)
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait();
```

### 3. Next Steps

Explore the comprehensive documentation:

1. **[API Reference](./api-reference.md)** - Complete method documentation
2. **[Builder Patterns](./builder-patterns.md)** - Advanced composition techniques  
3. **[Error Handling](./error-handling.md)** - Robust error management
4. **[Performance Guide](./performance.md)** - Production optimization

## Common Patterns

### Batch Payments

```java
Tx batchPayment = new Tx().from(sender);

for (Payment payment : payments) {
    batchPayment.payToAddress(payment.getReceiver(), payment.getAmount());
}

Result<String> result = quickTxBuilder
    .compose(batchPayment)
    .withSigner(signer)
    .complete();
```

### Token Operations

```java
// Create and mint tokens
Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("my_token", 1, 1);
Asset token = new Asset("MyToken", BigInteger.valueOf(1000000));

Tx tokenCreation = new Tx()
    .mintAssets(policy.getPolicyScript(), token, treasuryAddress)
    .attachMetadata(createTokenMetadata())
    .from(minterAddress);
```

### Staking Workflow

```java
// Complete staking setup
Tx stakingSetup = new Tx()
    .registerStakeAddress(account)
    .delegateTo(account, "pool1abcd...")
    .from(account.baseAddress());

// Later: withdraw rewards
Tx rewardWithdrawal = new Tx()
    .withdraw(account.stakeAddress(), availableRewards)
    .from(account.baseAddress());
```

## Migration from Other APIs

If you're using other transaction building approaches:

| From | To | Benefits |
|------|----|----|
| **Low-level API** | QuickTx | Dramatically less boilerplate |
| **Composable Functions** | QuickTx | Simpler syntax, better error handling |
| **Legacy High-level API** | QuickTx | Modern design, more features |

See **[Choosing Your Path](../quickstart/choosing-your-path.md)** for detailed migration guidance.

## Best Practices

### ✅ Do

- Use `Tx` for simple operations
- Use `ScriptTx` for smart contract interactions
- Always handle `Result` objects properly
- Use batch operations for multiple payments
- Configure appropriate timeouts for production
- Implement proper error handling

### ❌ Don't

- Ignore transaction results
- Mix transaction types inappropriately
- Use hardcoded addresses in production
- Skip validation of user inputs
- Create overly large transactions

## Production Considerations

For production deployments, review:

- **[Performance Guide](./performance.md)** - Optimization and scaling
- **[Error Handling](./error-handling.md)** - Robust error management
- **[First Transaction Tutorial](../quickstart/first-transaction.md)** - Step-by-step setup

## Support and Resources

- **[API Reference](./api-reference.md)** - Complete technical documentation
- **[Examples Repository](https://github.com/bloxbean/cardano-client-examples)** - Working code examples
- **[JavaDoc](https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-core/latest/index.html)** - API documentation
- **[Community Discord](https://discord.gg/your-discord)** - Get help and support

---

**QuickTx is the modern, developer-friendly way to build Cardano transactions. Start building today!**
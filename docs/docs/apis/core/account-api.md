---
title: "Account API"
description: "Account management and key derivation APIs"
sidebar_position: 1
---

# Account API

The Account API provides a simple abstraction for creating and managing Cardano accounts. It encapsulates features like CIP-1852 compatible address derivation, BIP-39 mnemonic generation, key management, and transaction signing through a simple `Account` class.

## Key Features

- **Mnemonic Generation**: Generate BIP-39 compatible mnemonic phrases
- **Address Derivation**: Derive various types of Cardano addresses (base, enterprise, stake)
- **Key Management**: Manage private keys and key pairs securely
- **Transaction Signing**: Sign transactions with account credentials
- **Network Support**: Support for mainnet, testnet, and preview networks
- **DRep Support**: Generate DRep credentials for governance participation

## Core Classes

### Account Class
The main class for account operations, providing methods for address generation, key management, and transaction signing.

**Key Methods:**
- `createFromMnemonic()` - Create account from mnemonic phrase
- `baseAddress()` - Get base address for payments
- `enterpriseAddress()` - Get enterprise address (no staking)
- `stakeAddress()` - Get stake address for delegation
- `sign()` - Sign transactions

## Usage Examples

### Creating Accounts

Create accounts for different networks using mnemonic phrases:

```java
// Create account from mnemonic for mainnet
String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
Account mainnetAccount = new Account(Networks.mainnet(), mnemonic);

// Create account for testnet
Account testnetAccount = new Account(Networks.testnet(), mnemonic);

// Generate new account with random mnemonic
Account newAccount = new Account(Networks.testnet());
String generatedMnemonic = newAccount.mnemonic();
```

### Address Generation

Retrieve different types of addresses from an account:

```java
// Get base address (payment + staking)
String baseAddress = account.baseAddress();
System.out.println("Base Address: " + baseAddress);

// Get enterprise address (payment only)
String enterpriseAddress = account.enterpriseAddress();
System.out.println("Enterprise Address: " + enterpriseAddress);

// Get stake address (for delegation)
String stakeAddress = account.stakeAddress();
System.out.println("Stake Address: " + stakeAddress);
```

### Key Management

Access private keys, public keys, and credentials:

```java
// Get private key bytes
byte[] privateKey = account.privateKeyBytes();

// Get HD key pair
HdKeyPair keyPair = account.hdKeyPair();
HdPublicKey publicKey = keyPair.getPublicKey();
HdPrivateKey privateKey = keyPair.getPrivateKey();

// Get mnemonic phrase
String mnemonic = account.mnemonic();

// Get DRep credential for governance
Credential drepCredential = account.drepCredential();
```

### Transaction Signing

Sign transactions using the account:

```java
// Create transaction signer from account
TransactionSigner signer = SignerProviders.signerFrom(account);

// Sign a transaction
Transaction signedTransaction = signer.sign(transaction);

// Alternative: Direct signing with account
Transaction signedTx = account.sign(transaction);
```

## Advanced Usage

### Multi-Account Management

Manage multiple accounts for different purposes:

```java
// Main spending account
Account spendingAccount = new Account(Networks.mainnet(), spendingMnemonic);

// Savings account
Account savingsAccount = new Account(Networks.mainnet(), savingsMnemonic);

// Business account
Account businessAccount = new Account(Networks.mainnet(), businessMnemonic);

// Get addresses for each account
String spendingAddress = spendingAccount.baseAddress();
String savingsAddress = savingsAccount.baseAddress();
String businessAddress = businessAccount.baseAddress();
```

### Network-Specific Operations

Handle different network configurations:

```java
// Mainnet account
Account mainnetAccount = new Account(Networks.mainnet(), mnemonic);
String mainnetAddress = mainnetAccount.baseAddress(); // addr1...

// Testnet account
Account testnetAccount = new Account(Networks.testnet(), mnemonic);
String testnetAddress = testnetAccount.baseAddress(); // addr_test1...

// Preview network account
Account previewAccount = new Account(Networks.preview(), mnemonic);
String previewAddress = previewAccount.baseAddress(); // addr_test1...
```

## Best Practices

### Secure Key Storage

```java
// Generate secure mnemonic
Account account = new Account(Networks.mainnet());
String mnemonic = account.mnemonic();

// Store mnemonic securely (not in plain text)
// Use secure storage mechanisms in production
```

### Mnemonic Validation

```java
// Validate mnemonic before creating account
public boolean isValidMnemonic(String mnemonic) {
    try {
        new Account(Networks.testnet(), mnemonic);
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

### Address Verification

```java
// Verify account generates expected addresses
Account account = new Account(Networks.testnet(), knownMnemonic);
String expectedAddress = "addr_test1..."; // Known address for this mnemonic

if (account.baseAddress().equals(expectedAddress)) {
    System.out.println("Account verification successful");
} else {
    System.out.println("Account verification failed");
}
```

## Error Handling

Handle common account-related errors:

```java
try {
    // Create account with potentially invalid mnemonic
    Account account = new Account(Networks.mainnet(), userMnemonic);
    String address = account.baseAddress();
} catch (Exception e) {
    if (e.getMessage().contains("Invalid mnemonic")) {
        System.err.println("Mnemonic phrase is invalid");
    } else if (e.getMessage().contains("Network")) {
        System.err.println("Network configuration error");
    } else {
        System.err.println("Account creation failed: " + e.getMessage());
    }
}
```

## Account vs HD Wallet Comparison

| Feature | Account API | HD Wallet API |
|---------|-------------|---------------|
| **Complexity** | Simple | Advanced |
| **Address Generation** | Fixed addresses | Multiple derived addresses |
| **Privacy** | Basic | Enhanced (new address per transaction) |
| **Use Case** | Simple applications | Privacy-focused applications |
| **Key Management** | Single key pair | Hierarchical key derivation |
| **Standards** | Basic Cardano | BIP32/CIP1852 compliant |

Choose Account API for simple applications requiring basic account functionality. For enhanced privacy and multiple address generation, consider the HD Wallet API.
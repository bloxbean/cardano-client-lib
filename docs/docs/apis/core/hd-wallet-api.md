---
title: "HD Wallet API"
description: "Hierarchical deterministic wallet management with BIP32/CIP1852 support"
sidebar_position: 2
---

# HD Wallet API

The HD Wallet API provides hierarchical deterministic wallet functionality, allowing you to derive multiple keys and addresses from a single master key. This is an alternative to the Account API that supports enhanced privacy through key derivation and follows BIP32/CIP1852 standards.

## Key Features

- **Hierarchical Key Derivation**: Generate multiple keys from a single master key
- **Address Privacy**: Generate new addresses for each transaction
- **BIP32/CIP1852 Compliance**: Follow industry standards for key derivation
- **Multi-Account Support**: Manage multiple accounts within a single wallet
- **Deterministic Generation**: Same mnemonic always generates same addresses
- **Backup Simplicity**: Single mnemonic backs up entire wallet structure

## Core Classes

### Wallet Interface
The main interface for HD wallet operations, providing methods for address generation, account management, and transaction signing.

### DefaultWallet Class
The default implementation of the Wallet interface, supporting standard HD wallet functionality.

**Key Methods:**
- `create()` - Create new wallet with generated mnemonic
- `createFromMnemonic()` - Restore wallet from existing mnemonic
- `getBaseAddress()` - Get base address at specific index
- `getEntAddress()` - Get enterprise address at specific index
- `signTransaction()` - Sign transactions with wallet keys

## Usage Examples

### Creating HD Wallets

Create wallets with different mnemonic lengths and configurations:

```java
// Create wallet with 24-word mnemonic (default)
Wallet wallet = Wallet.create(Networks.testnet());
String mnemonic = wallet.getMnemonic();
System.out.println("Generated 24-word mnemonic");

// Create wallet with 15-word mnemonic
Wallet wallet15 = Wallet.create(Networks.testnet(), Words.FIFTEEN);
String mnemonic15 = wallet15.getMnemonic();
System.out.println("Generated 15-word mnemonic");

// Create wallet with 12-word mnemonic
Wallet wallet12 = Wallet.create(Networks.testnet(), Words.TWELVE);
String mnemonic12 = wallet12.getMnemonic();
System.out.println("Generated 12-word mnemonic");
```

### Restoring Wallets

Restore wallets from existing mnemonic phrases:

```java
// Restore wallet from mnemonic
String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
Wallet restoredWallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic);

// Restore wallet for specific account
Wallet accountWallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic, 1);
```

### Address Generation

Generate multiple addresses from a single wallet:

```java
// Generate base addresses at different indexes
Address address0 = wallet.getBaseAddress(0);
Address address1 = wallet.getBaseAddress(1);
Address address2 = wallet.getBaseAddress(2);

System.out.println("Address 0: " + address0.getAddress());
System.out.println("Address 1: " + address1.getAddress());
System.out.println("Address 2: " + address2.getAddress());

// Generate enterprise addresses (no staking)
Address entAddress0 = wallet.getEntAddress(0);
Address entAddress1 = wallet.getEntAddress(1);

System.out.println("Enterprise Address 0: " + entAddress0.getAddress());
System.out.println("Enterprise Address 1: " + entAddress1.getAddress());
```

### Multi-Account Management

Manage multiple accounts within a single wallet:

```java
// Get addresses for different accounts
Address account0Address = wallet.getBaseAddress(0, 0); // Account 0, Index 0
Address account1Address = wallet.getBaseAddress(1, 0); // Account 1, Index 0
Address account2Address = wallet.getBaseAddress(2, 0); // Account 2, Index 0

// Switch between accounts
wallet.setAccountNo(0); // Switch to account 0
Address currentAccountAddress = wallet.getBaseAddress(0);

wallet.setAccountNo(1); // Switch to account 1
Address newAccountAddress = wallet.getBaseAddress(0);
```

### Account Object Access

Get Account objects for transaction operations:

```java
// Get account at specific index for current account number
Account account0 = wallet.getAccountAtIndex(0);
Account account1 = wallet.getAccountAtIndex(1);

// Get account for specific account number and index
Account specificAccount = wallet.getAccount(1, 5); // Account 1, Index 5

// Use accounts for transaction signing
String accountAddress = account0.baseAddress();
Transaction signedTx = account0.sign(transaction);
```

## Advanced Usage

### Privacy-Enhanced Address Generation

Generate new addresses for each transaction to enhance privacy:

```java
// Privacy pattern: new address per transaction
public class PrivacyWallet {
    private final Wallet wallet;
    private int currentIndex = 0;
    
    public PrivacyWallet(Network network) {
        this.wallet = Wallet.create(network);
    }
    
    public Address getNewAddress() {
        Address address = wallet.getBaseAddress(currentIndex++);
        System.out.println("Generated new address: " + address.getAddress());
        return address;
    }
    
    public List<Address> getUsedAddresses() {
        List<Address> addresses = new ArrayList<>();
        for (int i = 0; i < currentIndex; i++) {
            addresses.add(wallet.getBaseAddress(i));
        }
        return addresses;
    }
}
```

### Wallet Recovery and Scanning

Implement wallet recovery with address scanning:

```java
// Configure wallet scanning
DefaultWallet wallet = new DefaultWallet(Networks.testnet());

// Set gap limit for address scanning
wallet.setGapLimit(50); // Scan 50 consecutive empty addresses

// Set specific indexes to scan (bypass gap limit)
wallet.setIndexesToScan(new int[]{0, 1, 2, 5, 10, 20});

// Scan for used addresses
public List<Address> scanForUsedAddresses(Wallet wallet, UtxoSupplier utxoSupplier) {
    List<Address> usedAddresses = new ArrayList<>();
    int gapCount = 0;
    int index = 0;
    
    while (gapCount < wallet.getGapLimit()) {
        Address address = wallet.getBaseAddress(index);
        List<Utxo> utxos = utxoSupplier.getAll(address.getAddress());
        
        if (!utxos.isEmpty()) {
            usedAddresses.add(address);
            gapCount = 0; // Reset gap counter
        } else {
            gapCount++;
        }
        index++;
    }
    
    return usedAddresses;
}
```

### Key Management

Access and manage wallet keys securely:

```java
// Get root key pair (if available)
Optional<HdKeyPair> rootKeyPair = wallet.getRootKeyPair();
if (rootKeyPair.isPresent()) {
    HdKeyPair keyPair = rootKeyPair.get();
    System.out.println("Root key available");
}

// Get root private key bytes
Optional<byte[]> rootPrivateKey = wallet.getRootPvtKey();
if (rootPrivateKey.isPresent()) {
    byte[] keyBytes = rootPrivateKey.get();
    System.out.println("Root private key: " + keyBytes.length + " bytes");
}

// Get stake address for delegation
String stakeAddress = wallet.getStakeAddress();
System.out.println("Stake Address: " + stakeAddress);
```

### Transaction Signing

Sign transactions with wallet UTXOs:

```java
// Sign transaction with specific UTXOs
Set<WalletUtxo> walletUtxos = getWalletUtxos(); // Your UTXO collection logic
Transaction signedTx = wallet.signTransaction(transaction, walletUtxos);

// Sign with stake key
Transaction stakeSignedTx = wallet.signWithStakeKey(transaction);

// Combined signing
Transaction fullySignedTx = wallet.signTransaction(
    wallet.signWithStakeKey(transaction), 
    walletUtxos
);
```

## Best Practices

### Secure Wallet Creation

```java
// Generate wallet securely
Wallet secureWallet = Wallet.create(Networks.mainnet());
String mnemonic = secureWallet.getMnemonic();

// Store mnemonic securely (encrypted storage recommended)
// Never store mnemonic in plain text
storeSecurely(mnemonic);

// Recreate wallet when needed
Wallet restoredWallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic);
```

### Address Management Strategy

```java
// Organize addresses by purpose
public class WalletManager {
    private final Wallet wallet;
    
    public WalletManager(Network network, String mnemonic) {
        this.wallet = Wallet.createFromMnemonic(network, mnemonic);
    }
    
    // Receiving addresses (account 0)
    public Address getReceivingAddress(int index) {
        return wallet.getBaseAddress(0, index);
    }
    
    // Change addresses (account 1)
    public Address getChangeAddress(int index) {
        return wallet.getBaseAddress(1, index);
    }
    
    // Business addresses (account 2)
    public Address getBusinessAddress(int index) {
        return wallet.getBaseAddress(2, index);
    }
}
```

## HD Wallet vs Account API Comparison

| Feature | HD Wallet API | Account API |
|---------|---------------|-------------|
| **Key Management** | Hierarchical key derivation | Single key pair |
| **Address Generation** | Multiple derived addresses | Fixed addresses |
| **Privacy** | High (new address per transaction) | Limited |
| **Backup** | Single mnemonic | Individual keys |
| **Use Case** | Privacy-focused applications | Simple applications |
| **Complexity** | Medium to High | Low |
| **Standards** | BIP32/CIP1852 compliant | Basic |
| **Multi-Account** | Native support | Manual management |

Choose HD Wallet API for applications requiring enhanced privacy, multiple addresses, or hierarchical key management. Use Account API for simpler use cases where basic account functionality is sufficient.
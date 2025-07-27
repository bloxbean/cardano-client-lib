---
description: Complete guide to HD wallets, account management, and address generation in Cardano Client Lib
sidebar_label: HD Wallets & Accounts
sidebar_position: 1
---

# HD Wallets & Account Management

Cardano Client Lib provides a comprehensive Hierarchical Deterministic (HD) wallet implementation that follows industry standards while being optimized for Cardano's unique features. This guide covers everything from basic concepts to advanced multi-account scenarios.

## What are HD Wallets?

HD (Hierarchical Deterministic) wallets generate a tree of cryptographic keys from a single seed, allowing you to:

- **Generate unlimited addresses** from a single backup
- **Organize addresses hierarchically** (accounts, external/internal addresses)
- **Maintain privacy** with unique addresses for each transaction
- **Enable account separation** for different purposes
- **Support deterministic address generation** for wallet recovery

### Key Benefits

✅ **Single backup** - One mnemonic phrase backs up infinite addresses  
✅ **Deterministic recovery** - Same phrase always generates same addresses  
✅ **Account organization** - Separate accounts for different purposes  
✅ **Privacy protection** - New address for each transaction  
✅ **Standard compliance** - Follows CIP-1852 and BIP39 standards  

## Core Concepts

### BIP39 Mnemonic Phrases

Mnemonic phrases are human-readable seeds that represent your wallet's entropy:

```java
// Supported mnemonic lengths
Words.TWELVE     // 12 words (128 bits entropy)
Words.FIFTEEN    // 15 words (160 bits entropy)
Words.EIGHTEEN   // 18 words (192 bits entropy)
Words.TWENTY_ONE // 21 words (224 bits entropy)
Words.TWENTY_FOUR // 24 words (256 bits entropy) - recommended
```

### CIP-1852 Derivation Paths

Cardano follows CIP-1852 for key derivation with this structure:

```
m / purpose' / coin_type' / account' / role / index

Where:
- purpose' = 1852' (CIP-1852 standard, hardened)
- coin_type' = 1815' (ADA, hardened)
- account' = Account number (0', 1', 2', ... hardened)
- role = Address role (0=external, 1=internal, 2=stake)
- index = Address index (0, 1, 2, ... non-hardened)
```

### Address Roles

| Role | Purpose | Example Path | Usage |
|------|---------|--------------|-------|
| **0** | External (receiving) | `m/1852'/1815'/0'/0/0` | Public addresses for receiving payments |
| **1** | Internal (change) | `m/1852'/1815'/0'/1/0` | Change addresses for transactions |
| **2** | Stake | `m/1852'/1815'/0'/2/0` | Staking and delegation addresses |
| **3** | DRep | `m/1852'/1815'/0'/3/0` | Delegated Representative keys |
| **4** | Committee Cold | `m/1852'/1815'/0'/4/0` | Constitutional Committee cold keys |
| **5** | Committee Hot | `m/1852'/1815'/0'/5/0` | Constitutional Committee hot keys |

## Creating HD Wallets

### Method 1: Generate New Wallet

Create a completely new wallet with a random mnemonic:

```java
import com.bloxbean.cardano.hdwallet.Wallet;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip39.Words;

// Generate new wallet with 24-word mnemonic
Wallet wallet = Wallet.create(Networks.mainnet(), Words.TWENTY_FOUR);

// Get the mnemonic for backup (STORE SECURELY!)
String mnemonic = wallet.getMnemonic();
System.out.println("Backup this mnemonic: " + mnemonic);

// Get first account
Account firstAccount = wallet.getAccount(0, 0);
System.out.println("Address: " + firstAccount.baseAddress());
```

### Method 2: Restore from Mnemonic

Restore an existing wallet from a mnemonic phrase:

```java
// Your existing mnemonic phrase
String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";

// Restore wallet
Wallet wallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic);

// Access the same addresses as before
Account account = wallet.getAccount(0, 0);
System.out.println("Restored address: " + account.baseAddress());
```

### Method 3: Create from Root Key

For advanced use cases, create from a root key:

```java
// Your 64-byte root key (from secure storage)
byte[] rootKey = // ... your root key bytes

// Create wallet from root key
Wallet wallet = Wallet.createFromRootKey(Networks.mainnet(), rootKey);

// Use normally
Account account = wallet.getAccount(0, 0);
```

### Method 4: Account-Level Key Creation

Create accounts directly from account-level extended keys:

```java
// Account-level extended key (96 bytes: 64-byte key + 32-byte chaincode)
byte[] accountKey = // ... your account key bytes

// Create account directly
Account account = Account.createFromAccountKey(Networks.mainnet(), accountKey);

// This account can generate all its addresses but not other accounts
String baseAddress = account.baseAddress();
String changeAddress = account.changeAddress();
```

## Account Management

### Basic Account Operations

```java
// Create account from mnemonic
Account account = Account.createFromMnemonic(Networks.mainnet(), mnemonic);

// Get different address types
String baseAddress = account.baseAddress();           // Payment + stake
String enterpriseAddress = account.enterpriseAddress(); // Payment only
String stakeAddress = account.stakeAddress();         // Stake/reward address
String changeAddress = account.changeAddress();       // Internal change address

// Get public keys
String paymentVerificationKey = account.publicKeyHex();
String stakeVerificationKey = account.stakeHdKeyPair().getPublicKey().getKeyData();

// Sign transactions
byte[] signature = account.sign(transactionHash);
```

### Multi-Account Scenarios

HD wallets support multiple accounts for organization:

```java
// Create wallet
Wallet wallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic);

// Account 0: Personal spending
wallet.setAccountNo(0);
Account personal = wallet.getAccount(0, 0);

// Account 1: Business transactions  
wallet.setAccountNo(1);
Account business = wallet.getAccount(1, 0);

// Account 2: Savings/cold storage
wallet.setAccountNo(2);
Account savings = wallet.getAccount(2, 0);

System.out.println("Personal: " + personal.baseAddress());
System.out.println("Business: " + business.baseAddress());
System.out.println("Savings: " + savings.baseAddress());
```

### Address Generation Patterns

Generate multiple addresses for privacy:

```java
// Generate first 10 receiving addresses
for (int i = 0; i < 10; i++) {
    Account account = wallet.getAccount(0, i); // Account 0, index i
    System.out.println("Address " + i + ": " + account.baseAddress());
}

// Generate first 5 change addresses
for (int i = 0; i < 5; i++) {
    Account account = wallet.getAccount(0, i);
    System.out.println("Change " + i + ": " + account.changeAddress());
}
```

## Mnemonic Handling & Security

### Generating Secure Mnemonics

```java
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.bip39.Words;

// Generate cryptographically secure mnemonic
MnemonicCode mnemonicCode = new MnemonicCode();
String mnemonic = mnemonicCode.generateMnemonic(Words.TWENTY_FOUR);

// Validate mnemonic (always validate user input!)
boolean isValid = mnemonicCode.validateMnemonic(mnemonic);
if (!isValid) {
    throw new IllegalArgumentException("Invalid mnemonic phrase");
}
```

### Mnemonic with Passphrase

For enhanced security, use an additional passphrase:

```java
// Generate mnemonic with passphrase support
String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
String passphrase = "my-secure-passphrase"; // Optional additional security

// Create wallet with passphrase
Wallet wallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic, passphrase);

// Different passphrase = different wallet!
Wallet differentWallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic, "different-passphrase");
```

### Security Best Practices

:::warning Critical Security Guidelines

**Mnemonic Storage:**
- ✅ Store mnemonic phrases offline (paper, hardware wallet, etc.)
- ❌ Never store mnemonics in plain text files
- ❌ Never send mnemonics over email or messaging
- ❌ Never store mnemonics in application databases

**Key Management:**
- ✅ Use passphrases for additional security
- ✅ Implement proper key derivation
- ✅ Clear sensitive data from memory when possible
- ❌ Never log private keys or mnemonics

**Application Security:**
- ✅ Validate all user-provided mnemonics
- ✅ Use secure random number generation
- ✅ Implement proper error handling
- ❌ Never expose private keys in APIs

:::

### Validation and Error Handling

```java
public class SecureWalletManager {
    
    public Wallet createSecureWallet(String userMnemonic) {
        try {
            // Validate mnemonic first
            MnemonicCode mnemonicCode = new MnemonicCode();
            if (!mnemonicCode.validateMnemonic(userMnemonic)) {
                throw new IllegalArgumentException("Invalid mnemonic phrase");
            }
            
            // Create wallet
            Wallet wallet = Wallet.createFromMnemonic(Networks.mainnet(), userMnemonic);
            
            // Clear sensitive data (if using char[] or byte[])
            // Arrays.fill(mnemonicBytes, (byte) 0);
            
            return wallet;
            
        } catch (Exception e) {
            // Proper error handling without exposing sensitive data
            throw new RuntimeException("Failed to create wallet", e);
        }
    }
    
    public boolean validateAccountAccess(Account account) {
        try {
            // Test address generation to validate account
            String address = account.baseAddress();
            return address != null && !address.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
```

## Advanced HD Wallet Features

### Address Discovery & Gap Limits

Use address discovery to find used addresses:

```java
import com.bloxbean.cardano.hdwallet.util.HDWalletAddressIterator;
import com.bloxbean.cardano.client.backend.api.UtxoService;

// Setup UTXO supplier for address discovery
UtxoService utxoService = // ... your backend service
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(utxoService);

// Create address iterator with gap limit
HDWalletAddressIterator iterator = new HDWalletAddressIterator(
    wallet,
    utxoSupplier,
    20  // Gap limit: stop after 20 consecutive unused addresses
);

// Find all used addresses
List<Account> usedAccounts = new ArrayList<>();
while (iterator.hasNext()) {
    Account account = iterator.next();
    if (hasUtxos(account.baseAddress())) {
        usedAccounts.add(account);
    }
}
```

### Governance Key Management

Generate keys for Cardano governance participation:

```java
// Generate DRep (Delegated Representative) keys
HdKeyPair drepKeys = wallet.getDRepHdKeyPair();
String drepId = wallet.getDRepId(); // CIP-129 DRep ID

// Generate Constitutional Committee keys
HdKeyPair committeeColdKeys = wallet.getCommitteeColdHdKeyPair();
HdKeyPair committeeHotKeys = wallet.getCommitteeHotHdKeyPair();

// Use in governance transactions
StakeTx govTx = new StakeTx()
    .drepRegistration(drepId, anchor)
    .from(account.baseAddress());
```

### Watch-Only Wallets

Create watch-only wallets from extended public keys:

```java
// Get account extended public key from full wallet
byte[] accountXPub = account.getExtendedPublicKey();

// Create watch-only account (can generate addresses, cannot sign)
Account watchOnlyAccount = Account.createFromAccountPublicKey(Networks.mainnet(), accountXPub);

// Generate addresses (but cannot sign transactions)
String baseAddress = watchOnlyAccount.baseAddress();
String enterpriseAddress = watchOnlyAccount.enterpriseAddress();

// This will throw an exception - cannot sign with public key only
// watchOnlyAccount.sign(transactionHash); // ❌ Will fail
```

### Custom Derivation Paths

For advanced scenarios, use custom derivation paths:

```java
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;

// Create custom derivation path
DerivationPath customPath = DerivationPath.builder()
    .m()
    .purpose(1852, true)    // CIP-1852, hardened
    .coinType(1815, true)   // ADA, hardened  
    .account(5, true)       // Account 5, hardened
    .role(0)                // External addresses
    .index(10)              // Address index 10
    .build();

// Derive keys at custom path
HdKeyPair customKeys = wallet.getKeyPairAtPath(customPath);
```

## Integration with QuickTx

HD wallets integrate seamlessly with QuickTx for transaction building:

```java
// Create wallet and account
Wallet wallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic);
Account senderAccount = wallet.getAccount(0, 0);
Account changeAccount = wallet.getAccount(0, 1); // Different address for change

// Build transaction with HD wallet
Tx tx = new Tx()
    .payToAddress(receiverAddress, Amount.ada(50))
    .from(senderAccount.baseAddress())
    .changeTo(changeAccount.baseAddress()); // Use different change address

// Sign and submit
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
Result<String> result = quickTxBuilder
    .compose(tx)
    .withSigner(SignerProviders.signerFrom(senderAccount))
    .completeAndWait();

if (result.isSuccessful()) {
    System.out.println("Transaction hash: " + result.getValue());
} else {
    System.err.println("Transaction failed: " + result.getResponse());
}
```

## Testing and Development

### Test Network Usage

Always test with testnets during development:

```java
// Use testnet for development
Wallet testWallet = Wallet.create(Networks.testnet(), Words.TWENTY_FOUR);
Account testAccount = testWallet.getAccount(0, 0);

// Testnet addresses have different prefixes
String testAddress = testAccount.baseAddress(); // addr_test...
```

### Unit Testing HD Wallets

```java
@Test
public void testWalletDeterminism() {
    String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
    
    // Create two wallets from same mnemonic
    Wallet wallet1 = Wallet.createFromMnemonic(Networks.testnet(), mnemonic);
    Wallet wallet2 = Wallet.createFromMnemonic(Networks.testnet(), mnemonic);
    
    // Should generate identical addresses
    Account account1 = wallet1.getAccount(0, 0);
    Account account2 = wallet2.getAccount(0, 0);
    
    assertEquals(account1.baseAddress(), account2.baseAddress());
    assertEquals(account1.enterpriseAddress(), account2.enterpriseAddress());
    assertEquals(account1.stakeAddress(), account2.stakeAddress());
}

@Test
public void testMultiAccountSeparation() {
    Wallet wallet = Wallet.create(Networks.testnet(), Words.TWENTY_FOUR);
    
    // Different accounts should have different addresses
    Account account0 = wallet.getAccount(0, 0);
    Account account1 = wallet.getAccount(1, 0);
    
    assertNotEquals(account0.baseAddress(), account1.baseAddress());
}
```

## Common Patterns and Examples

### Simple Wallet Application

```java
public class SimpleWallet {
    private final Wallet wallet;
    private final BackendService backendService;
    
    public SimpleWallet(String mnemonic, BackendService backendService) {
        this.wallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic);
        this.backendService = backendService;
    }
    
    public String getReceiveAddress() {
        // Generate new address for each request
        int nextIndex = findNextUnusedIndex();
        Account account = wallet.getAccount(0, nextIndex);
        return account.baseAddress();
    }
    
    public Result<String> sendAda(String toAddress, BigInteger amount) {
        Account senderAccount = wallet.getAccount(0, 0);
        
        Tx tx = new Tx()
            .payToAddress(toAddress, Amount.ada(amount))
            .from(senderAccount.baseAddress());
            
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigner(SignerProviders.signerFrom(senderAccount))
            .completeAndWait();
    }
    
    private int findNextUnusedIndex() {
        // Implementation to find next unused address index
        // Check UTXO service for each address until finding unused one
        return 0; // Simplified
    }
}
```

### Multi-Account Treasury

```java
public class MultiAccountTreasury {
    private final Wallet wallet;
    
    // Account purposes
    private static final int OPERATIONS_ACCOUNT = 0;
    private static final int RESERVES_ACCOUNT = 1;
    private static final int GOVERNANCE_ACCOUNT = 2;
    
    public MultiAccountTreasury(String mnemonic) {
        this.wallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic);
    }
    
    public Account getOperationsAccount() {
        return wallet.getAccount(OPERATIONS_ACCOUNT, 0);
    }
    
    public Account getReservesAccount() {
        return wallet.getAccount(RESERVES_ACCOUNT, 0);
    }
    
    public Account getGovernanceAccount() {
        return wallet.getAccount(GOVERNANCE_ACCOUNT, 0);
    }
    
    public String getDRepId() {
        wallet.setAccountNo(GOVERNANCE_ACCOUNT);
        return wallet.getDRepId();
    }
}
```

## Troubleshooting

### Common Issues

**Issue**: "Invalid mnemonic phrase"
```java
// Solution: Always validate mnemonic before use
MnemonicCode mnemonicCode = new MnemonicCode();
if (!mnemonicCode.validateMnemonic(userInput.trim())) {
    throw new IllegalArgumentException("Please check your mnemonic phrase");
}
```

**Issue**: "Different addresses than expected"
```java
// Check network (mainnet vs testnet)
Wallet mainnetWallet = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic);
Wallet testnetWallet = Wallet.createFromMnemonic(Networks.testnet(), mnemonic);
// These will generate different addresses!

// Check passphrase (empty string vs null are different)
Wallet wallet1 = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic, "");
Wallet wallet2 = Wallet.createFromMnemonic(Networks.mainnet(), mnemonic, null);
// These may generate different addresses!
```

**Issue**: "Cannot sign transactions"
```java
// Ensure you have the private key, not just public key
Account fullAccount = Account.createFromMnemonic(Networks.mainnet(), mnemonic);
// ✅ Can sign

Account watchOnly = Account.createFromAccountPublicKey(Networks.mainnet(), publicKey);
// ❌ Cannot sign
```

## Next Steps

Now that you understand HD wallets and account management, explore:

- **[Address Types Guide](./address-types.md)** - Different Cardano address formats
- **[Cryptographic Operations](../keys-and-crypto/cryptographic-operations.md)** - Advanced key management and security
- **[First Transaction](../../quickstart/first-transaction.md)** - Using accounts with QuickTx
- **[Native Scripts](../native-scripts/native-scripts-overview.md)** - Multi-signature wallets and time constraints

## Resources

- **[CIP-1852 Specification](https://cips.cardano.org/cips/cip1852/)** - Cardano HD wallet standard
- **[BIP39 Specification](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki)** - Mnemonic phrase standard
- **[Examples Repository](https://github.com/bloxbean/cardano-client-examples)** - Complete working examples
- **[JavaDoc API Reference](https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-core/latest/index.html)** - Detailed API documentation

---

**Remember**: HD wallets are the foundation of Cardano applications. Understanding derivation paths, account separation, and security practices will help you build robust, user-friendly applications.
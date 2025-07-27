---
description: Complete guide to Cardano address types, construction, validation, and practical usage patterns
sidebar_label: Address Types
sidebar_position: 2
---

# Cardano Address Types Guide

Cardano supports multiple address formats, each designed for specific use cases. Understanding these address types is crucial for building robust Cardano applications. This comprehensive guide covers all address types, their structure, use cases, and practical implementation.

## Overview: Why Multiple Address Types?

Cardano's address system is designed for flexibility, privacy, and efficiency:

- **Flexibility**: Different credential combinations (keys vs scripts)
- **Privacy**: Separate addresses for receiving and change
- **Efficiency**: Lightweight delegation with pointer addresses
- **Security**: Proper isolation between payment and staking
- **Backward Compatibility**: Support for legacy Byron addresses

## Address Structure Fundamentals

### Address Components

All modern Cardano addresses (Shelley era) consist of:

```
┌─────────┬──────────────────┬─────────────────────┐
│ Header  │ Payment Part     │ Delegation Part     │
│ (1 byte)│ (28 bytes)       │ (0-28 bytes)        │
└─────────┴──────────────────┴─────────────────────┘
```

**Header Byte Structure:**
```
┌────────────┬──────────────┐
│ Type (4b)  │ Network (4b) │
└────────────┴──────────────┘
```

**Network Encodings:**
- Mainnet: `0001` (1)
- Testnet: `0000` (0)

### Credential Types

**Payment/Delegation credentials can be:**
- **Key Hash**: 28-byte Blake2b-224 hash of a public key
- **Script Hash**: 28-byte Blake2b-224 hash of a native script

## Address Types Deep Dive

### 🏠 Base Addresses

**Header Types**: `0000-0011` (different payment/stake credential combinations)  
**Format**: `addr[1][payment_credential][stake_credential]`  
**Length**: 57 bytes (1 + 28 + 28)

Base addresses contain both payment and staking credentials, enabling:
- **Payment transactions** using the payment credential
- **Stake delegation** using the stake credential
- **Reward collection** to the associated stake credential

#### Construction Patterns

```java
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.common.model.Networks;

// Pattern 1: Payment Key + Stake Key (most common)
HdPublicKey paymentKey = account.hdKeyPair().getPublicKey();
HdPublicKey stakeKey = account.stakeHdKeyPair().getPublicKey();

Address baseAddress = AddressProvider.getBaseAddress(
    paymentKey, 
    stakeKey, 
    Networks.mainnet()
);

// Pattern 2: Payment Script + Stake Key (smart contract with delegation)
Script paymentScript = // ... your native script
Address scriptBaseAddress = AddressProvider.getBaseAddress(
    paymentScript, 
    stakeKey, 
    Networks.mainnet()
);

// Pattern 3: Payment Key + Stake Script (advanced delegation)
Script stakeScript = // ... your stake script
Address advancedBaseAddress = AddressProvider.getBaseAddress(
    paymentKey, 
    stakeScript, 
    Networks.mainnet()
);

// Pattern 4: Payment Script + Stake Script (full script control)
Address fullScriptAddress = AddressProvider.getBaseAddress(
    paymentScript, 
    stakeScript, 
    Networks.mainnet()
);
```

#### Use Cases

✅ **Personal Wallets** - Standard user addresses  
✅ **DeFi Applications** - Payment with staking rewards  
✅ **Multi-signature Wallets** - Shared control with staking  
✅ **Smart Contract Outputs** - Contract funds that earn staking rewards  
✅ **Corporate Treasuries** - Business funds with delegation  

#### Practical Example

```java
public class WalletAddressManager {
    
    public Address createUserAddress(Account account) {
        return AddressProvider.getBaseAddress(
            account.hdKeyPair().getPublicKey(),
            account.stakeHdKeyPair().getPublicKey(),
            Networks.mainnet()
        );
    }
    
    public Address createMultisigAddress(List<HdPublicKey> signers, int threshold, HdPublicKey stakeKey) {
        // Create multi-signature script
        NativeScript multiSigScript = ScriptAtLeast.builder()
            .n(threshold)
            .nativeScripts(signers.stream()
                .map(key -> ScriptPubkey.builder().keyHash(key.getKeyHash()).build())
                .collect(Collectors.toList()))
            .build();
        
        return AddressProvider.getBaseAddress(
            multiSigScript, 
            stakeKey, 
            Networks.mainnet()
        );
    }
}
```

### 🏢 Enterprise Addresses

**Header Types**: `0110-0111`  
**Format**: `addr[1][payment_credential]`  
**Length**: 29 bytes (1 + 28)

Enterprise addresses contain only payment credentials - no staking component.

#### Construction

```java
// Key-based enterprise address
HdPublicKey paymentKey = account.hdKeyPair().getPublicKey();
Address enterpriseAddress = AddressProvider.getEntAddress(
    paymentKey, 
    Networks.mainnet()
);

// Script-based enterprise address
NativeScript paymentScript = // ... your script
Address scriptEnterpriseAddress = AddressProvider.getEntAddress(
    paymentScript, 
    Networks.mainnet()
);

// Generic credential-based
Credential paymentCredential = Credential.fromKey(paymentKey.getKeyHash());
Address credentialAddress = AddressProvider.getEntAddress(
    paymentCredential, 
    Networks.mainnet()
);
```

#### Use Cases

✅ **Exchange Hot Wallets** - No staking needed, lower fees  
✅ **Payment Processors** - Transaction-focused addresses  
✅ **Temporary Addresses** - Short-term use without staking  
✅ **Smart Contract Outputs** - When staking is undesirable  
✅ **Privacy Applications** - Smaller address footprint  

#### Benefits vs Base Addresses

| Factor | Enterprise | Base |
|--------|------------|------|
| **Size** | 29 bytes | 57 bytes |
| **Transaction Fees** | Lower | Higher |
| **Staking Rewards** | ❌ None | ✅ Available |
| **Use Case** | Payments only | Full functionality |

#### Practical Example

```java
public class PaymentProcessor {
    
    public Address createHotWalletAddress(Account account) {
        // Use enterprise address for faster, cheaper transactions
        return AddressProvider.getEntAddress(
            account.hdKeyPair().getPublicKey(),
            Networks.mainnet()
        );
    }
    
    public void processPayments(List<Payment> payments) {
        // Enterprise addresses are ideal for high-volume payment processing
        Address hotWallet = createHotWalletAddress(hotWalletAccount);
        
        for (Payment payment : payments) {
            Tx tx = new Tx()
                .payToAddress(payment.getRecipient(), payment.getAmount())
                .from(hotWallet); // Lower transaction size = lower fees
                
            // Process transaction...
        }
    }
}
```

### 🎯 Pointer Addresses

**Header Types**: `0100-0101`  
**Format**: `addr[1][payment_credential][pointer]`  
**Length**: Variable (29-33 bytes)

Pointer addresses reference stake credentials indirectly through blockchain pointers, enabling lightweight delegation.

#### Pointer Structure

```java
public class Pointer {
    long slot;      // Slot number where stake credential was registered
    int txIndex;    // Transaction index in that slot's block  
    int certIndex;  // Certificate index in that transaction
}
```

#### Construction

```java
import com.bloxbean.cardano.client.address.Pointer;
import com.bloxbean.cardano.client.address.PointerAddress;

// Create pointer to existing stake registration
Pointer stakePointer = new Pointer(2498243, 27, 3);
// Points to: slot 2498243, transaction 27, certificate 3

// Create pointer address
HdPublicKey paymentKey = account.hdKeyPair().getPublicKey();
Address pointerAddress = AddressProvider.getPointerAddress(
    paymentKey, 
    stakePointer, 
    Networks.mainnet()
);

// With script
NativeScript paymentScript = // ... your script
Address scriptPointerAddress = AddressProvider.getPointerAddress(
    paymentScript, 
    stakePointer, 
    Networks.mainnet()
);

// Decode pointer from existing pointer address
PointerAddress existingPointer = new PointerAddress("addr1...");
Pointer extractedPointer = existingPointer.getPointer();
System.out.println("Points to slot: " + extractedPointer.getSlot());
```

#### Use Cases

✅ **Shared Stake Pools** - Multiple addresses pointing to same stake registration  
✅ **Corporate Delegation** - Many payment addresses, one delegation  
✅ **DApp Optimization** - Reduced on-chain footprint  
✅ **Batch Operations** - Single stake registration, multiple payment addresses  

#### Benefits

| Advantage | Description |
|-----------|-------------|
| **Space Efficient** | ~4-8 bytes vs 28 bytes for direct stake credential |
| **Shared Delegation** | Multiple addresses can point to same stake registration |
| **Lower Fees** | Smaller address size reduces transaction costs |
| **Flexible** | Can change delegation without affecting address |

#### Practical Example

```java
public class CorporateTreasury {
    private final Pointer sharedStakePointer;
    
    public CorporateTreasury() {
        // All addresses will point to this stake registration
        this.sharedStakePointer = new Pointer(2498243, 27, 3);
    }
    
    public Address createDepartmentAddress(HdPublicKey departmentKey) {
        // Each department gets its own payment address
        // But all point to the same stake delegation
        return AddressProvider.getPointerAddress(
            departmentKey,
            sharedStakePointer,
            Networks.mainnet()
        );
    }
    
    public List<Address> generateProjectAddresses(List<HdPublicKey> projectKeys) {
        return projectKeys.stream()
            .map(key -> AddressProvider.getPointerAddress(
                key, sharedStakePointer, Networks.mainnet()))
            .collect(Collectors.toList());
    }
}
```

### 🏆 Reward Addresses

**Header Types**: `1110-1111`  
**Format**: `stake[1][stake_credential]`  
**Length**: 29 bytes (1 + 28)

Reward addresses are used exclusively for staking operations and reward collection.

#### Construction

```java
// Stake key-based reward address
HdPublicKey stakeKey = account.stakeHdKeyPair().getPublicKey();
Address rewardAddress = AddressProvider.getRewardAddress(
    stakeKey, 
    Networks.mainnet()
);

// Stake script-based reward address  
NativeScript stakeScript = // ... your stake script
Address scriptRewardAddress = AddressProvider.getRewardAddress(
    stakeScript, 
    Networks.mainnet()
);

// Generic credential-based
Credential stakeCredential = Credential.fromKey(stakeKey.getKeyHash());
Address credentialRewardAddress = AddressProvider.getRewardAddress(
    stakeCredential, 
    Networks.mainnet()
);
```

#### Use Cases

✅ **Stake Registration** - Register stake credentials  
✅ **Delegation** - Delegate to stake pools  
✅ **Reward Withdrawal** - Collect staking rewards  
✅ **Stake Pool Operations** - Pool registration and updates  
✅ **Governance** - DRep registration and voting  

#### Staking Operations Example

```java
public class StakingManager {
    
    public Result<String> registerAndDelegate(Account account, String poolId) {
        Address rewardAddress = AddressProvider.getRewardAddress(
            account.stakeHdKeyPair().getPublicKey(),
            Networks.mainnet()
        );
        
        // Create stake registration and delegation certificates
        StakeRegistration stakeReg = StakeRegistration.builder()
            .stakeCredential(StakeCredential.fromKey(account.stakeHdKeyPair().getPublicKey()))
            .build();
            
        StakeDelegation delegation = StakeDelegation.builder()
            .stakeCredential(StakeCredential.fromKey(account.stakeHdKeyPair().getPublicKey()))
            .poolKeyHash(poolId)
            .build();
        
        // Build staking transaction
        StakeTx stakeTx = new StakeTx()
            .attachCertificate(stakeReg)
            .attachCertificate(delegation)
            .from(account.baseAddress());
            
        return new QuickTxBuilder(backendService)
            .compose(stakeTx)
            .withSigner(SignerProviders.signerFrom(account))
            .completeAndWait();
    }
    
    public Result<String> withdrawRewards(Account account) {
        Address rewardAddress = AddressProvider.getRewardAddress(
            account.stakeHdKeyPair().getPublicKey(),
            Networks.mainnet()
        );
        
        // Check available rewards
        BigInteger rewards = getRewardBalance(rewardAddress);
        if (rewards.equals(BigInteger.ZERO)) {
            return Result.error("No rewards available");
        }
        
        // Create withdrawal
        StakeTx withdrawalTx = new StakeTx()
            .withdrawal(Withdrawal.builder()
                .rewardAddress(rewardAddress)
                .coin(rewards)
                .build())
            .from(account.baseAddress());
            
        return new QuickTxBuilder(backendService)
            .compose(withdrawalTx)
            .withSigner(SignerProviders.signerFrom(account))
            .completeAndWait();
    }
}
```

### 🏛️ Byron Addresses (Legacy)

**Format**: Base58-encoded legacy addresses  
**Prefix**: No bech32 prefix (raw Base58)

Byron addresses are from Cardano's original implementation and are still supported for backward compatibility.

#### Characteristics

- **Encoding**: Base58 (not Bech32)
- **Structure**: Different from Shelley addresses
- **Support**: Read-only in most modern applications
- **Network**: Mainnet only (no testnet Byron addresses in practice)

#### Working with Byron Addresses

```java
import com.bloxbean.cardano.client.address.ByronAddress;

// Recognize Byron address (starts with 'Ae' or 'Dd')
String byronAddrString = "DdzFFzCqrhsqedBTRsl...";

// Validate Byron address
boolean isValidByron = ByronAddress.isValidAddress(byronAddrString);

// Create Byron address object
if (isValidByron) {
    ByronAddress byronAddress = new ByronAddress(byronAddrString);
    
    // Get address type
    AddressType type = byronAddress.getAddressType(); // AddressType.Byron
    
    // Get raw bytes
    byte[] addressBytes = byronAddress.getBytes();
    
    // Byron addresses can receive payments but have limitations
    System.out.println("Byron address: " + byronAddress.getAddress());
}
```

#### Migration Considerations

```java
public class LegacyAddressHandler {
    
    public boolean canReceivePayments(String address) {
        if (ByronAddress.isValidAddress(address)) {
            // Byron addresses can receive but have limitations
            return true;
        }
        
        if (Address.isValidAddress(address)) {
            // Modern Shelley addresses (preferred)
            return true;
        }
        
        return false;
    }
    
    public void migrateFromByron(String byronAddress, Account modernAccount) {
        // Move funds from Byron to modern address
        Address modernAddress = modernAccount.baseAddress();
        
        Tx migrationTx = new Tx()
            .payToAddress(modernAddress, getAllFunds(byronAddress))
            .from(byronAddress);
            
        // Note: You'll need the Byron private key to sign this transaction
    }
}
```

## Address Validation and Utilities

### Comprehensive Address Validation

```java
import com.bloxbean.cardano.client.address.util.AddressUtil;

public class AddressValidator {
    
    public AddressValidationResult validateAddress(String addressString) {
        // Check if it's a valid Cardano address (any type)
        if (!AddressUtil.isValidAddress(addressString)) {
            return AddressValidationResult.invalid("Invalid address format");
        }
        
        // Determine address type
        if (ByronAddress.isValidAddress(addressString)) {
            return validateByronAddress(addressString);
        } else {
            return validateShelleyAddress(addressString);
        }
    }
    
    private AddressValidationResult validateShelleyAddress(String addressString) {
        try {
            Address address = new Address(addressString);
            
            AddressType type = address.getAddressType();
            Network network = address.getNetwork();
            
            // Extract credentials
            Optional<byte[]> paymentCredential = address.getPaymentCredentialHash();
            Optional<byte[]> stakeCredential = address.getDelegationCredentialHash();
            
            // Validate credential formats
            if (paymentCredential.isPresent() && paymentCredential.get().length != 28) {
                return AddressValidationResult.invalid("Invalid payment credential length");
            }
            
            if (stakeCredential.isPresent() && stakeCredential.get().length != 28) {
                return AddressValidationResult.invalid("Invalid stake credential length");
            }
            
            return AddressValidationResult.valid(type, network);
            
        } catch (Exception e) {
            return AddressValidationResult.invalid("Failed to parse address: " + e.getMessage());
        }
    }
    
    private AddressValidationResult validateByronAddress(String addressString) {
        ByronAddress byronAddress = new ByronAddress(addressString);
        return AddressValidationResult.valid(AddressType.Byron, null);
    }
}

public class AddressValidationResult {
    private final boolean valid;
    private final String error;
    private final AddressType type;
    private final Network network;
    
    public static AddressValidationResult valid(AddressType type, Network network) {
        return new AddressValidationResult(true, null, type, network);
    }
    
    public static AddressValidationResult invalid(String error) {
        return new AddressValidationResult(false, error, null, null);
    }
    
    // Constructor and getters...
}
```

### Address Analysis and Introspection

```java
public class AddressAnalyzer {
    
    public AddressInfo analyzeAddress(String addressString) {
        if (!AddressUtil.isValidAddress(addressString)) {
            throw new IllegalArgumentException("Invalid address");
        }
        
        if (ByronAddress.isValidAddress(addressString)) {
            return analyzeByronAddress(addressString);
        } else {
            return analyzeShelleyAddress(addressString);
        }
    }
    
    private AddressInfo analyzeShelleyAddress(String addressString) {
        Address address = new Address(addressString);
        
        AddressInfo.Builder builder = AddressInfo.builder()
            .address(addressString)
            .type(address.getAddressType())
            .network(address.getNetwork())
            .encoding("Bech32");
        
        // Analyze payment credentials
        if (address.getPaymentCredentialHash().isPresent()) {
            boolean isKeyHash = address.isPubKeyHashInPaymentPart();
            builder.paymentCredential(
                isKeyHash ? "Key Hash" : "Script Hash",
                Hex.encodeHexString(address.getPaymentCredentialHash().get())
            );
        }
        
        // Analyze stake credentials
        if (address.getDelegationCredentialHash().isPresent()) {
            boolean isKeyHash = address.isStakeKeyHashInDelegationPart();
            builder.stakeCredential(
                isKeyHash ? "Key Hash" : "Script Hash",
                Hex.encodeHexString(address.getDelegationCredentialHash().get())
            );
        }
        
        // Special handling for pointer addresses
        if (address.getAddressType() == AddressType.Ptr) {
            PointerAddress pointerAddr = new PointerAddress(addressString);
            Pointer pointer = pointerAddr.getPointer();
            builder.pointer(pointer.getSlot(), pointer.getTxIndex(), pointer.getCertIndex());
        }
        
        return builder.build();
    }
    
    private AddressInfo analyzeByronAddress(String addressString) {
        return AddressInfo.builder()
            .address(addressString)
            .type(AddressType.Byron)
            .network(null) // Byron addresses don't have clear network indicators
            .encoding("Base58")
            .build();
    }
}
```

### Address Conversion Utilities

```java
public class AddressConverter {
    
    // Convert between different representations
    public String addressToHex(String addressString) {
        byte[] addressBytes = AddressUtil.addressToBytes(addressString);
        return Hex.encodeHexString(addressBytes);
    }
    
    public String hexToAddress(String hexString) {
        byte[] addressBytes = Hex.decodeHex(hexString);
        return AddressUtil.bytesToAddress(addressBytes);
    }
    
    // Extract just the bech32 prefix
    public String getAddressPrefix(String addressString) {
        if (ByronAddress.isValidAddress(addressString)) {
            return "byron"; // Byron addresses don't have prefixes
        }
        
        Address address = new Address(addressString);
        Network network = address.getNetwork();
        AddressType type = address.getAddressType();
        
        if (type == AddressType.Reward) {
            return network == Networks.mainnet() ? "stake" : "stake_test";
        } else {
            return network == Networks.mainnet() ? "addr" : "addr_test";
        }
    }
    
    // Check network compatibility
    public boolean isMainnetAddress(String addressString) {
        if (ByronAddress.isValidAddress(addressString)) {
            return true; // Assume Byron addresses are mainnet
        }
        
        Address address = new Address(addressString);
        return address.getNetwork() == Networks.mainnet();
    }
    
    public boolean isTestnetAddress(String addressString) {
        if (ByronAddress.isValidAddress(addressString)) {
            return false; // Byron addresses are typically mainnet
        }
        
        Address address = new Address(addressString);
        return address.getNetwork() != Networks.mainnet();
    }
}
```

## Network-Specific Considerations

### Mainnet vs Testnet Addresses

```java
public class NetworkAddressManager {
    
    public Address createForNetwork(HdPublicKey paymentKey, HdPublicKey stakeKey, Network network) {
        return AddressProvider.getBaseAddress(paymentKey, stakeKey, network);
    }
    
    public void demonstrateNetworkDifferences() {
        HdPublicKey paymentKey = // ... same key
        HdPublicKey stakeKey = // ... same key
        
        // Same keys, different networks = different addresses
        Address mainnetAddr = createForNetwork(paymentKey, stakeKey, Networks.mainnet());
        Address testnetAddr = createForNetwork(paymentKey, stakeKey, Networks.testnet());
        
        System.out.println("Mainnet: " + mainnetAddr.getAddress()); // addr1...
        System.out.println("Testnet: " + testnetAddr.getAddress()); // addr_test1...
    }
    
    public boolean isCompatibleWithBackend(String address, Network backendNetwork) {
        if (ByronAddress.isValidAddress(address)) {
            // Byron addresses are typically mainnet
            return backendNetwork == Networks.mainnet();
        }
        
        Address addr = new Address(address);
        return addr.getNetwork() == backendNetwork;
    }
}
```

### Address Prefixes by Network

| Network | Base/Enterprise/Pointer | Reward |
|---------|------------------------|--------|
| **Mainnet** | `addr1...` | `stake1...` |
| **Testnet** | `addr_test1...` | `stake_test1...` |
| **Preprod** | `addr_test1...` | `stake_test1...` |
| **Preview** | `addr_test1...` | `stake_test1...` |

## Integration with QuickTx

### Address-Aware Transaction Building

```java
public class AddressAwareTransactionBuilder {
    
    public Result<String> smartTransfer(String fromAddress, String toAddress, Amount amount) {
        // Validate addresses
        if (!AddressUtil.isValidAddress(fromAddress) || !AddressUtil.isValidAddress(toAddress)) {
            throw new IllegalArgumentException("Invalid address provided");
        }
        
        // Check network compatibility
        ensureNetworkCompatibility(fromAddress, toAddress);
        
        // Build transaction based on address types
        Tx tx = new Tx()
            .payToAddress(toAddress, amount)
            .from(fromAddress);
            
        // Use different strategies based on address types
        if (isEnterpriseAddress(fromAddress)) {
            // Enterprise addresses might need different fee strategies
            tx = tx.feeCalculator(new OptimizedFeeCalculator());
        }
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .completeAndWait();
    }
    
    private void ensureNetworkCompatibility(String addr1, String addr2) {
        Network network1 = getAddressNetwork(addr1);
        Network network2 = getAddressNetwork(addr2);
        
        if (network1 != null && network2 != null && !network1.equals(network2)) {
            throw new IllegalArgumentException("Address network mismatch");
        }
    }
    
    private Network getAddressNetwork(String addressString) {
        if (ByronAddress.isValidAddress(addressString)) {
            return Networks.mainnet(); // Assume mainnet for Byron
        }
        return new Address(addressString).getNetwork();
    }
    
    private boolean isEnterpriseAddress(String addressString) {
        if (ByronAddress.isValidAddress(addressString)) {
            return false;
        }
        return new Address(addressString).getAddressType() == AddressType.Enterprise;
    }
}
```

## Best Practices and Recommendations

### Address Selection Guidelines

**Use Base Addresses When:**
- ✅ Building personal wallets
- ✅ Applications need staking rewards
- ✅ Users want full Cardano functionality
- ✅ Long-term address usage

**Use Enterprise Addresses When:**
- ✅ Payment processing (exchanges, merchants)
- ✅ Temporary or single-use addresses  
- ✅ Transaction cost optimization
- ✅ No staking requirements

**Use Pointer Addresses When:**
- ✅ Multiple addresses need same delegation
- ✅ Optimizing transaction size
- ✅ Corporate/institutional scenarios
- ✅ Space efficiency is important

**Use Reward Addresses When:**
- ✅ Staking operations only
- ✅ Pool registration/updates
- ✅ Governance participation
- ✅ Reward collection

### Security Considerations

```java
public class SecureAddressHandler {
    
    // Always validate user-provided addresses
    public void processUserAddress(String userAddress) {
        if (!AddressUtil.isValidAddress(userAddress)) {
            throw new SecurityException("Invalid address format");
        }
        
        // Check for known network
        if (!isKnownNetwork(userAddress)) {
            throw new SecurityException("Unknown network");
        }
        
        // Additional validation...
    }
    
    // Prevent network confusion attacks
    public void ensureCorrectNetwork(String address, Network expectedNetwork) {
        if (ByronAddress.isValidAddress(address)) {
            if (expectedNetwork != Networks.mainnet()) {
                throw new SecurityException("Byron addresses only supported on mainnet");
            }
            return;
        }
        
        Address addr = new Address(address);
        if (!addr.getNetwork().equals(expectedNetwork)) {
            throw new SecurityException("Address network mismatch");
        }
    }
    
    private boolean isKnownNetwork(String address) {
        if (ByronAddress.isValidAddress(address)) {
            return true; // Byron is known
        }
        
        Address addr = new Address(address);
        Network network = addr.getNetwork();
        
        return network.equals(Networks.mainnet()) || 
               network.equals(Networks.testnet()) ||
               network.equals(Networks.preprod()) ||
               network.equals(Networks.preview());
    }
}
```

## Testing and Development

### Address Testing Patterns

```java
@Test
public class AddressTypeTests {
    
    @Test
    public void testAddressTypeGeneration() {
        Account account = Account.createFromMnemonic(Networks.testnet(), TEST_MNEMONIC);
        
        // Test base address
        Address baseAddr = AddressProvider.getBaseAddress(
            account.hdKeyPair().getPublicKey(),
            account.stakeHdKeyPair().getPublicKey(),
            Networks.testnet()
        );
        assertEquals(AddressType.Base, baseAddr.getAddressType());
        assertTrue(baseAddr.getAddress().startsWith("addr_test"));
        
        // Test enterprise address
        Address entAddr = AddressProvider.getEntAddress(
            account.hdKeyPair().getPublicKey(),
            Networks.testnet()
        );
        assertEquals(AddressType.Enterprise, entAddr.getAddressType());
        assertFalse(entAddr.getDelegationCredentialHash().isPresent());
        
        // Test reward address
        Address rewardAddr = AddressProvider.getRewardAddress(
            account.stakeHdKeyPair().getPublicKey(),
            Networks.testnet()
        );
        assertEquals(AddressType.Reward, rewardAddr.getAddressType());
        assertTrue(rewardAddr.getAddress().startsWith("stake_test"));
    }
    
    @Test
    public void testAddressValidation() {
        String validBase = "addr_test1qq...";
        String validEnterprise = "addr_test1vp...";
        String validReward = "stake_test1up...";
        String validByron = "DdzFFzCqrh...";
        String invalid = "invalid_address";
        
        assertTrue(AddressUtil.isValidAddress(validBase));
        assertTrue(AddressUtil.isValidAddress(validEnterprise));
        assertTrue(AddressUtil.isValidAddress(validReward));
        assertTrue(AddressUtil.isValidAddress(validByron));
        assertFalse(AddressUtil.isValidAddress(invalid));
    }
    
    @Test
    public void testNetworkCompatibility() {
        // Same key, different networks
        HdPublicKey key = Account.createFromMnemonic(Networks.testnet(), TEST_MNEMONIC)
            .hdKeyPair().getPublicKey();
            
        Address mainnetAddr = AddressProvider.getEntAddress(key, Networks.mainnet());
        Address testnetAddr = AddressProvider.getEntAddress(key, Networks.testnet());
        
        // Should be different addresses
        assertNotEquals(mainnetAddr.getAddress(), testnetAddr.getAddress());
        
        // But same credential hash
        assertEquals(
            mainnetAddr.getPaymentCredentialHash().get(),
            testnetAddr.getPaymentCredentialHash().get()
        );
    }
}
```

## Troubleshooting Common Issues

### Issue: "Invalid address format"

```java
// Problem: Mixed network addresses
String mainnetAddr = "addr1...";
String testnetAddr = "addr_test1...";

// Solution: Validate network consistency
public void validateNetworkConsistency(List<String> addresses) {
    Network expectedNetwork = null;
    
    for (String addr : addresses) {
        if (!AddressUtil.isValidAddress(addr)) {
            throw new IllegalArgumentException("Invalid address: " + addr);
        }
        
        Network addrNetwork = getAddressNetwork(addr);
        if (expectedNetwork == null) {
            expectedNetwork = addrNetwork;
        } else if (!expectedNetwork.equals(addrNetwork)) {
            throw new IllegalArgumentException("Network mismatch in address list");
        }
    }
}
```

### Issue: "Cannot extract credentials"

```java
// Problem: Trying to get stake credentials from enterprise address
Address enterpriseAddr = AddressProvider.getEntAddress(key, Networks.mainnet());
Optional<byte[]> stakeCredential = enterpriseAddr.getDelegationCredentialHash(); // Empty!

// Solution: Check address type first
public Optional<byte[]> safeGetStakeCredential(Address address) {
    AddressType type = address.getAddressType();
    
    if (type == AddressType.Enterprise || type == AddressType.Byron) {
        return Optional.empty(); // These don't have stake credentials
    }
    
    return address.getDelegationCredentialHash();
}
```

### Issue: "Address doesn't match expected type"

```java
// Problem: Assuming all addresses can stake
public void delegateAddress(String address) {
    Address addr = new Address(address);
    // This will fail for enterprise addresses!
    byte[] stakeCredential = addr.getDelegationCredentialHash().get();
}

// Solution: Check capabilities first
public boolean canDelegate(String addressString) {
    if (ByronAddress.isValidAddress(addressString)) {
        return false; // Byron addresses can't delegate
    }
    
    Address address = new Address(addressString);
    AddressType type = address.getAddressType();
    
    return type == AddressType.Base || type == AddressType.Ptr;
}
```

## Summary and Next Steps

Cardano's address system provides powerful flexibility for different use cases:

- **Base addresses** for full functionality with staking
- **Enterprise addresses** for payment-focused applications  
- **Pointer addresses** for space-efficient delegation
- **Reward addresses** for staking operations
- **Byron addresses** for legacy compatibility

### Key Takeaways

✅ **Choose the right address type** for your specific use case  
✅ **Always validate addresses** before using them  
✅ **Consider network compatibility** in multi-address operations  
✅ **Understand credential requirements** for different operations  
✅ **Test with testnets** during development  

### Next Steps

Now that you understand address types, explore:

- **[HD Wallets & Accounts](./hd-wallets.md)** - Account management and derivation
- **[First Transaction](../../quickstart/first-transaction.md)** - Using addresses in transactions  
- **Cryptographic Operations** - Key management and signing (coming soon)
- **Native Scripts** - Multi-signature address patterns (coming soon)

## Resources

- **[Cardano Address Specification](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0019)** - Official address format specification
- **[Bech32 Specification](https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki)** - Address encoding standard
- **[Examples Repository](https://github.com/bloxbean/cardano-client-examples)** - Complete working examples
- **[JavaDoc API Reference](https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-core/latest/index.html)** - Detailed API documentation

---

**Remember**: Address types are fundamental to Cardano application architecture. Choose the right type for your use case, validate inputs properly, and test thoroughly across different networks.
---
description: Complete guide to Cardano native scripts including multi-signature patterns, time constraints, script composition, and policy derivation
sidebar_label: Native Scripts Overview
sidebar_position: 1
---

# Native Scripts Comprehensive Guide

Native scripts are Cardano's built-in scripting language for expressing spending conditions without requiring Plutus smart contracts. They provide a simple, efficient way to create multi-signature wallets, time-locked contracts, and token minting policies while maintaining excellent performance and predictable costs.

## What are Native Scripts?

Native scripts are simple, declarative programs that specify conditions under which UTXOs can be spent or tokens can be minted. Unlike Plutus scripts, native scripts:

- **Execute off-chain** during transaction construction
- **Have predictable costs** with no execution fees
- **Provide limited but powerful functionality** for common use cases
- **Are validated by the ledger** using simple rules

### Key Benefits

✅ **Simple and Safe** - Limited functionality reduces attack surface  
✅ **Predictable Costs** - No script execution fees or memory limits  
✅ **Efficient** - Fast validation and compact representation  
✅ **Composable** - Scripts can be nested and combined  
✅ **Multi-signature Support** - Built-in support for M-of-N signatures  
✅ **Time Constraints** - Native support for time-locked conditions  

## Native Script Types

Cardano supports six native script types that can be combined to create complex conditions:

### 1. 🔑 ScriptPubkey (Single Signature)

Requires a signature from a specific public key.

```java
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.crypto.Keys;

// Create from existing key
Keys signerKeys = KeyGenUtil.generateKey();
ScriptPubkey singleSig = ScriptPubkey.create(signerKeys.getVkey());

// Create and generate new key pair
ScriptPubkey newSig = ScriptPubkey.createWithNewKey();
Keys newKeys = newSig.getKeys(); // Get the associated keys

// Create from key hash (28 bytes)
String keyHash = "a1b2c3d4..."; // 28-byte key hash in hex
ScriptPubkey fromHash = ScriptPubkey.builder()
    .keyHash(keyHash)
    .build();

System.out.println("Script hash: " + singleSig.getScriptHash());
System.out.println("Policy ID: " + singleSig.getPolicyId());
```

### 2. 🔒 ScriptAll (AND Logic)

Requires ALL sub-scripts to be satisfied.

```java
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAll;

// Create 2-of-2 multi-signature (both signatures required)
ScriptPubkey alice = ScriptPubkey.createWithNewKey();
ScriptPubkey bob = ScriptPubkey.createWithNewKey();

ScriptAll bothRequired = ScriptAll.builder()
    .nativeScripts(Arrays.asList(alice, bob))
    .build();

// Fluent API
ScriptAll fluentAll = new ScriptAll()
    .addScript(alice)
    .addScript(bob);

// All conditions must be met
System.out.println("Policy ID: " + bothRequired.getPolicyId());
```

### 3. 🔓 ScriptAny (OR Logic)

Requires ANY ONE sub-script to be satisfied.

```java
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAny;

// Either Alice OR Bob can spend
ScriptPubkey alice = ScriptPubkey.createWithNewKey();
ScriptPubkey bob = ScriptPubkey.createWithNewKey();

ScriptAny eitherSigner = ScriptAny.builder()
    .nativeScripts(Arrays.asList(alice, bob))
    .build();

// Fluent API
ScriptAny fluentAny = new ScriptAny()
    .addScript(alice)
    .addScript(bob);

// Only one condition needs to be met
System.out.println("Either Alice or Bob can spend");
```

### 4. 🎯 ScriptAtLeast (M-of-N Signatures)

Requires M signatures from N possible signers.

```java
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAtLeast;

// 2-of-3 multi-signature wallet
ScriptPubkey alice = ScriptPubkey.createWithNewKey();
ScriptPubkey bob = ScriptPubkey.createWithNewKey();
ScriptPubkey charlie = ScriptPubkey.createWithNewKey();

ScriptAtLeast twoOfThree = ScriptAtLeast.builder()
    .required(BigInteger.valueOf(2))  // Require 2 signatures
    .nativeScripts(Arrays.asList(alice, bob, charlie))
    .build();

// Fluent API
ScriptAtLeast fluentAtLeast = new ScriptAtLeast(2)  // Require 2 signatures
    .addScript(alice)
    .addScript(bob)
    .addScript(charlie);

System.out.println("2-of-3 multi-sig policy: " + twoOfThree.getPolicyId());
```

### 5. ⏰ RequireTimeBefore (Time Upper Bound)

Valid only before a specific slot.

```java
import com.bloxbean.cardano.client.transaction.spec.script.RequireTimeBefore;

// Valid only before slot 1000000 (expires at this slot)
long expirySlot = 1000000;
RequireTimeBefore expiry = RequireTimeBefore.builder()
    .after(BigInteger.valueOf(expirySlot))
    .build();

// Fluent API
RequireTimeBefore fluentExpiry = new RequireTimeBefore(expirySlot);

System.out.println("Script expires at slot: " + expirySlot);
```

### 6. ⏳ RequireTimeAfter (Time Lower Bound)

Valid only after a specific slot.

```java
import com.bloxbean.cardano.client.transaction.spec.script.RequireTimeAfter;

// Valid only after slot 500000 (becomes active at this slot)
long activationSlot = 500000;
RequireTimeAfter activation = RequireTimeAfter.builder()
    .after(BigInteger.valueOf(activationSlot))
    .build();

// Fluent API
RequireTimeAfter fluentActivation = new RequireTimeAfter(activationSlot);

System.out.println("Script becomes active at slot: " + activationSlot);
```

## Script Composition and Nesting

Native scripts can be composed and nested to create complex conditions:

### Multi-Signature with Time Constraints

```java
public class CompositeScriptExamples {
    
    public NativeScript createTimeLimitedMultiSig() {
        // Create signers
        ScriptPubkey alice = ScriptPubkey.createWithNewKey();
        ScriptPubkey bob = ScriptPubkey.createWithNewKey();
        ScriptPubkey charlie = ScriptPubkey.createWithNewKey();
        
        // Current slot + 1 week (assuming 1 slot = 1 second)
        long expirySlot = getCurrentSlot() + (7 * 24 * 60 * 60);
        
        // Require 2-of-3 signatures AND must be before expiry
        ScriptAll timeLimitedMultiSig = new ScriptAll()
            .addScript(new ScriptAtLeast(2)
                .addScript(alice)
                .addScript(bob)
                .addScript(charlie))
            .addScript(new RequireTimeBefore(expirySlot));
        
        return timeLimitedMultiSig;
    }
    
    public NativeScript createEscrowScript() {
        ScriptPubkey buyer = ScriptPubkey.createWithNewKey();
        ScriptPubkey seller = ScriptPubkey.createWithNewKey();
        ScriptPubkey arbitrator = ScriptPubkey.createWithNewKey();
        
        long escrowTimeout = getCurrentSlot() + (30 * 24 * 60 * 60); // 30 days
        
        // Either (buyer AND seller) OR (arbitrator after timeout)
        ScriptAny escrowConditions = new ScriptAny()
            .addScript(new ScriptAll()
                .addScript(buyer)
                .addScript(seller))
            .addScript(new ScriptAll()
                .addScript(arbitrator)
                .addScript(new RequireTimeAfter(escrowTimeout)));
        
        return escrowConditions;
    }
    
    public NativeScript createVestingScript() {
        ScriptPubkey beneficiary = ScriptPubkey.createWithNewKey();
        
        // Vesting schedule: can spend 25% every 3 months
        long quarterlySlots = 90 * 24 * 60 * 60; // 90 days in slots
        long startSlot = getCurrentSlot();
        
        // This is a simplified example - real vesting would need multiple UTXOs
        ScriptAll vestingScript = new ScriptAll()
            .addScript(beneficiary)
            .addScript(new RequireTimeAfter(startSlot + quarterlySlots));
        
        return vestingScript;
    }
}
```

### Complex Governance Script

```java
public NativeScript createGovernanceScript() {
    // Board members
    ScriptPubkey chair = ScriptPubkey.createWithNewKey();
    ScriptPubkey cto = ScriptPubkey.createWithNewKey();
    ScriptPubkey cfo = ScriptPubkey.createWithNewKey();
    
    // Emergency contacts
    ScriptPubkey emergencyKey1 = ScriptPubkey.createWithNewKey();
    ScriptPubkey emergencyKey2 = ScriptPubkey.createWithNewKey();
    
    long emergencyDelay = getCurrentSlot() + (7 * 24 * 60 * 60); // 7 days
    
    // Complex governance: Either majority of board OR emergency protocol
    ScriptAny governanceScript = new ScriptAny()
        // Normal governance: 2-of-3 board members
        .addScript(new ScriptAtLeast(2)
            .addScript(chair)
            .addScript(cto)
            .addScript(cfo))
        // Emergency governance: both emergency keys after delay
        .addScript(new ScriptAll()
            .addScript(new RequireTimeAfter(emergencyDelay))
            .addScript(emergencyKey1)
            .addScript(emergencyKey2));
    
    return governanceScript;
}
```

## Policy ID Derivation

Policy IDs are derived from native scripts using Blake2b-224 hashing:

```java
public class PolicyDerivation {
    
    public void demonstratePolicyDerivation() {
        // Create a simple minting policy
        ScriptPubkey mintingKey = ScriptPubkey.createWithNewKey();
        
        // Get the script hash (28 bytes)
        byte[] scriptHash = mintingKey.getScriptHash();
        String scriptHashHex = Hex.encodeHexString(scriptHash);
        
        // Policy ID is the same as script hash
        String policyId = mintingKey.getPolicyId();
        
        System.out.println("Script hash: " + scriptHashHex);
        System.out.println("Policy ID: " + policyId);
        System.out.println("Same value: " + scriptHashHex.equals(policyId));
    }
    
    public void demonstrateComplexPolicyDerivation() {
        // Time-limited minting policy
        ScriptPubkey mintingKey = ScriptPubkey.createWithNewKey();
        long mintingDeadline = getCurrentSlot() + (30 * 24 * 60 * 60); // 30 days
        
        ScriptAll timeLimitedMinting = new ScriptAll()
            .addScript(mintingKey)
            .addScript(new RequireTimeBefore(mintingDeadline));
        
        String policyId = timeLimitedMinting.getPolicyId();
        
        System.out.println("Time-limited policy ID: " + policyId);
        System.out.println("Minting expires at slot: " + mintingDeadline);
    }
    
    public String createOneTimeMintingPolicy() {
        // Policy that can only mint once by burning a specific UTxO
        ScriptPubkey mintingKey = ScriptPubkey.createWithNewKey();
        
        // This would typically reference a specific UTxO that gets consumed
        // For simplicity, we'll create a basic time-limited policy
        long oneTimeWindow = getCurrentSlot() + (24 * 60 * 60); // 24 hours
        
        ScriptAll oneTimePolicy = new ScriptAll()
            .addScript(mintingKey)
            .addScript(new RequireTimeBefore(oneTimeWindow));
        
        return oneTimePolicy.getPolicyId();
    }
}
```

## Working with Policies

```java
import com.bloxbean.cardano.client.transaction.spec.Policy;

public class PolicyUsage {
    
    public Policy createMintingPolicy() {
        // Create script and keys
        ScriptPubkey mintingScript = ScriptPubkey.createWithNewKey();
        Keys mintingKeys = mintingScript.getKeys();
        
        // Create policy with signing capability
        Policy mintingPolicy = Policy.builder()
            .script(mintingScript)
            .secretKeys(Arrays.asList(mintingKeys.getSkey()))
            .build();
        
        return mintingPolicy;
    }
    
    public Policy createMultiSigMintingPolicy() {
        // 2-of-3 multi-signature minting
        ScriptPubkey key1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey key2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey key3 = ScriptPubkey.createWithNewKey();
        
        ScriptAtLeast multiSigScript = new ScriptAtLeast(2)
            .addScript(key1)
            .addScript(key2)
            .addScript(key3);
        
        // Include secret keys for signing
        Policy multiSigPolicy = Policy.builder()
            .script(multiSigScript)
            .secretKeys(Arrays.asList(
                key1.getKeys().getSkey(),
                key2.getKeys().getSkey()
                // Only need 2 keys for 2-of-3
            ))
            .build();
        
        return multiSigPolicy;
    }
    
    public void demonstratePolicyUsage(Policy policy) {
        String policyId = policy.getPolicyId();
        System.out.println("Policy ID: " + policyId);
        
        // Use with QuickTx for minting
        Asset customToken = Asset.builder()
            .policyId(policyId)
            .name("MyToken")
            .value(BigInteger.valueOf(1000000))
            .build();
        
        System.out.println("Created asset: " + customToken.getAssetName());
    }
}
```

## Script Serialization and CBOR

```java
public class ScriptSerialization {
    
    public void demonstrateSerialization() throws Exception {
        // Create a complex script
        ScriptAll complexScript = new ScriptAll()
            .addScript(new ScriptAtLeast(2)
                .addScript(ScriptPubkey.createWithNewKey())
                .addScript(ScriptPubkey.createWithNewKey())
                .addScript(ScriptPubkey.createWithNewKey()))
            .addScript(new RequireTimeBefore(getCurrentSlot() + 10000));
        
        // Serialize to CBOR
        byte[] cborBytes = complexScript.serialize();
        String cborHex = Hex.encodeHexString(cborBytes);
        
        System.out.println("CBOR serialization: " + cborHex);
        
        // Serialize as data item (for embedding in other structures)
        DataItem dataItem = complexScript.serializeAsDataItem();
        
        // Get script reference bytes (for UTxO script references)
        byte[] scriptRefBytes = complexScript.scriptRefBytes();
        
        System.out.println("Script ref bytes: " + Hex.encodeHexString(scriptRefBytes));
    }
    
    public void demonstrateDeserialization() throws Exception {
        String cborHex = "..."; // CBOR hex from serialization
        byte[] cborBytes = Hex.decodeHex(cborHex);
        
        // Deserialize from CBOR
        NativeScript deserializedScript = NativeScript.deserialize(cborBytes);
        
        System.out.println("Deserialized script type: " + deserializedScript.getClass().getSimpleName());
        System.out.println("Policy ID: " + deserializedScript.getPolicyId());
    }
    
    public void demonstrateJsonSerialization() throws Exception {
        ScriptAtLeast script = new ScriptAtLeast(2)
            .addScript(ScriptPubkey.createWithNewKey())
            .addScript(ScriptPubkey.createWithNewKey());
        
        // Serialize to JSON
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(script);
        
        System.out.println("JSON representation: " + json);
        
        // Deserialize from JSON
        NativeScript fromJson = mapper.readValue(json, NativeScript.class);
        System.out.println("Deserialized policy ID: " + fromJson.getPolicyId());
    }
}
```

## Common Multi-Signature Patterns

### 2-of-3 Multi-Signature Wallet

```java
public class MultiSigWallet {
    
    public static class MultiSigWalletResult {
        private final ScriptAtLeast script;
        private final List<Keys> allKeys;
        private final Address walletAddress;
        
        // Constructor and getters...
    }
    
    public MultiSigWalletResult create2of3Wallet(Network network) {
        // Generate three key pairs
        ScriptPubkey key1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey key2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey key3 = ScriptPubkey.createWithNewKey();
        
        // Create 2-of-3 script
        ScriptAtLeast multiSigScript = new ScriptAtLeast(2)
            .addScript(key1)
            .addScript(key2)
            .addScript(key3);
        
        // Generate script address
        Address walletAddress = AddressProvider.getEntAddress(
            multiSigScript, 
            network
        );
        
        // Collect all keys
        List<Keys> allKeys = Arrays.asList(
            key1.getKeys(),
            key2.getKeys(),
            key3.getKeys()
        );
        
        return new MultiSigWalletResult(multiSigScript, allKeys, walletAddress);
    }
    
    public Result<String> spendFromWallet(
            MultiSigWalletResult wallet,
            String toAddress,
            Amount amount,
            List<Keys> signingKeys) {
        
        if (signingKeys.size() < 2) {
            return Result.error("Need at least 2 signatures for 2-of-3 wallet");
        }
        
        // Build transaction
        Tx tx = new Tx()
            .payToAddress(toAddress, amount)
            .from(wallet.getWalletAddress().getAddress())
            .attachSpendingScript(wallet.getScript());
        
        // Create signers for the required keys
        List<TransactionSigner> signers = signingKeys.stream()
            .map(SignerProviders::signerFrom)
            .collect(Collectors.toList());
        
        // Build and submit
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        return builder.compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
}
```

### Corporate Treasury with Time Delays

```java
public class CorporateTreasury {
    
    public NativeScript createTreasuryScript() {
        // Executive team (any can approve small amounts)
        ScriptPubkey ceo = ScriptPubkey.createWithNewKey();
        ScriptPubkey cfo = ScriptPubkey.createWithNewKey();
        ScriptPubkey cto = ScriptPubkey.createWithNewKey();
        
        // Board (majority needed for large amounts)
        ScriptPubkey boardMember1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey boardMember2 = ScriptPubkey.createWithNewKey();
        ScriptPubkey boardMember3 = ScriptPubkey.createWithNewKey();
        ScriptPubkey boardMember4 = ScriptPubkey.createWithNewKey();
        ScriptPubkey boardMember5 = ScriptPubkey.createWithNewKey();
        
        long emergencyDelay = getCurrentSlot() + (72 * 60 * 60); // 72 hours
        
        // Complex treasury governance
        ScriptAny treasuryScript = new ScriptAny()
            // Executive approval (any executive)
            .addScript(new ScriptAny()
                .addScript(ceo)
                .addScript(cfo)
                .addScript(cto))
            // Board majority (3 of 5) with time delay for security
            .addScript(new ScriptAll()
                .addScript(new RequireTimeAfter(emergencyDelay))
                .addScript(new ScriptAtLeast(3)
                    .addScript(boardMember1)
                    .addScript(boardMember2)
                    .addScript(boardMember3)
                    .addScript(boardMember4)
                    .addScript(boardMember5)));
        
        return treasuryScript;
    }
}
```

## Time-Locked Contracts

### Vesting Schedule Implementation

```java
public class VestingContract {
    
    public List<NativeScript> createVestingSchedule(
            ScriptPubkey beneficiary, 
            long startSlot, 
            int quarters) {
        
        List<NativeScript> vestingScripts = new ArrayList<>();
        long quarterlySlots = 90 * 24 * 60 * 60; // 90 days
        
        for (int i = 0; i < quarters; i++) {
            long vestingSlot = startSlot + (i * quarterlySlots);
            
            ScriptAll quarterlyVesting = new ScriptAll()
                .addScript(beneficiary)
                .addScript(new RequireTimeAfter(vestingSlot));
            
            vestingScripts.add(quarterlyVesting);
        }
        
        return vestingScripts;
    }
    
    public NativeScript createCliffVesting(
            ScriptPubkey beneficiary,
            long cliffSlot,
            long vestingSlot) {
        
        // No access until cliff, then gradual vesting
        ScriptAll cliffVesting = new ScriptAll()
            .addScript(beneficiary)
            .addScript(new RequireTimeAfter(cliffSlot))
            .addScript(new RequireTimeBefore(vestingSlot));
        
        return cliffVesting;
    }
}
```

### Escrow Contract

```java
public class EscrowContract {
    
    public NativeScript createEscrowScript(
            ScriptPubkey buyer,
            ScriptPubkey seller,
            ScriptPubkey arbitrator,
            long timeoutSlot) {
        
        // Escrow conditions:
        // 1. Both buyer and seller agree, OR
        // 2. Arbitrator decides after timeout
        ScriptAny escrowScript = new ScriptAny()
            // Mutual agreement
            .addScript(new ScriptAll()
                .addScript(buyer)
                .addScript(seller))
            // Arbitrator intervention after timeout
            .addScript(new ScriptAll()
                .addScript(arbitrator)
                .addScript(new RequireTimeAfter(timeoutSlot)));
        
        return escrowScript;
    }
    
    public Result<String> resolveEscrow(
            NativeScript escrowScript,
            String recipientAddress,
            Amount amount,
            EscrowResolution resolution) {
        
        Tx tx = new Tx()
            .payToAddress(recipientAddress, amount)
            .from(getEscrowAddress(escrowScript))
            .attachSpendingScript(escrowScript);
        
        List<TransactionSigner> signers = resolution.getSigners();
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
}
```

## Advanced Features and Utilities

### Policy Utilities

```java
import com.bloxbean.cardano.client.util.PolicyUtil;

public class PolicyUtilities {
    
    public void demonstratePolicyUtils() {
        // Create simple policies using utility methods
        List<Keys> signingKeys = Arrays.asList(
            KeyGenUtil.generateKey(),
            KeyGenUtil.generateKey(),
            KeyGenUtil.generateKey()
        );
        
        // All signatures required
        Policy allRequiredPolicy = PolicyUtil.createMultiSigScriptAllPolicy(
            "AllRequired", 
            signingKeys, 
            1 // slot
        );
        
        // 2-of-3 signatures
        Policy atLeastPolicy = PolicyUtil.createMultiSigScriptAtLeastPolicy(
            "TwoOfThree",
            2, // required signatures
            signingKeys,
            1 // slot
        );
        
        System.out.println("All required policy: " + allRequiredPolicy.getPolicyId());
        System.out.println("2-of-3 policy: " + atLeastPolicy.getPolicyId());
    }
    
    public Policy createTimeLockedPolicy(int epochDuration) {
        Keys mintingKey = KeyGenUtil.generateKey();
        
        Policy timeLockedPolicy = PolicyUtil.createEpochBasedTimeLockedPolicy(
            "TimeLocked",
            mintingKey,
            epochDuration,
            1 // current slot
        );
        
        return timeLockedPolicy;
    }
}
```

### Script Validation

```java
public class ScriptValidation {
    
    public boolean validateScript(NativeScript script) {
        try {
            // Check if script can be serialized
            byte[] serialized = script.serialize();
            
            // Check if it can be deserialized
            NativeScript deserialized = NativeScript.deserialize(serialized);
            
            // Check if policy IDs match
            String originalPolicyId = script.getPolicyId();
            String deserializedPolicyId = deserialized.getPolicyId();
            
            return originalPolicyId.equals(deserializedPolicyId);
            
        } catch (Exception e) {
            System.err.println("Script validation failed: " + e.getMessage());
            return false;
        }
    }
    
    public void validateScriptComplexity(NativeScript script) {
        int depth = calculateDepth(script, 0);
        int scriptCount = countScripts(script);
        
        System.out.println("Script depth: " + depth);
        System.out.println("Total scripts: " + scriptCount);
        
        if (depth > 10) {
            System.out.println("Warning: Deep nesting may affect performance");
        }
        
        if (scriptCount > 100) {
            System.out.println("Warning: Large number of scripts may affect size");
        }
    }
    
    private int calculateDepth(NativeScript script, int currentDepth) {
        if (script instanceof ScriptPubkey || 
            script instanceof RequireTimeBefore || 
            script instanceof RequireTimeAfter) {
            return currentDepth;
        }
        
        int maxDepth = currentDepth;
        
        if (script instanceof ScriptAll) {
            ScriptAll scriptAll = (ScriptAll) script;
            for (NativeScript subScript : scriptAll.getNativeScripts()) {
                maxDepth = Math.max(maxDepth, calculateDepth(subScript, currentDepth + 1));
            }
        }
        
        // Similar logic for ScriptAny and ScriptAtLeast...
        
        return maxDepth;
    }
    
    private int countScripts(NativeScript script) {
        if (script instanceof ScriptPubkey || 
            script instanceof RequireTimeBefore || 
            script instanceof RequireTimeAfter) {
            return 1;
        }
        
        int count = 1; // Count this script
        
        if (script instanceof ScriptAll) {
            ScriptAll scriptAll = (ScriptAll) script;
            for (NativeScript subScript : scriptAll.getNativeScripts()) {
                count += countScripts(subScript);
            }
        }
        
        // Similar logic for ScriptAny and ScriptAtLeast...
        
        return count;
    }
}
```

## Integration with QuickTx

### Using Scripts in Transactions

```java
public class ScriptTransactionExamples {
    
    public Result<String> spendFromScriptAddress(
            NativeScript script, 
            List<Keys> signingKeys,
            String recipient,
            Amount amount) {
        
        // Get script address
        Address scriptAddress = AddressProvider.getEntAddress(script, Networks.mainnet());
        
        // Build transaction
        Tx tx = new Tx()
            .payToAddress(recipient, amount)
            .from(scriptAddress.getAddress())
            .attachSpendingScript(script);
        
        // Create signers
        List<TransactionSigner> signers = signingKeys.stream()
            .map(SignerProviders::signerFrom)
            .collect(Collectors.toList());
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
    
    public Result<String> mintTokensWithScript(
            NativeScript mintingScript,
            List<Keys> signingKeys,
            String assetName,
            BigInteger quantity,
            String recipient) {
        
        // Create asset to mint
        String policyId = mintingScript.getPolicyId();
        Asset asset = Asset.builder()
            .policyId(policyId)
            .name(assetName)
            .value(quantity)
            .build();
        
        // Build minting transaction
        Tx tx = new Tx()
            .mintAssets(mintingScript, Arrays.asList(asset))
            .payToAddress(recipient, Amount.asset(asset))
            .from(getPaymentAddress());
        
        List<TransactionSigner> signers = signingKeys.stream()
            .map(SignerProviders::signerFrom)
            .collect(Collectors.toList());
        
        return new QuickTxBuilder(backendService)
            .compose(tx)
            .withSigners(signers)
            .completeAndWait();
    }
}
```

## Testing and Development

### Unit Testing Native Scripts

```java
@Test
public class NativeScriptTests {
    
    @Test
    public void testScriptSerialization() throws Exception {
        ScriptAtLeast script = new ScriptAtLeast(2)
            .addScript(ScriptPubkey.createWithNewKey())
            .addScript(ScriptPubkey.createWithNewKey())
            .addScript(ScriptPubkey.createWithNewKey());
        
        // Test CBOR serialization
        byte[] serialized = script.serialize();
        NativeScript deserialized = NativeScript.deserialize(serialized);
        
        assertEquals(script.getPolicyId(), deserialized.getPolicyId());
    }
    
    @Test
    public void testPolicyIdDerivation() {
        ScriptPubkey script1 = ScriptPubkey.createWithNewKey();
        ScriptPubkey script2 = ScriptPubkey.createWithNewKey();
        
        // Different scripts should have different policy IDs
        assertNotEquals(script1.getPolicyId(), script2.getPolicyId());
        
        // Same script should have same policy ID
        assertEquals(script1.getPolicyId(), script1.getPolicyId());
    }
    
    @Test
    public void testComplexScriptComposition() {
        ScriptAll complex = new ScriptAll()
            .addScript(new ScriptAtLeast(2)
                .addScript(ScriptPubkey.createWithNewKey())
                .addScript(ScriptPubkey.createWithNewKey())
                .addScript(ScriptPubkey.createWithNewKey()))
            .addScript(new RequireTimeBefore(1000000));
        
        assertNotNull(complex.getPolicyId());
        assertTrue(complex.getNativeScripts().size() == 2);
    }
    
    @Test
    public void testTimeConstraints() {
        long currentSlot = 500000;
        
        RequireTimeAfter after = new RequireTimeAfter(currentSlot);
        RequireTimeBefore before = new RequireTimeBefore(currentSlot + 1000);
        
        ScriptAll timeWindow = new ScriptAll()
            .addScript(after)
            .addScript(before)
            .addScript(ScriptPubkey.createWithNewKey());
        
        assertNotNull(timeWindow.getPolicyId());
    }
}
```

## Performance and Best Practices

### Optimization Guidelines

✅ **Keep scripts simple** - Avoid unnecessary nesting  
✅ **Use appropriate script types** - ScriptAny for OR, ScriptAll for AND  
✅ **Minimize script size** - Smaller scripts = lower transaction costs  
✅ **Cache policy IDs** - Computation is deterministic  
✅ **Test thoroughly** - Validate scripts with different key combinations  

### Common Patterns

```java
public class BestPractices {
    
    // ✅ Good: Simple and efficient
    public NativeScript createSimpleMultiSig() {
        return new ScriptAtLeast(2)
            .addScript(ScriptPubkey.createWithNewKey())
            .addScript(ScriptPubkey.createWithNewKey())
            .addScript(ScriptPubkey.createWithNewKey());
    }
    
    // ✅ Good: Logical grouping
    public NativeScript createLogicalGroups() {
        ScriptAny executives = new ScriptAny()
            .addScript(ScriptPubkey.createWithNewKey()) // CEO
            .addScript(ScriptPubkey.createWithNewKey()); // CFO
        
        ScriptAtLeast board = new ScriptAtLeast(3)
            .addScript(ScriptPubkey.createWithNewKey())
            .addScript(ScriptPubkey.createWithNewKey())
            .addScript(ScriptPubkey.createWithNewKey())
            .addScript(ScriptPubkey.createWithNewKey())
            .addScript(ScriptPubkey.createWithNewKey());
        
        return new ScriptAny()
            .addScript(executives)
            .addScript(board);
    }
    
    // ❌ Avoid: Unnecessary nesting
    public NativeScript avoidUnnecessaryNesting() {
        // Don't create single-item ScriptAll or ScriptAny
        return new ScriptAll()
            .addScript(ScriptPubkey.createWithNewKey()); // Just use ScriptPubkey directly
    }
    
    // ✅ Good: Efficient time constraints
    public NativeScript createTimeConstraints() {
        long currentSlot = getCurrentSlot();
        
        return new ScriptAll()
            .addScript(ScriptPubkey.createWithNewKey())
            .addScript(new RequireTimeAfter(currentSlot + 100))
            .addScript(new RequireTimeBefore(currentSlot + 10000));
    }
}
```

## Troubleshooting Common Issues

### Issue: Script Serialization Errors

```java
// Problem: Script with null values
ScriptAll problematicScript = new ScriptAll()
    .addScript(null); // ❌ Will cause serialization error

// Solution: Validate scripts before serialization
public boolean isValidScript(NativeScript script) {
    try {
        script.serialize();
        return true;
    } catch (Exception e) {
        System.err.println("Invalid script: " + e.getMessage());
        return false;
    }
}
```

### Issue: Incorrect Signing

```java
// Problem: Not enough signatures for ScriptAtLeast
ScriptAtLeast twoOfThree = new ScriptAtLeast(2)
    .addScript(ScriptPubkey.createWithNewKey())
    .addScript(ScriptPubkey.createWithNewKey())
    .addScript(ScriptPubkey.createWithNewKey());

// ❌ Only providing 1 signature for 2-of-3 script
List<Keys> insufficientKeys = Arrays.asList(key1);

// Solution: Provide enough signatures
List<Keys> sufficientKeys = Arrays.asList(key1, key2); // At least 2 keys
```

### Issue: Time Constraint Validation

```java
// Problem: Invalid time ranges
long currentSlot = getCurrentSlot();

// ❌ Invalid: before time is after after time
ScriptAll invalidTimeRange = new ScriptAll()
    .addScript(new RequireTimeAfter(currentSlot + 1000))
    .addScript(new RequireTimeBefore(currentSlot + 500)); // Before earlier time

// Solution: Validate time constraints
public boolean hasValidTimeRange(NativeScript script) {
    // Implementation to check time constraint logic
    return true; // Simplified
}
```

## Summary and Next Steps

Native scripts provide a powerful, efficient way to implement common spending conditions in Cardano:

### Key Takeaways

✅ **Six script types** cover most common use cases  
✅ **Composable design** enables complex conditions  
✅ **Predictable costs** with no execution fees  
✅ **Multi-signature support** built-in  
✅ **Time constraints** for temporal logic  
✅ **Policy derivation** for token minting  

### When to Use Native Scripts

- ✅ Multi-signature wallets
- ✅ Time-locked contracts
- ✅ Token minting policies
- ✅ Simple escrow contracts
- ✅ Governance mechanisms
- ✅ Vesting schedules

### When to Consider Plutus Scripts

- Complex business logic
- State-dependent conditions
- Advanced cryptographic operations
- Custom validation rules

### Next Steps

Now that you understand native scripts, explore:

- **[HD Wallets & Accounts](../accounts-and-addresses/hd-wallets.md)** - Account management for multi-sig
- **[Address Types](../accounts-and-addresses/address-types.md)** - Script addresses and validation
- **[Cryptographic Operations](../keys-and-crypto/cryptographic-operations.md)** - Key management for scripts
- **Smart Contracts** - Plutus scripts for advanced logic (coming soon)

## Resources

- **[Native Scripts Specification](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0032)** - Official native script format
- **[Multi-signature Patterns](https://docs.cardano.org/native-tokens/multi-signature-scripts)** - Common patterns and examples
- **[Examples Repository](https://github.com/bloxbean/cardano-client-examples)** - Complete working examples
- **[JavaDoc API Reference](https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-core/latest/index.html)** - Detailed API documentation

---

**Remember**: Native scripts excel at expressing simple, deterministic conditions. Choose the right script type for your use case, compose them logically, and always test your scripts thoroughly before deploying to mainnet.
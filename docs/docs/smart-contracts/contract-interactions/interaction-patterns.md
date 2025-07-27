---
description: Comprehensive guide to smart contract interaction patterns including lock/unlock, minting validators, state machines, and oracle integration
sidebar_label: Interaction Patterns
sidebar_position: 1
---

# Smart Contract Interaction Patterns

This comprehensive guide covers essential patterns for interacting with Plutus smart contracts using the Cardano Client Library. Learn how to implement lock/unlock scenarios, minting validators, state machines, validator chaining, and oracle integration patterns.

:::tip Prerequisites
Understanding of [PlutusData Types](../plutus-basics/plutus-data-types.md), [Script Execution](../plutus-basics/script-execution.md), and [Blueprint Integration](../blueprint-integration/aiken-blueprint-integration.md) is recommended.
:::

## Overview

Smart contract interaction patterns provide reusable solutions for common blockchain use cases:

- **Lock/Unlock Patterns** - Secure value storage and retrieval
- **Minting Validators** - Token creation and burning logic
- **State Machines** - Multi-step contract workflows
- **Validator Chaining** - Cross-contract interactions
- **Oracle Integration** - External data consumption

## Lock/Unlock Patterns

Lock/unlock patterns are fundamental for creating secure escrow systems, time-locked payments, and conditional value storage.

### Basic Lock/Unlock Implementation

```java
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.plutus.spec.*;

// Basic lock/unlock validator datum structure
@Constr(alternative = 0)
public class LockDatum implements Data<LockDatum> {
    @PlutusField(order = 0)
    private byte[] owner;           // Owner's public key hash
    
    @PlutusField(order = 1)
    private BigInteger lockUntil;   // Unix timestamp
    
    @PlutusField(order = 2)
    private byte[] secret;          // Optional secret hash
    
    // Constructors, getters, setters, toPlutusData()
}

// Redeemer for unlock operations
@Constr(alternative = 0)
public class UnlockRedeemer implements Data<UnlockRedeemer> {
    @PlutusField(order = 0)
    private BigInteger action;      // 0=unlock, 1=extend, 2=cancel
    
    @PlutusField(order = 1)
    private byte[] proof;           // Signature or secret
    
    @PlutusField(order = 2)
    private BigInteger timestamp;   // Current time proof
    
    // Implementation methods
}
```

### Complete Lock/Unlock Workflow

```java
public class LockUnlockContract {
    private final PlutusV2Script lockScript;
    private final QuickTxBuilder txBuilder;
    private final String scriptAddress;
    
    public LockUnlockContract(PlutusV2Script script, QuickTxBuilder builder) {
        this.lockScript = script;
        this.txBuilder = builder;
        this.scriptAddress = AddressUtil.getEnterprise(script, Networks.testnet()).toBech32();
    }
    
    // Lock ADA with time constraint
    public Result<String> lockFunds(Account owner, Amount lockAmount, long lockDurationSeconds) {
        // Create lock datum
        LockDatum datum = new LockDatum();
        datum.setOwner(owner.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        datum.setLockUntil(BigInteger.valueOf(System.currentTimeMillis() / 1000 + lockDurationSeconds));
        datum.setSecret(null); // No secret required
        
        // Build lock transaction
        Tx lockTx = new Tx()
            .payTo(scriptAddress, lockAmount)
            .attachDatum(datum.toPlutusData())
            .from(owner.baseAddress());
            
        return txBuilder.compose(lockTx)
            .withSigner(SignerProviders.signerFrom(owner))
            .completeAndSubmit();
    }
    
    // Unlock funds after time constraint
    public Result<String> unlockFunds(LockDatum originalDatum, Account owner) {
        // Verify time constraint
        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime < originalDatum.getLockUntil().longValue()) {
            throw new IllegalStateException("Lock period not yet expired");
        }
        
        // Create unlock redeemer
        UnlockRedeemer redeemer = new UnlockRedeemer();
        redeemer.setAction(BigInteger.ZERO); // Unlock action
        redeemer.setProof(generateOwnerProof(owner, originalDatum));
        redeemer.setTimestamp(BigInteger.valueOf(currentTime));
        
        // Find locked UTXO
        Utxo lockedUtxo = findLockedUtxo(originalDatum);
        
        // Build unlock transaction
        ScriptTx unlockTx = new ScriptTx()
            .collectFrom(lockedUtxo, redeemer.toPlutusData())
            .payTo(owner.baseAddress(), extractLockedAmount(lockedUtxo))
            .attachSpendingValidator(lockScript)
            .withRequiredSigners(owner.getBaseAddress());
            
        return txBuilder.compose(unlockTx)
            .withSigner(SignerProviders.signerFrom(owner))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    // Emergency unlock with secret
    public Result<String> emergencyUnlock(LockDatum originalDatum, String secret, String recipientAddress) {
        // Verify secret matches
        byte[] secretHash = Blake2bUtil.blake2bHash256(secret.getBytes());
        if (!Arrays.equals(secretHash, originalDatum.getSecret())) {
            throw new IllegalArgumentException("Invalid secret");
        }
        
        UnlockRedeemer redeemer = new UnlockRedeemer();
        redeemer.setAction(BigInteger.valueOf(3)); // Emergency unlock
        redeemer.setProof(secret.getBytes());
        redeemer.setTimestamp(BigInteger.valueOf(System.currentTimeMillis() / 1000));
        
        Utxo lockedUtxo = findLockedUtxo(originalDatum);
        
        ScriptTx emergencyTx = new ScriptTx()
            .collectFrom(lockedUtxo, redeemer.toPlutusData())
            .payTo(recipientAddress, extractLockedAmount(lockedUtxo))
            .attachSpendingValidator(lockScript);
            
        return txBuilder.compose(emergencyTx)
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private byte[] generateOwnerProof(Account owner, LockDatum datum) {
        // Create signature proof
        String message = "unlock:" + HexUtil.encodeHexString(datum.getOwner()) + ":" + datum.getLockUntil();
        return owner.sign(message.getBytes());
    }
    
    private Utxo findLockedUtxo(LockDatum datum) {
        // Implementation to find matching UTXO
        return txBuilder.getUtxoSupplier().getUtxos(scriptAddress).stream()
            .filter(utxo -> matchesDatum(utxo, datum))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Locked UTXO not found"));
    }
    
    private boolean matchesDatum(Utxo utxo, LockDatum datum) {
        // Verify UTXO datum matches expected datum
        if (utxo.getInlineDatum() != null) {
            try {
                LockDatum utxoDatum = LockDatum.fromPlutusData((ConstrPlutusData) utxo.getInlineDatum());
                return Arrays.equals(utxoDatum.getOwner(), datum.getOwner()) &&
                       utxoDatum.getLockUntil().equals(datum.getLockUntil());
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
    
    private Amount extractLockedAmount(Utxo utxo) {
        return Amount.builder()
            .coin(utxo.getAmount().getCoin())
            .multiAssets(utxo.getAmount().getMultiAssets())
            .build();
    }
}
```

### Advanced Lock/Unlock Patterns

#### Multi-Signature Lock

```java
@Constr(alternative = 0)
public class MultiSigLockDatum implements Data<MultiSigLockDatum> {
    @PlutusField(order = 0)
    private List<byte[]> requiredSigners;    // List of required public key hashes
    
    @PlutusField(order = 1)
    private BigInteger threshold;            // Minimum signatures required
    
    @PlutusField(order = 2)
    private BigInteger lockUntil;           // Time constraint
    
    @PlutusField(order = 3)
    private Map<byte[], byte[]> metadata;   // Additional lock conditions
}

@Constr(alternative = 0)
public class MultiSigUnlockRedeemer implements Data<MultiSigUnlockRedeemer> {
    @PlutusField(order = 0)
    private List<byte[]> signatures;        // Provided signatures
    
    @PlutusField(order = 1)
    private List<byte[]> signers;          // Corresponding signer hashes
    
    @PlutusField(order = 2)
    private BigInteger unlockType;         // Normal=0, Emergency=1
}

public class MultiSigLockContract {
    
    public Result<String> createMultiSigLock(List<Account> signers, int threshold, Amount lockAmount, long lockDuration) {
        List<byte[]> signerHashes = signers.stream()
            .map(acc -> acc.getBaseAddress().getPaymentCredentialHash().orElseThrow())
            .collect(Collectors.toList());
            
        MultiSigLockDatum datum = new MultiSigLockDatum();
        datum.setRequiredSigners(signerHashes);
        datum.setThreshold(BigInteger.valueOf(threshold));
        datum.setLockUntil(BigInteger.valueOf(System.currentTimeMillis() / 1000 + lockDuration));
        datum.setMetadata(new HashMap<>());
        
        // Create coordinated transaction
        return createCoordinatedLockTransaction(signers.get(0), datum, lockAmount);
    }
    
    public Result<String> unlockMultiSig(MultiSigLockDatum datum, List<Account> signingAccounts, String recipientAddress) {
        if (signingAccounts.size() < datum.getThreshold().intValue()) {
            throw new IllegalArgumentException("Insufficient signers provided");
        }
        
        // Generate signatures from each signer
        List<byte[]> signatures = new ArrayList<>();
        List<byte[]> signerHashes = new ArrayList<>();
        
        String unlockMessage = generateUnlockMessage(datum);
        
        for (Account signer : signingAccounts) {
            byte[] signature = signer.sign(unlockMessage.getBytes());
            byte[] signerHash = signer.getBaseAddress().getPaymentCredentialHash().orElseThrow();
            
            signatures.add(signature);
            signerHashes.add(signerHash);
        }
        
        MultiSigUnlockRedeemer redeemer = new MultiSigUnlockRedeemer();
        redeemer.setSignatures(signatures);
        redeemer.setSigners(signerHashes);
        redeemer.setUnlockType(BigInteger.ZERO);
        
        return executeMultiSigUnlock(datum, redeemer, recipientAddress, signingAccounts);
    }
}
```

#### Conditional Lock with Oracle Data

```java
@Constr(alternative = 0)
public class OracleConditionDatum implements Data<OracleConditionDatum> {
    @PlutusField(order = 0)
    private byte[] owner;
    
    @PlutusField(order = 1)
    private byte[] oracleAddress;           // Oracle script address
    
    @PlutusField(order = 2)
    private byte[] conditionKey;            // Data key to check
    
    @PlutusField(order = 3)
    private BigInteger conditionValue;      // Expected value
    
    @PlutusField(order = 4)
    private BigInteger comparisonType;      // 0=equal, 1=greater, 2=less
}

public class OracleConditionLock {
    
    public Result<String> createConditionalLock(Account owner, Amount lockAmount, 
                                               String oracleAddress, String dataKey, BigInteger expectedValue) {
        OracleConditionDatum datum = new OracleConditionDatum();
        datum.setOwner(owner.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        datum.setOracleAddress(AddressUtil.fromBech32(oracleAddress).getBytes());
        datum.setConditionKey(dataKey.getBytes());
        datum.setConditionValue(expectedValue);
        datum.setComparisonType(BigInteger.ZERO); // Equal comparison
        
        return createLockWithOracleCondition(owner, datum, lockAmount);
    }
    
    public Result<String> unlockConditional(OracleConditionDatum datum, Account owner) {
        // Fetch current oracle data
        PlutusData oracleData = fetchOracleData(datum.getOracleAddress(), datum.getConditionKey());
        
        // Verify condition is met
        if (!evaluateCondition(oracleData, datum)) {
            throw new IllegalStateException("Oracle condition not met");
        }
        
        // Create unlock with oracle proof
        return executeConditionalUnlock(datum, owner, oracleData);
    }
    
    private PlutusData fetchOracleData(byte[] oracleAddress, byte[] dataKey) {
        // Implementation to fetch oracle data from chain
        String oracleAddr = AddressUtil.fromBytes(oracleAddress).toBech32();
        
        // Find oracle UTXO containing the required data
        List<Utxo> oracleUtxos = txBuilder.getUtxoSupplier().getUtxos(oracleAddr);
        
        return oracleUtxos.stream()
            .map(utxo -> utxo.getInlineDatum())
            .filter(Objects::nonNull)
            .filter(datum -> containsDataKey(datum, dataKey))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Oracle data not found"));
    }
    
    private boolean evaluateCondition(PlutusData oracleData, OracleConditionDatum condition) {
        // Extract value from oracle data and compare
        BigInteger oracleValue = extractValueFromOracleData(oracleData, condition.getConditionKey());
        BigInteger expectedValue = condition.getConditionValue();
        int comparisonType = condition.getComparisonType().intValue();
        
        return switch (comparisonType) {
            case 0 -> oracleValue.equals(expectedValue);           // Equal
            case 1 -> oracleValue.compareTo(expectedValue) > 0;   // Greater
            case 2 -> oracleValue.compareTo(expectedValue) < 0;   // Less
            default -> false;
        };
    }
}
```

## Minting Validator Patterns

Minting validators control token creation, burning, and policy enforcement.

### Basic Token Minting Policy

```java
@Constr(alternative = 0)
public class MintingDatum implements Data<MintingDatum> {
    @PlutusField(order = 0)
    private byte[] issuer;              // Token issuer
    
    @PlutusField(order = 1)
    private BigInteger maxSupply;       // Maximum tokens to mint
    
    @PlutusField(order = 2)
    private BigInteger currentSupply;   // Current minted amount
    
    @PlutusField(order = 3)
    private Map<byte[], byte[]> metadata; // Token metadata
}

@Constr(alternative = 0)
public class MintingRedeemer implements Data<MintingRedeemer> {
    @PlutusField(order = 0)
    private BigInteger action;          // 0=mint, 1=burn, 2=update
    
    @PlutusField(order = 1)
    private BigInteger amount;          // Amount to mint/burn
    
    @PlutusField(order = 2)
    private byte[] recipient;           // Recipient for minting
    
    @PlutusField(order = 3)
    private byte[] authorization;       // Authorization proof
}

public class TokenMintingContract {
    private final PlutusV2Script mintingPolicy;
    private final String policyId;
    
    public TokenMintingContract(PlutusV2Script policy) {
        this.mintingPolicy = policy;
        this.policyId = policy.getPolicyId();
    }
    
    public Result<String> mintTokens(Account issuer, String tokenName, BigInteger amount, 
                                   String recipientAddress, Map<String, String> metadata) {
        // Create minting datum
        MintingDatum datum = new MintingDatum();
        datum.setIssuer(issuer.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        datum.setMaxSupply(BigInteger.valueOf(1000000)); // 1M token limit
        datum.setCurrentSupply(BigInteger.ZERO);
        
        Map<byte[], byte[]> metadataBytes = metadata.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().getBytes(),
                e -> e.getValue().getBytes()
            ));
        datum.setMetadata(metadataBytes);
        
        // Create minting redeemer
        MintingRedeemer redeemer = new MintingRedeemer();
        redeemer.setAction(BigInteger.ZERO); // Mint action
        redeemer.setAmount(amount);
        redeemer.setRecipient(AddressUtil.fromBech32(recipientAddress).getBytes());
        redeemer.setAuthorization(generateMintAuthorization(issuer, tokenName, amount));
        
        // Build minting transaction
        Asset tokenAsset = Asset.builder()
            .policyId(policyId)
            .assetName(tokenName)
            .amount(amount)
            .build();
            
        ScriptTx mintTx = new ScriptTx()
            .mintAssets(mintingPolicy, redeemer.toPlutusData(), tokenAsset)
            .payTo(recipientAddress, Amount.builder().assets(Arrays.asList(tokenAsset)).build())
            .from(issuer.baseAddress());
            
        return txBuilder.compose(mintTx)
            .withSigner(SignerProviders.signerFrom(issuer))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    public Result<String> burnTokens(Account holder, String tokenName, BigInteger amount) {
        MintingRedeemer redeemer = new MintingRedeemer();
        redeemer.setAction(BigInteger.ONE); // Burn action
        redeemer.setAmount(amount);
        redeemer.setRecipient(new byte[0]); // No recipient for burning
        redeemer.setAuthorization(generateBurnAuthorization(holder, tokenName, amount));
        
        // Negative amount for burning
        Asset burnAsset = Asset.builder()
            .policyId(policyId)
            .assetName(tokenName)
            .amount(amount.negate())
            .build();
            
        ScriptTx burnTx = new ScriptTx()
            .mintAssets(mintingPolicy, redeemer.toPlutusData(), burnAsset)
            .from(holder.baseAddress());
            
        return txBuilder.compose(burnTx)
            .withSigner(SignerProviders.signerFrom(holder))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private byte[] generateMintAuthorization(Account issuer, String tokenName, BigInteger amount) {
        String authMessage = "mint:" + tokenName + ":" + amount + ":" + System.currentTimeMillis();
        return issuer.sign(authMessage.getBytes());
    }
    
    private byte[] generateBurnAuthorization(Account holder, String tokenName, BigInteger amount) {
        String authMessage = "burn:" + tokenName + ":" + amount + ":" + System.currentTimeMillis();
        return holder.sign(authMessage.getBytes());
    }
}
```

### Advanced Minting Patterns

#### Time-Based Token Release

```java
@Constr(alternative = 0)
public class VestingTokenDatum implements Data<VestingTokenDatum> {
    @PlutusField(order = 0)
    private byte[] beneficiary;
    
    @PlutusField(order = 1)
    private BigInteger totalAmount;         // Total vesting amount
    
    @PlutusField(order = 2)
    private BigInteger releasedAmount;      // Already released
    
    @PlutusField(order = 3)
    private BigInteger vestingStart;        // Vesting start time
    
    @PlutusField(order = 4)
    private BigInteger vestingDuration;     // Total vesting period
    
    @PlutusField(order = 5)
    private BigInteger cliffPeriod;         // Cliff period before vesting starts
}

public class VestingTokenContract {
    
    public Result<String> createVestingSchedule(Account issuer, String beneficiaryAddress, 
                                              BigInteger totalTokens, long vestingDurationDays, long cliffDays) {
        long currentTime = System.currentTimeMillis() / 1000;
        
        VestingTokenDatum datum = new VestingTokenDatum();
        datum.setBeneficiary(AddressUtil.fromBech32(beneficiaryAddress).getBytes());
        datum.setTotalAmount(totalTokens);
        datum.setReleasedAmount(BigInteger.ZERO);
        datum.setVestingStart(BigInteger.valueOf(currentTime));
        datum.setVestingDuration(BigInteger.valueOf(vestingDurationDays * 24 * 3600));
        datum.setCliffPeriod(BigInteger.valueOf(cliffDays * 24 * 3600));
        
        return lockVestingTokens(issuer, datum);
    }
    
    public Result<String> claimVestedTokens(VestingTokenDatum vestingDatum, Account beneficiary) {
        long currentTime = System.currentTimeMillis() / 1000;
        BigInteger vestedAmount = calculateVestedAmount(vestingDatum, currentTime);
        BigInteger claimableAmount = vestedAmount.subtract(vestingDatum.getReleasedAmount());
        
        if (claimableAmount.compareTo(BigInteger.ZERO) <= 0) {
            throw new IllegalStateException("No tokens available for claiming");
        }
        
        // Update vesting datum
        VestingTokenDatum updatedDatum = new VestingTokenDatum();
        updatedDatum.setBeneficiary(vestingDatum.getBeneficiary());
        updatedDatum.setTotalAmount(vestingDatum.getTotalAmount());
        updatedDatum.setReleasedAmount(vestingDatum.getReleasedAmount().add(claimableAmount));
        updatedDatum.setVestingStart(vestingDatum.getVestingStart());
        updatedDatum.setVestingDuration(vestingDatum.getVestingDuration());
        updatedDatum.setCliffPeriod(vestingDatum.getCliffPeriod());
        
        return executeVestingClaim(vestingDatum, updatedDatum, claimableAmount, beneficiary);
    }
    
    private BigInteger calculateVestedAmount(VestingTokenDatum datum, long currentTime) {
        long vestingStart = datum.getVestingStart().longValue();
        long cliffEnd = vestingStart + datum.getCliffPeriod().longValue();
        long vestingEnd = vestingStart + datum.getVestingDuration().longValue();
        
        if (currentTime < cliffEnd) {
            return BigInteger.ZERO; // Still in cliff period
        }
        
        if (currentTime >= vestingEnd) {
            return datum.getTotalAmount(); // Fully vested
        }
        
        // Linear vesting calculation
        long vestedTime = currentTime - cliffEnd;
        long totalVestingTime = vestingEnd - cliffEnd;
        
        return datum.getTotalAmount()
            .multiply(BigInteger.valueOf(vestedTime))
            .divide(BigInteger.valueOf(totalVestingTime));
    }
}
```

#### Multi-Asset Bundle Minting

```java
@Constr(alternative = 0)
public class AssetBundleDatum implements Data<AssetBundleDatum> {
    @PlutusField(order = 0)
    private byte[] creator;
    
    @PlutusField(order = 1)
    private List<AssetDefinition> assets;
    
    @PlutusField(order = 2)
    private BigInteger bundleId;
    
    @PlutusField(order = 3)
    private Map<byte[], byte[]> bundleMetadata;
}

@Constr(alternative = 0)
public class AssetDefinition implements Data<AssetDefinition> {
    @PlutusField(order = 0)
    private byte[] assetName;
    
    @PlutusField(order = 1)
    private BigInteger quantity;
    
    @PlutusField(order = 2)
    private Map<byte[], byte[]> metadata;
}

public class AssetBundleMinter {
    
    public Result<String> mintAssetBundle(Account creator, List<AssetDefinition> assetDefinitions,
                                        String recipientAddress, Map<String, String> bundleMetadata) {
        AssetBundleDatum datum = new AssetBundleDatum();
        datum.setCreator(creator.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        datum.setAssets(assetDefinitions);
        datum.setBundleId(BigInteger.valueOf(System.currentTimeMillis()));
        datum.setBundleMetadata(convertMetadata(bundleMetadata));
        
        // Create all assets for minting
        List<Asset> assetsToMint = assetDefinitions.stream()
            .map(def -> Asset.builder()
                .policyId(policyId)
                .assetName(new String(def.getAssetName()))
                .amount(def.getQuantity())
                .build())
            .collect(Collectors.toList());
            
        MintingRedeemer redeemer = new MintingRedeemer();
        redeemer.setAction(BigInteger.valueOf(10)); // Bundle mint action
        redeemer.setAmount(BigInteger.valueOf(assetDefinitions.size()));
        redeemer.setRecipient(AddressUtil.fromBech32(recipientAddress).getBytes());
        redeemer.setAuthorization(generateBundleAuthorization(creator, datum));
        
        ScriptTx bundleMintTx = new ScriptTx()
            .mintAssets(mintingPolicy, redeemer.toPlutusData(), assetsToMint.toArray(new Asset[0]))
            .payTo(recipientAddress, Amount.builder().assets(assetsToMint).build())
            .from(creator.baseAddress());
            
        return txBuilder.compose(bundleMintTx)
            .withSigner(SignerProviders.signerFrom(creator))
            .withTxEvaluator()
            .completeAndSubmit();
    }
}
```

## State Machine Patterns

State machines enable complex multi-step contract workflows with state transitions and validation.

### Basic State Machine Implementation

```java
// State enumeration
public enum ContractState implements Data<ContractState> {
    INITIALIZED(0),
    PENDING(1),
    ACTIVE(2),
    SUSPENDED(3),
    COMPLETED(4),
    CANCELLED(5);
    
    private final int value;
    
    ContractState(int value) {
        this.value = value;
    }
    
    @Override
    public ConstrPlutusData toPlutusData() {
        return ConstrPlutusData.of(value);
    }
    
    public static ContractState fromPlutusData(ConstrPlutusData data) {
        int stateValue = data.getAlternative();
        return Arrays.stream(values())
            .filter(state -> state.value == stateValue)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid state: " + stateValue));
    }
}

// State machine datum
@Constr(alternative = 0)
public class StateMachineDatum implements Data<StateMachineDatum> {
    @PlutusField(order = 0)
    private ContractState currentState;
    
    @PlutusField(order = 1)
    private byte[] owner;
    
    @PlutusField(order = 2)
    private Map<byte[], PlutusData> stateData;    // State-specific data
    
    @PlutusField(order = 3)
    private BigInteger version;                   // Version for upgrades
    
    @PlutusField(order = 4)
    private BigInteger lastTransition;           // Timestamp of last state change
}

// State transition redeemer
@Constr(alternative = 0)
public class StateTransitionRedeemer implements Data<StateTransitionRedeemer> {
    @PlutusField(order = 0)
    private ContractState targetState;
    
    @PlutusField(order = 1)
    private Map<byte[], PlutusData> transitionData; // Data for transition
    
    @PlutusField(order = 2)
    private byte[] authorization;                    // Transition authorization
}

public class StateMachineContract {
    private final PlutusV2Script stateScript;
    private final String scriptAddress;
    
    // State transition matrix defining valid transitions
    private static final Map<ContractState, Set<ContractState>> VALID_TRANSITIONS = Map.of(
        ContractState.INITIALIZED, Set.of(ContractState.PENDING, ContractState.CANCELLED),
        ContractState.PENDING, Set.of(ContractState.ACTIVE, ContractState.CANCELLED),
        ContractState.ACTIVE, Set.of(ContractState.SUSPENDED, ContractState.COMPLETED, ContractState.CANCELLED),
        ContractState.SUSPENDED, Set.of(ContractState.ACTIVE, ContractState.CANCELLED),
        ContractState.COMPLETED, Set.of(), // Terminal state
        ContractState.CANCELLED, Set.of()  // Terminal state
    );
    
    public Result<String> initializeContract(Account owner, Map<String, PlutusData> initialData) {
        StateMachineDatum initialDatum = new StateMachineDatum();
        initialDatum.setCurrentState(ContractState.INITIALIZED);
        initialDatum.setOwner(owner.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        initialDatum.setStateData(convertStateData(initialData));
        initialDatum.setVersion(BigInteger.ONE);
        initialDatum.setLastTransition(BigInteger.valueOf(System.currentTimeMillis() / 1000));
        
        Tx initTx = new Tx()
            .payTo(scriptAddress, Amount.ada(2.0)) // Initial contract value
            .attachDatum(initialDatum.toPlutusData())
            .from(owner.baseAddress());
            
        return txBuilder.compose(initTx)
            .withSigner(SignerProviders.signerFrom(owner))
            .completeAndSubmit();
    }
    
    public Result<String> transitionState(StateMachineDatum currentDatum, ContractState targetState,
                                        Map<String, PlutusData> transitionData, Account authorizer) {
        // Validate transition
        if (!isValidTransition(currentDatum.getCurrentState(), targetState)) {
            throw new IllegalStateException(
                String.format("Invalid transition: %s -> %s", 
                    currentDatum.getCurrentState(), targetState)
            );
        }
        
        // Create transition redeemer
        StateTransitionRedeemer redeemer = new StateTransitionRedeemer();
        redeemer.setTargetState(targetState);
        redeemer.setTransitionData(convertStateData(transitionData));
        redeemer.setAuthorization(generateTransitionAuth(authorizer, currentDatum, targetState));
        
        // Create updated datum
        StateMachineDatum newDatum = new StateMachineDatum();
        newDatum.setCurrentState(targetState);
        newDatum.setOwner(currentDatum.getOwner());
        newDatum.setStateData(mergeStateData(currentDatum.getStateData(), transitionData));
        newDatum.setVersion(currentDatum.getVersion().add(BigInteger.ONE));
        newDatum.setLastTransition(BigInteger.valueOf(System.currentTimeMillis() / 1000));
        
        // Find current contract UTXO
        Utxo contractUtxo = findContractUtxo(currentDatum);
        
        ScriptTx transitionTx = new ScriptTx()
            .collectFrom(contractUtxo, redeemer.toPlutusData())
            .payTo(scriptAddress, extractContractValue(contractUtxo))
            .attachDatum(newDatum.toPlutusData())
            .attachSpendingValidator(stateScript)
            .withRequiredSigners(authorizer.getBaseAddress());
            
        return txBuilder.compose(transitionTx)
            .withSigner(SignerProviders.signerFrom(authorizer))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    public Result<String> executeStateAction(StateMachineDatum currentDatum, String actionName,
                                           Map<String, PlutusData> actionParams, Account executor) {
        // Execute state-specific action without changing state
        return switch (currentDatum.getCurrentState()) {
            case ACTIVE -> executeActiveStateAction(currentDatum, actionName, actionParams, executor);
            case PENDING -> executePendingStateAction(currentDatum, actionName, actionParams, executor);
            case SUSPENDED -> executeSuspendedStateAction(currentDatum, actionName, actionParams, executor);
            default -> throw new IllegalStateException("No actions available in state: " + currentDatum.getCurrentState());
        };
    }
    
    private boolean isValidTransition(ContractState from, ContractState to) {
        return VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
    
    private Map<byte[], PlutusData> convertStateData(Map<String, PlutusData> data) {
        return data.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().getBytes(),
                Map.Entry::getValue
            ));
    }
    
    private Map<byte[], PlutusData> mergeStateData(Map<byte[], PlutusData> existing, Map<String, PlutusData> updates) {
        Map<byte[], PlutusData> merged = new HashMap<>(existing);
        updates.forEach((key, value) -> merged.put(key.getBytes(), value));
        return merged;
    }
    
    private byte[] generateTransitionAuth(Account authorizer, StateMachineDatum currentDatum, ContractState targetState) {
        String authMessage = String.format("transition:%s:%s:%s:%d",
            currentDatum.getCurrentState().name(),
            targetState.name(),
            HexUtil.encodeHexString(currentDatum.getOwner()),
            System.currentTimeMillis()
        );
        return authorizer.sign(authMessage.getBytes());
    }
}
```

### Advanced State Machine: Order Processing

```java
// Order processing states
public enum OrderState implements Data<OrderState> {
    CREATED(0),
    PAYMENT_PENDING(1),
    PAYMENT_CONFIRMED(2),
    PROCESSING(3),
    SHIPPED(4),
    DELIVERED(5),
    CANCELLED(6),
    REFUNDED(7);
    
    private final int value;
    OrderState(int value) { this.value = value; }
    
    // Implementation methods...
}

@Constr(alternative = 0)
public class OrderDatum implements Data<OrderDatum> {
    @PlutusField(order = 0)
    private OrderState state;
    
    @PlutusField(order = 1)
    private byte[] customer;
    
    @PlutusField(order = 2)
    private byte[] merchant;
    
    @PlutusField(order = 3)
    private List<OrderItem> items;
    
    @PlutusField(order = 4)
    private BigInteger totalAmount;
    
    @PlutusField(order = 5)
    private BigInteger paymentDeadline;
    
    @PlutusField(order = 6)
    private Map<byte[], byte[]> orderMetadata;
}

public class OrderProcessingStateMachine extends StateMachineContract {
    
    public Result<String> createOrder(Account customer, List<OrderItem> items, 
                                    String merchantAddress, long paymentTimeoutMinutes) {
        BigInteger totalAmount = calculateTotalAmount(items);
        
        OrderDatum orderDatum = new OrderDatum();
        orderDatum.setState(OrderState.CREATED);
        orderDatum.setCustomer(customer.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        orderDatum.setMerchant(AddressUtil.fromBech32(merchantAddress).getBytes());
        orderDatum.setItems(items);
        orderDatum.setTotalAmount(totalAmount);
        orderDatum.setPaymentDeadline(BigInteger.valueOf(
            System.currentTimeMillis() / 1000 + paymentTimeoutMinutes * 60
        ));
        orderDatum.setOrderMetadata(new HashMap<>());
        
        return initializeOrderContract(customer, orderDatum);
    }
    
    public Result<String> processPayment(OrderDatum orderDatum, Account customer, Amount paymentAmount) {
        if (orderDatum.getState() != OrderState.CREATED) {
            throw new IllegalStateException("Order not in correct state for payment");
        }
        
        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime > orderDatum.getPaymentDeadline().longValue()) {
            throw new IllegalStateException("Payment deadline exceeded");
        }
        
        // Verify payment amount
        if (paymentAmount.getCoin().compareTo(orderDatum.getTotalAmount()) < 0) {
            throw new IllegalArgumentException("Insufficient payment amount");
        }
        
        return transitionOrderState(orderDatum, OrderState.PAYMENT_CONFIRMED, 
                                  Map.of("payment_amount", BigIntPlutusData.of(paymentAmount.getCoin())), 
                                  customer);
    }
    
    public Result<String> beginProcessing(OrderDatum orderDatum, Account merchant) {
        validateStateTransition(orderDatum, OrderState.PAYMENT_CONFIRMED, OrderState.PROCESSING);
        
        return transitionOrderState(orderDatum, OrderState.PROCESSING,
                                  Map.of("processing_start", BigIntPlutusData.of(System.currentTimeMillis())),
                                  merchant);
    }
    
    public Result<String> shipOrder(OrderDatum orderDatum, Account merchant, String trackingNumber) {
        validateStateTransition(orderDatum, OrderState.PROCESSING, OrderState.SHIPPED);
        
        return transitionOrderState(orderDatum, OrderState.SHIPPED,
                                  Map.of("tracking_number", BytesPlutusData.of(trackingNumber.getBytes())),
                                  merchant);
    }
    
    public Result<String> confirmDelivery(OrderDatum orderDatum, Account customer) {
        validateStateTransition(orderDatum, OrderState.SHIPPED, OrderState.DELIVERED);
        
        // Release payment to merchant
        return finalizeOrderWithPayment(orderDatum, customer);
    }
    
    public Result<String> cancelOrder(OrderDatum orderDatum, Account requester, String reason) {
        // Allow cancellation from various states with different rules
        if (!canCancelFromState(orderDatum.getState())) {
            throw new IllegalStateException("Cannot cancel order from current state");
        }
        
        return transitionOrderState(orderDatum, OrderState.CANCELLED,
                                  Map.of("cancellation_reason", BytesPlutusData.of(reason.getBytes())),
                                  requester);
    }
    
    private Result<String> transitionOrderState(OrderDatum currentDatum, OrderState targetState,
                                              Map<String, PlutusData> transitionData, Account authorizer) {
        // Create state transition with order-specific logic
        return super.transitionState(convertToStateMachineDatum(currentDatum), 
                                   convertToContractState(targetState), 
                                   transitionData, authorizer);
    }
    
    private boolean canCancelFromState(OrderState state) {
        return state == OrderState.CREATED || 
               state == OrderState.PAYMENT_PENDING || 
               state == OrderState.PAYMENT_CONFIRMED;
    }
    
    private BigInteger calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
            .map(item -> item.getUnitPrice().multiply(item.getQuantity()))
            .reduce(BigInteger.ZERO, BigInteger::add);
    }
}
```

This comprehensive guide provides the foundation for implementing sophisticated smart contract interaction patterns. The examples demonstrate real-world scenarios while maintaining security and efficiency best practices.
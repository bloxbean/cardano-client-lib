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

## Validator Chaining Patterns

Validator chaining enables sophisticated cross-contract interactions where multiple validators work together to implement complex business logic.

### Basic Validator Chaining

```java
// Datum for validator that references another validator
@Constr(alternative = 0)
public class ChainedValidatorDatum implements Data<ChainedValidatorDatum> {
    @PlutusField(order = 0)
    private byte[] nextValidator;       // Address of next validator in chain
    
    @PlutusField(order = 1)
    private byte[] requiredValidator;   // Required previous validator
    
    @PlutusField(order = 2)
    private BigInteger sequenceNumber;  // Position in chain
    
    @PlutusField(order = 3)
    private Map<byte[], PlutusData> chainData; // Data passed through chain
}

@Constr(alternative = 0)
public class ChainRedeemer implements Data<ChainRedeemer> {
    @PlutusField(order = 0)
    private byte[] previousOutput;      // Reference to previous validator output
    
    @PlutusField(order = 1)
    private byte[] nextInput;          // Data for next validator
    
    @PlutusField(order = 2)
    private BigInteger action;         // Chain action type
}

public class ValidatorChainContract {
    private final Map<String, PlutusV2Script> validators;
    private final QuickTxBuilder txBuilder;
    
    public ValidatorChainContract(Map<String, PlutusV2Script> validators, QuickTxBuilder builder) {
        this.validators = validators;
        this.txBuilder = builder;
    }
    
    public Result<String> initiateChain(Account initiator, String firstValidatorName, 
                                       Map<String, PlutusData> initialData, Amount chainValue) {
        PlutusV2Script firstValidator = validators.get(firstValidatorName);
        String firstAddress = AddressUtil.getEnterprise(firstValidator, Networks.testnet()).toBech32();
        
        ChainedValidatorDatum initialDatum = new ChainedValidatorDatum();
        initialDatum.setNextValidator(getValidatorAddress("validator2").getBytes());
        initialDatum.setRequiredValidator(new byte[0]); // No previous validator
        initialDatum.setSequenceNumber(BigInteger.ZERO);
        initialDatum.setChainData(convertToPlutusData(initialData));
        
        Tx initTx = new Tx()
            .payTo(firstAddress, chainValue)
            .attachDatum(initialDatum.toPlutusData())
            .from(initiator.baseAddress());
            
        return txBuilder.compose(initTx)
            .withSigner(SignerProviders.signerFrom(initiator))
            .completeAndSubmit();
    }
    
    public Result<String> executeChainStep(String currentValidatorName, ChainedValidatorDatum currentDatum,
                                         String nextValidatorName, Map<String, PlutusData> processedData,
                                         Account executor) {
        PlutusV2Script currentValidator = validators.get(currentValidatorName);
        PlutusV2Script nextValidator = validators.get(nextValidatorName);
        
        String currentAddress = AddressUtil.getEnterprise(currentValidator, Networks.testnet()).toBech32();
        String nextAddress = AddressUtil.getEnterprise(nextValidator, Networks.testnet()).toBech32();
        
        // Find current UTXO
        Utxo currentUtxo = findChainUtxo(currentAddress, currentDatum);
        
        // Create redeemer for current validator
        ChainRedeemer redeemer = new ChainRedeemer();
        redeemer.setPreviousOutput(currentUtxo.getTxHash().getBytes());
        redeemer.setNextInput(serializeProcessedData(processedData));
        redeemer.setAction(BigInteger.ONE); // Continue chain
        
        // Create datum for next validator
        ChainedValidatorDatum nextDatum = new ChainedValidatorDatum();
        nextDatum.setNextValidator(getValidatorAddress("validator3").getBytes());
        nextDatum.setRequiredValidator(currentAddress.getBytes());
        nextDatum.setSequenceNumber(currentDatum.getSequenceNumber().add(BigInteger.ONE));
        nextDatum.setChainData(processedData);
        
        ScriptTx chainTx = new ScriptTx()
            .collectFrom(currentUtxo, redeemer.toPlutusData())
            .payTo(nextAddress, extractValue(currentUtxo))
            .attachDatum(nextDatum.toPlutusData())
            .attachSpendingValidator(currentValidator)
            .withRequiredSigners(executor.getBaseAddress());
            
        return txBuilder.compose(chainTx)
            .withSigner(SignerProviders.signerFrom(executor))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    public Result<String> finalizeChain(String finalValidatorName, ChainedValidatorDatum finalDatum,
                                      String recipientAddress, Account executor) {
        PlutusV2Script finalValidator = validators.get(finalValidatorName);
        String finalAddress = AddressUtil.getEnterprise(finalValidator, Networks.testnet()).toBech32();
        
        Utxo finalUtxo = findChainUtxo(finalAddress, finalDatum);
        
        ChainRedeemer finalRedeemer = new ChainRedeemer();
        finalRedeemer.setPreviousOutput(finalUtxo.getTxHash().getBytes());
        finalRedeemer.setNextInput(new byte[0]); // No next validator
        finalRedeemer.setAction(BigInteger.valueOf(2)); // Finalize chain
        
        ScriptTx finalizeTx = new ScriptTx()
            .collectFrom(finalUtxo, finalRedeemer.toPlutusData())
            .payTo(recipientAddress, extractValue(finalUtxo))
            .attachSpendingValidator(finalValidator)
            .withRequiredSigners(executor.getBaseAddress());
            
        return txBuilder.compose(finalizeTx)
            .withSigner(SignerProviders.signerFrom(executor))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private Utxo findChainUtxo(String address, ChainedValidatorDatum datum) {
        return txBuilder.getUtxoSupplier().getUtxos(address).stream()
            .filter(utxo -> matchesChainDatum(utxo, datum))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Chain UTXO not found"));
    }
    
    private boolean matchesChainDatum(Utxo utxo, ChainedValidatorDatum expected) {
        if (utxo.getInlineDatum() != null) {
            try {
                ChainedValidatorDatum actual = ChainedValidatorDatum.fromPlutusData(
                    (ConstrPlutusData) utxo.getInlineDatum()
                );
                return actual.getSequenceNumber().equals(expected.getSequenceNumber());
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}
```

### Advanced Validator Chaining: Multi-Path Workflow

```java
// Workflow with conditional branching
@Constr(alternative = 0)
public class WorkflowDatum implements Data<WorkflowDatum> {
    @PlutusField(order = 0)
    private byte[] workflowId;
    
    @PlutusField(order = 1)
    private BigInteger currentStep;
    
    @PlutusField(order = 2)
    private Map<BigInteger, byte[]> stepValidators; // Step -> Validator mapping
    
    @PlutusField(order = 3)
    private Map<byte[], PlutusData> workflowData;
    
    @PlutusField(order = 4)
    private List<BigInteger> completedSteps;
    
    @PlutusField(order = 5)
    private BigInteger branchCondition;  // Determines path through workflow
}

public class ConditionalWorkflowContract {
    
    public Result<String> executeConditionalStep(WorkflowDatum currentDatum, BigInteger decision,
                                               Map<String, PlutusData> stepData, Account executor) {
        // Determine next validator based on decision
        byte[] nextValidatorAddress = determineNextValidator(currentDatum, decision);
        
        // Create transition based on workflow rules
        if (requiresParallelExecution(currentDatum, decision)) {
            return executeParallelBranches(currentDatum, stepData, executor);
        } else {
            return executeSingleBranch(currentDatum, nextValidatorAddress, stepData, executor);
        }
    }
    
    private Result<String> executeParallelBranches(WorkflowDatum datum, 
                                                 Map<String, PlutusData> data, Account executor) {
        // Split value across multiple validators for parallel execution
        List<byte[]> parallelValidators = getParallelValidators(datum);
        
        ScriptTx parallelTx = new ScriptTx();
        Amount totalValue = getWorkflowValue(datum);
        Amount splitValue = Amount.ada(totalValue.getCoin().divide(BigInteger.valueOf(parallelValidators.size())));
        
        for (int i = 0; i < parallelValidators.size(); i++) {
            WorkflowDatum branchDatum = createBranchDatum(datum, i, parallelValidators.get(i));
            String branchAddress = AddressUtil.fromBytes(parallelValidators.get(i)).toBech32();
            
            parallelTx.payTo(branchAddress, splitValue)
                     .attachDatum(branchDatum.toPlutusData());
        }
        
        // Collect from current validator
        Utxo currentUtxo = findWorkflowUtxo(datum);
        parallelTx.collectFrom(currentUtxo, createBranchRedeemer(datum, parallelValidators));
        
        return txBuilder.compose(parallelTx)
            .withSigner(SignerProviders.signerFrom(executor))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private Result<String> mergeBranches(List<WorkflowDatum> branchDatums, 
                                       byte[] mergeValidator, Account executor) {
        ScriptTx mergeTx = new ScriptTx();
        Amount totalValue = Amount.ada(0L);
        
        // Collect from all branches
        for (WorkflowDatum branchDatum : branchDatums) {
            Utxo branchUtxo = findWorkflowUtxo(branchDatum);
            mergeTx.collectFrom(branchUtxo, createMergeRedeemer(branchDatum));
            totalValue = totalValue.plus(extractValue(branchUtxo));
        }
        
        // Create merged datum
        WorkflowDatum mergedDatum = createMergedDatum(branchDatums);
        String mergeAddress = AddressUtil.fromBytes(mergeValidator).toBech32();
        
        mergeTx.payTo(mergeAddress, totalValue)
               .attachDatum(mergedDatum.toPlutusData());
               
        return txBuilder.compose(mergeTx)
            .withSigner(SignerProviders.signerFrom(executor))
            .withTxEvaluator()
            .completeAndSubmit();
    }
}
```

### Cross-Contract Reference Pattern

```java
// Pattern for validators that reference each other's state
@Constr(alternative = 0)
public class ReferenceValidatorDatum implements Data<ReferenceValidatorDatum> {
    @PlutusField(order = 0)
    private byte[] validatorId;
    
    @PlutusField(order = 1)
    private List<byte[]> referencedValidators;  // Other validators this depends on
    
    @PlutusField(order = 2)
    private Map<byte[], byte[]> referenceData;  // Validator -> Required data hash
    
    @PlutusField(order = 3)
    private BigInteger lastVerification;        // When references were last verified
}

public class CrossContractReferencePattern {
    
    public Result<String> executeWithReferences(ReferenceValidatorDatum datum, 
                                              Map<String, PlutusData> executionData,
                                              Account executor) {
        // Verify all referenced validators have expected state
        for (byte[] refValidator : datum.getReferencedValidators()) {
            if (!verifyValidatorState(refValidator, datum.getReferenceData().get(refValidator))) {
                throw new IllegalStateException("Referenced validator state invalid");
            }
        }
        
        // Build transaction that reads from reference validators
        ScriptTx referenceTx = new ScriptTx();
        
        // Add reference inputs for state verification
        for (byte[] refValidator : datum.getReferencedValidators()) {
            Utxo refUtxo = findReferenceUtxo(refValidator);
            referenceTx.readFrom(refUtxo); // Reference input - not consumed
        }
        
        // Execute main validator logic
        Utxo mainUtxo = findValidatorUtxo(datum.getValidatorId());
        PlutusData updatedData = processWithReferences(datum, executionData);
        
        referenceTx.collectFrom(mainUtxo, createReferenceRedeemer(datum, executionData))
                   .payTo(getValidatorAddress(datum.getValidatorId()), extractValue(mainUtxo))
                   .attachDatum(updatedData)
                   .withRequiredSigners(executor.getBaseAddress());
                   
        return txBuilder.compose(referenceTx)
            .withSigner(SignerProviders.signerFrom(executor))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private boolean verifyValidatorState(byte[] validatorAddress, byte[] expectedDataHash) {
        String address = AddressUtil.fromBytes(validatorAddress).toBech32();
        List<Utxo> utxos = txBuilder.getUtxoSupplier().getUtxos(address);
        
        return utxos.stream()
            .anyMatch(utxo -> {
                if (utxo.getInlineDatum() != null) {
                    byte[] actualHash = Blake2bUtil.blake2bHash256(
                        CborSerializationUtil.serialize(utxo.getInlineDatum())
                    );
                    return Arrays.equals(actualHash, expectedDataHash);
                }
                return false;
            });
    }
}
```

## Oracle Integration Patterns

Oracle integration allows smart contracts to consume external data securely and reliably.

### Basic Oracle Pattern

```java
// Oracle datum structure
@Constr(alternative = 0)
public class OracleDatum implements Data<OracleDatum> {
    @PlutusField(order = 0)
    private byte[] oracleProvider;      // Oracle provider identifier
    
    @PlutusField(order = 1)
    private Map<byte[], PlutusData> dataFeeds; // Feed name -> Current value
    
    @PlutusField(order = 2)
    private BigInteger lastUpdate;      // Timestamp of last update
    
    @PlutusField(order = 3)
    private BigInteger validityPeriod;  // How long data remains valid
    
    @PlutusField(order = 4)
    private byte[] signature;           // Provider's signature
}

// Oracle consumer datum
@Constr(alternative = 0) 
public class OracleConsumerDatum implements Data<OracleConsumerDatum> {
    @PlutusField(order = 0)
    private byte[] requiredOracle;      // Oracle address to read from
    
    @PlutusField(order = 1)
    private byte[] dataFeedKey;         // Which data feed to use
    
    @PlutusField(order = 2)
    private BigInteger threshold;       // Threshold value for action
    
    @PlutusField(order = 3)
    private BigInteger lastOracleValue; // Last read oracle value
}

public class OracleIntegrationContract {
    private final PlutusV2Script oracleScript;
    private final PlutusV2Script consumerScript;
    private final QuickTxBuilder txBuilder;
    
    // Oracle provider operations
    public Result<String> updateOracleData(Account oracleProvider, Map<String, PlutusData> newDataFeeds) {
        String oracleAddress = AddressUtil.getEnterprise(oracleScript, Networks.testnet()).toBech32();
        
        // Find current oracle UTXO
        Utxo currentOracleUtxo = findLatestOracleUtxo(oracleAddress);
        OracleDatum currentDatum = null;
        
        if (currentOracleUtxo != null) {
            currentDatum = OracleDatum.fromPlutusData((ConstrPlutusData) currentOracleUtxo.getInlineDatum());
        }
        
        // Create updated oracle datum
        OracleDatum newDatum = new OracleDatum();
        newDatum.setOracleProvider(oracleProvider.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        newDatum.setDataFeeds(convertDataFeeds(newDataFeeds));
        newDatum.setLastUpdate(BigInteger.valueOf(System.currentTimeMillis() / 1000));
        newDatum.setValidityPeriod(BigInteger.valueOf(3600)); // 1 hour validity
        newDatum.setSignature(signOracleData(oracleProvider, newDataFeeds));
        
        if (currentOracleUtxo == null) {
            // Initial oracle deployment
            Tx deployTx = new Tx()
                .payTo(oracleAddress, Amount.ada(2.0))
                .attachDatum(newDatum.toPlutusData())
                .from(oracleProvider.baseAddress());
                
            return txBuilder.compose(deployTx)
                .withSigner(SignerProviders.signerFrom(oracleProvider))
                .completeAndSubmit();
        } else {
            // Update existing oracle
            ScriptTx updateTx = new ScriptTx()
                .collectFrom(currentOracleUtxo, createOracleUpdateRedeemer())
                .payTo(oracleAddress, extractValue(currentOracleUtxo))
                .attachDatum(newDatum.toPlutusData())
                .attachSpendingValidator(oracleScript)
                .withRequiredSigners(oracleProvider.getBaseAddress());
                
            return txBuilder.compose(updateTx)
                .withSigner(SignerProviders.signerFrom(oracleProvider))
                .withTxEvaluator()
                .completeAndSubmit();
        }
    }
    
    // Consumer contract operations
    public Result<String> executeWithOracleData(OracleConsumerDatum consumerDatum, Account executor) {
        // Find oracle UTXO
        String oracleAddress = AddressUtil.fromBytes(consumerDatum.getRequiredOracle()).toBech32();
        Utxo oracleUtxo = findLatestOracleUtxo(oracleAddress);
        
        if (oracleUtxo == null) {
            throw new RuntimeException("Oracle data not found");
        }
        
        OracleDatum oracleDatum = OracleDatum.fromPlutusData((ConstrPlutusData) oracleUtxo.getInlineDatum());
        
        // Verify oracle data is still valid
        long currentTime = System.currentTimeMillis() / 1000;
        long dataAge = currentTime - oracleDatum.getLastUpdate().longValue();
        
        if (dataAge > oracleDatum.getValidityPeriod().longValue()) {
            throw new IllegalStateException("Oracle data expired");
        }
        
        // Extract required data feed value
        PlutusData feedValue = oracleDatum.getDataFeeds().get(consumerDatum.getDataFeedKey());
        if (feedValue == null) {
            throw new IllegalArgumentException("Required data feed not found");
        }
        
        BigInteger oracleValue = extractNumericValue(feedValue);
        
        // Check if threshold is met
        if (oracleValue.compareTo(consumerDatum.getThreshold()) < 0) {
            throw new IllegalStateException("Oracle value below threshold");
        }
        
        // Find consumer UTXO
        String consumerAddress = AddressUtil.getEnterprise(consumerScript, Networks.testnet()).toBech32();
        Utxo consumerUtxo = findConsumerUtxo(consumerAddress, consumerDatum);
        
        // Create redeemer with oracle proof
        PlutusData redeemer = createOracleConsumerRedeemer(oracleUtxo, oracleValue);
        
        // Execute consumer action
        ScriptTx consumerTx = new ScriptTx()
            .readFrom(oracleUtxo)  // Reference oracle UTXO without consuming
            .collectFrom(consumerUtxo, redeemer)
            .payTo(executor.baseAddress(), extractValue(consumerUtxo))
            .attachSpendingValidator(consumerScript)
            .withRequiredSigners(executor.getBaseAddress());
            
        return txBuilder.compose(consumerTx)
            .withSigner(SignerProviders.signerFrom(executor))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private byte[] signOracleData(Account provider, Map<String, PlutusData> dataFeeds) {
        String dataString = dataFeeds.entrySet().stream()
            .map(e -> e.getKey() + ":" + e.getValue().toString())
            .collect(Collectors.joining(","));
        return provider.sign(dataString.getBytes());
    }
    
    private Utxo findLatestOracleUtxo(String oracleAddress) {
        return txBuilder.getUtxoSupplier().getUtxos(oracleAddress).stream()
            .filter(utxo -> utxo.getInlineDatum() != null)
            .max(Comparator.comparing(utxo -> {
                try {
                    OracleDatum datum = OracleDatum.fromPlutusData((ConstrPlutusData) utxo.getInlineDatum());
                    return datum.getLastUpdate();
                } catch (Exception e) {
                    return BigInteger.ZERO;
                }
            }))
            .orElse(null);
    }
}
```

### Advanced Oracle Pattern: Multi-Oracle Aggregation

```java
// Aggregated oracle datum for consensus
@Constr(alternative = 0)
public class AggregatedOracleDatum implements Data<AggregatedOracleDatum> {
    @PlutusField(order = 0)
    private List<byte[]> oracleProviders;    // List of oracle addresses
    
    @PlutusField(order = 1)
    private BigInteger requiredOracles;      // Minimum oracles for consensus
    
    @PlutusField(order = 2)
    private byte[] dataFeedKey;             // Which feed to aggregate
    
    @PlutusField(order = 3)
    private BigInteger aggregationType;      // 0=median, 1=average, 2=min, 3=max
    
    @PlutusField(order = 4)
    private BigInteger lastAggregation;     // Last aggregation timestamp
    
    @PlutusField(order = 5)
    private PlutusData aggregatedValue;     // Current aggregated value
}

public class MultiOracleAggregator {
    
    public Result<String> aggregateOracleData(AggregatedOracleDatum aggregationDatum, Account aggregator) {
        List<OracleDataPoint> oracleData = new ArrayList<>();
        
        // Collect data from all oracles
        for (byte[] oracleAddress : aggregationDatum.getOracleProviders()) {
            String address = AddressUtil.fromBytes(oracleAddress).toBech32();
            Utxo oracleUtxo = findLatestOracleUtxo(address);
            
            if (oracleUtxo != null) {
                OracleDatum datum = OracleDatum.fromPlutusData((ConstrPlutusData) oracleUtxo.getInlineDatum());
                PlutusData feedValue = datum.getDataFeeds().get(aggregationDatum.getDataFeedKey());
                
                if (feedValue != null && isOracleDataValid(datum)) {
                    oracleData.add(new OracleDataPoint(oracleUtxo, datum, feedValue));
                }
            }
        }
        
        // Check if we have enough oracles
        if (oracleData.size() < aggregationDatum.getRequiredOracles().intValue()) {
            throw new IllegalStateException("Insufficient valid oracle data");
        }
        
        // Perform aggregation
        PlutusData aggregatedValue = performAggregation(oracleData, aggregationDatum.getAggregationType());
        
        // Update aggregation contract
        AggregatedOracleDatum newDatum = new AggregatedOracleDatum();
        newDatum.setOracleProviders(aggregationDatum.getOracleProviders());
        newDatum.setRequiredOracles(aggregationDatum.getRequiredOracles());
        newDatum.setDataFeedKey(aggregationDatum.getDataFeedKey());
        newDatum.setAggregationType(aggregationDatum.getAggregationType());
        newDatum.setLastAggregation(BigInteger.valueOf(System.currentTimeMillis() / 1000));
        newDatum.setAggregatedValue(aggregatedValue);
        
        // Build transaction with oracle references
        ScriptTx aggregationTx = new ScriptTx();
        
        // Add all oracle UTXOs as reference inputs
        for (OracleDataPoint dataPoint : oracleData) {
            aggregationTx.readFrom(dataPoint.utxo);
        }
        
        // Update aggregation UTXO
        Utxo aggregationUtxo = findAggregationUtxo(aggregationDatum);
        aggregationTx.collectFrom(aggregationUtxo, createAggregationRedeemer(oracleData))
                     .payTo(getAggregatorAddress(), extractValue(aggregationUtxo))
                     .attachDatum(newDatum.toPlutusData())
                     .withRequiredSigners(aggregator.getBaseAddress());
                     
        return txBuilder.compose(aggregationTx)
            .withSigner(SignerProviders.signerFrom(aggregator))
            .withTxEvaluator()
            .completeAndSubmit();
    }
    
    private PlutusData performAggregation(List<OracleDataPoint> dataPoints, BigInteger aggregationType) {
        List<BigInteger> values = dataPoints.stream()
            .map(dp -> extractNumericValue(dp.value))
            .sorted()
            .collect(Collectors.toList());
            
        return switch (aggregationType.intValue()) {
            case 0 -> BigIntPlutusData.of(calculateMedian(values));     // Median
            case 1 -> BigIntPlutusData.of(calculateAverage(values));    // Average
            case 2 -> BigIntPlutusData.of(values.get(0));              // Min
            case 3 -> BigIntPlutusData.of(values.get(values.size()-1)); // Max
            default -> throw new IllegalArgumentException("Invalid aggregation type");
        };
    }
    
    private BigInteger calculateMedian(List<BigInteger> sortedValues) {
        int size = sortedValues.size();
        if (size % 2 == 0) {
            return sortedValues.get(size/2 - 1)
                .add(sortedValues.get(size/2))
                .divide(BigInteger.valueOf(2));
        } else {
            return sortedValues.get(size/2);
        }
    }
    
    private BigInteger calculateAverage(List<BigInteger> values) {
        BigInteger sum = values.stream()
            .reduce(BigInteger.ZERO, BigInteger::add);
        return sum.divide(BigInteger.valueOf(values.size()));
    }
    
    private boolean isOracleDataValid(OracleDatum datum) {
        long currentTime = System.currentTimeMillis() / 1000;
        long dataAge = currentTime - datum.getLastUpdate().longValue();
        return dataAge <= datum.getValidityPeriod().longValue();
    }
    
    private static class OracleDataPoint {
        final Utxo utxo;
        final OracleDatum datum;
        final PlutusData value;
        
        OracleDataPoint(Utxo utxo, OracleDatum datum, PlutusData value) {
            this.utxo = utxo;
            this.datum = datum;
            this.value = value;
        }
    }
}
```

### Price Feed Oracle Pattern

```java
// Specialized price feed oracle
@Constr(alternative = 0)
public class PriceFeedDatum implements Data<PriceFeedDatum> {
    @PlutusField(order = 0)
    private byte[] feedProvider;
    
    @PlutusField(order = 1)
    private Map<byte[], PriceData> priceFeeds; // Asset -> Price data
    
    @PlutusField(order = 2)
    private BigInteger updateFrequency;        // Minimum seconds between updates
    
    @PlutusField(order = 3)
    private BigInteger lastUpdate;
}

@Constr(alternative = 0)
public class PriceData implements Data<PriceData> {
    @PlutusField(order = 0)
    private BigInteger price;           // Price in smallest unit
    
    @PlutusField(order = 1) 
    private BigInteger decimals;        // Decimal places
    
    @PlutusField(order = 2)
    private BigInteger confidence;      // Confidence interval
    
    @PlutusField(order = 3)
    private BigInteger volume24h;       // 24h volume
}

public class PriceFeedOracle {
    
    public Result<String> executePriceBasedAction(String assetId, BigInteger targetPrice, 
                                                 boolean isBuyOrder, Amount tradeAmount, Account trader) {
        // Find price oracle
        PriceFeedDatum priceFeed = findPriceFeed();
        PriceData assetPrice = priceFeed.getPriceFeeds().get(assetId.getBytes());
        
        if (assetPrice == null) {
            throw new IllegalArgumentException("Asset price not available");
        }
        
        // Verify price conditions
        BigInteger currentPrice = assetPrice.getPrice();
        boolean priceConditionMet = isBuyOrder ? 
            currentPrice.compareTo(targetPrice) <= 0 :  // Buy if price <= target
            currentPrice.compareTo(targetPrice) >= 0;   // Sell if price >= target
            
        if (!priceConditionMet) {
            throw new IllegalStateException("Price condition not met");
        }
        
        // Execute trade with price proof
        return executeTradeWithPriceProof(assetId, assetPrice, tradeAmount, trader);
    }
    
    public Result<String> createConditionalOrder(String assetId, BigInteger triggerPrice,
                                               boolean isBuyOrder, Amount orderAmount, 
                                               long expirationHours, Account trader) {
        ConditionalOrderDatum orderDatum = new ConditionalOrderDatum();
        orderDatum.setTrader(trader.getBaseAddress().getPaymentCredentialHash().orElseThrow());
        orderDatum.setAssetId(assetId.getBytes());
        orderDatum.setTriggerPrice(triggerPrice);
        orderDatum.setOrderType(isBuyOrder ? BigInteger.ZERO : BigInteger.ONE);
        orderDatum.setOrderAmount(orderAmount.getCoin());
        orderDatum.setExpiration(BigInteger.valueOf(System.currentTimeMillis() / 1000 + expirationHours * 3600));
        orderDatum.setPriceOracle(getPriceOracleAddress().getBytes());
        
        String orderAddress = AddressUtil.getEnterprise(conditionalOrderScript, Networks.testnet()).toBech32();
        
        Tx orderTx = new Tx()
            .payTo(orderAddress, orderAmount)
            .attachDatum(orderDatum.toPlutusData())
            .from(trader.baseAddress());
            
        return txBuilder.compose(orderTx)
            .withSigner(SignerProviders.signerFrom(trader))
            .completeAndSubmit();
    }
}
```

## Best Practices and Security Considerations

### Validator Chaining Best Practices

1. **State Verification**: Always verify the state of referenced validators before proceeding
2. **Atomic Operations**: Ensure all validators in a chain execute atomically
3. **Error Propagation**: Implement proper error handling across validator boundaries
4. **Version Control**: Include version numbers in datum to handle upgrades

### Oracle Integration Security

1. **Data Freshness**: Always check oracle data timestamps and validity periods
2. **Multiple Sources**: Use aggregated data from multiple oracles when possible
3. **Signature Verification**: Verify oracle provider signatures
4. **Fallback Mechanisms**: Implement fallbacks for oracle failures
5. **Rate Limiting**: Prevent oracle data manipulation through update frequency limits

### Performance Optimization

1. **Reference Inputs**: Use reference inputs instead of consuming oracle UTXOs
2. **Batch Operations**: Combine multiple validator interactions in single transactions
3. **Data Compression**: Minimize datum size for chain efficiency
4. **Caching Strategies**: Cache oracle data locally when appropriate

### Testing Strategies

```java
@Test
public void testValidatorChainExecution() {
    // Test complete chain execution
    ValidatorChainContract chain = new ValidatorChainContract(validators, txBuilder);
    
    // Initialize chain
    Result<String> initResult = chain.initiateChain(account, "validator1", initialData, Amount.ada(10));
    assertTrue(initResult.isSuccessful());
    
    // Execute chain steps
    Result<String> step1Result = chain.executeChainStep("validator1", datum1, "validator2", processedData1, account);
    assertTrue(step1Result.isSuccessful());
    
    Result<String> step2Result = chain.executeChainStep("validator2", datum2, "validator3", processedData2, account);
    assertTrue(step2Result.isSuccessful());
    
    // Finalize chain
    Result<String> finalResult = chain.finalizeChain("validator3", datum3, recipientAddress, account);
    assertTrue(finalResult.isSuccessful());
}

@Test
public void testOracleDataAggregation() {
    // Test multi-oracle aggregation
    MultiOracleAggregator aggregator = new MultiOracleAggregator();
    
    // Setup multiple oracle data points
    List<byte[]> oracleAddresses = setupTestOracles(3);
    
    AggregatedOracleDatum aggregationDatum = new AggregatedOracleDatum();
    aggregationDatum.setOracleProviders(oracleAddresses);
    aggregationDatum.setRequiredOracles(BigInteger.valueOf(2));
    aggregationDatum.setDataFeedKey("ADA/USD".getBytes());
    aggregationDatum.setAggregationType(BigInteger.ZERO); // Median
    
    // Execute aggregation
    Result<String> result = aggregator.aggregateOracleData(aggregationDatum, aggregatorAccount);
    assertTrue(result.isSuccessful());
    
    // Verify aggregated value
    PlutusData aggregatedValue = getAggregatedValue(result.getValue());
    assertNotNull(aggregatedValue);
}
```

This comprehensive guide provides the foundation for implementing sophisticated smart contract interaction patterns. The examples demonstrate real-world scenarios while maintaining security and efficiency best practices.
---
description: Complete guide to Plutus script execution including Redeemer/Datum handling, ExUnits calculation, and version differences
sidebar_label: Script Execution
sidebar_position: 2
---

# Plutus Script Execution

This comprehensive guide covers all aspects of Plutus script execution in the Cardano Client Library, including Datum and Redeemer handling, execution units (ExUnits) calculation, script cost evaluation, and the differences between Plutus V1, V2, and V3 versions.

:::tip Prerequisites
Understanding of [PlutusData Types](./plutus-data-types.md) is essential before working with script execution.
:::

## Overview

Plutus script execution involves several key components:
- **Scripts** - The compiled Plutus code (V1, V2, or V3)
- **Datum** - Data attached to UTXOs that scripts can read
- **Redeemer** - Data provided when spending/using scripts
- **ExUnits** - Execution limits for memory and CPU steps
- **Execution Context** - The environment in which scripts run

## Plutus Script Versions

The library supports all three Plutus script versions with distinct capabilities and cost models.

### Script Version Overview

```java
import com.bloxbean.cardano.client.plutus.spec.*;

// Plutus V1 - Original version
PlutusV1Script v1Script = PlutusV1Script.builder()
    .type("PlutusScriptV1")
    .cborHex("590a32590a2f01000...")
    .build();

// Plutus V2 - Reference scripts, inline datums, reference inputs
PlutusV2Script v2Script = PlutusV2Script.builder()
    .type("PlutusScriptV2") 
    .cborHex("590b12590b0f01000...")
    .build();

// Plutus V3 - Governance features, BitWise operations, additional builtins
PlutusV3Script v3Script = PlutusV3Script.builder()
    .type("PlutusScriptV3")
    .cborHex("590c45590c4201000...")
    .build();
```

### Language Identification

```java
import com.bloxbean.cardano.client.plutus.spec.Language;

// Get script language
Language v1Lang = Language.PLUTUS_V1; // Internal value: 0
Language v2Lang = Language.PLUTUS_V2; // Internal value: 1  
Language v3Lang = Language.PLUTUS_V3; // Internal value: 2

// Version-specific properties
System.out.println("V1 Script Type: " + v1Script.getScriptType()); // 1
System.out.println("V2 Script Type: " + v2Script.getScriptType()); // 2
System.out.println("V3 Script Type: " + v3Script.getScriptType()); // 3
```

### Version Capabilities Comparison

| Feature | Plutus V1 | Plutus V2 | Plutus V3 |
|---------|-----------|-----------|-----------|
| Basic validation | ✅ | ✅ | ✅ |
| Reference scripts | ❌ | ✅ | ✅ |
| Inline datums | ❌ | ✅ | ✅ |
| Reference inputs | ❌ | ✅ | ✅ |
| Governance scripts | ❌ | ❌ | ✅ |
| BitWise operations | ❌ | ❌ | ✅ |
| BLS12-381 primitives | ❌ | ❌ | ✅ |
| Keccak-256, Blake2b-224 | ❌ | ❌ | ✅ |

## Datum Handling

Datums are PlutusData values attached to UTXOs that provide context for script validation.

### Creating Datums

```java
import com.bloxbean.cardano.client.plutus.spec.*;

// Simple datum with constructor pattern
PlutusData simpleDatum = ConstrPlutusData.of(0,
    BytesPlutusData.of("owner_address"),
    BigIntPlutusData.of(1000000), // Amount in lovelace
    BigIntPlutusData.of(System.currentTimeMillis() / 1000) // Timestamp
);

// Complex datum with nested structures
PlutusData orderDatum = ConstrPlutusData.of(0,
    // Order ID
    BytesPlutusData.of("ORDER-001"),
    
    // Seller info (nested constructor)
    ConstrPlutusData.of(1,
        BytesPlutusData.of("seller_address"),
        BytesPlutusData.of("seller_pubkey")
    ),
    
    // Items list
    ListPlutusData.of(
        ConstrPlutusData.of(0, // Item 1
            BytesPlutusData.of("policy_id_1"),
            BytesPlutusData.of("asset_name_1"),
            BigIntPlutusData.of(100)
        ),
        ConstrPlutusData.of(0, // Item 2
            BytesPlutusData.of("policy_id_2"), 
            BytesPlutusData.of("asset_name_2"),
            BigIntPlutusData.of(50)
        )
    ),
    
    // Metadata map
    MapPlutusData.of(Map.of(
        BytesPlutusData.of("priority"), BytesPlutusData.of("high"),
        BytesPlutusData.of("expires"), BigIntPlutusData.of(1234567890)
    ))
);
```

### Datum Hash Calculation

```java
// Calculate datum hash (for datum hash in UTXO)
String datumHash = simpleDatum.getDatumHash();
System.out.println("Datum Hash: " + datumHash);

// Get datum hash as bytes
byte[] datumHashBytes = simpleDatum.getDatumHashAsBytes();
```

### Datum Serialization

```java
// Serialize datum to CBOR hex
String datumCbor = simpleDatum.serializeToHex();

// Deserialize datum from CBOR
PlutusData restoredDatum = PlutusData.deserialize(
    HexUtil.decodeHexString(datumCbor)
);

// Verify roundtrip
assert simpleDatum.getDatumHash().equals(restoredDatum.getDatumHash());
```

### Working with Inline Datums (V2+)

```java
// For Plutus V2/V3, datums can be stored inline with UTXOs
// This eliminates the need for datum lookups during validation

// Create UTXO with inline datum
TransactionOutput utxoWithInlineDatum = TransactionOutput.builder()
    .address("addr1...")
    .value(Value.of(2000000)) // 2 ADA
    .inlineDatum(orderDatum) // Datum stored directly in UTXO
    .build();

// Scripts can access inline datums directly without additional witness data
```

## Redeemer Handling

Redeemers provide the input data that scripts use during validation. They are associated with specific transaction actions.

### Redeemer Structure

```java
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;

// Basic redeemer structure
Redeemer redeemer = Redeemer.builder()
    .tag(RedeemerTag.Spend)    // What type of action
    .index(BigInteger.valueOf(0)) // Index of the action
    .data(redeemerData)        // PlutusData payload
    .exUnits(exUnits)          // Execution limits
    .build();
```

### Redeemer Tags

Different script purposes use different redeemer tags:

```java
// Spending from script address
RedeemerTag.Spend    // Value: 0 - Spending script UTXO
RedeemerTag.Mint     // Value: 1 - Minting/burning tokens  
RedeemerTag.Cert     // Value: 2 - Certificate operations
RedeemerTag.Reward   // Value: 3 - Reward withdrawals
RedeemerTag.Voting   // Value: 4 - Governance voting (V3)
RedeemerTag.Proposing // Value: 5 - Governance proposals (V3)
```

### Creating Redeemers for Different Purposes

#### Spending Redeemer

```java
// Unlock script with specific action
PlutusData unlockRedeemer = ConstrPlutusData.of(0, // Action: Unlock
    BytesPlutusData.of("owner_signature"),
    BigIntPlutusData.of(System.currentTimeMillis())
);

Redeemer spendRedeemer = Redeemer.builder()
    .tag(RedeemerTag.Spend)
    .index(BigInteger.valueOf(0)) // First script input index
    .data(unlockRedeemer)
    .exUnits(ExUnits.builder()
        .mem(BigInteger.valueOf(1000000))
        .steps(BigInteger.valueOf(500000000))
        .build())
    .build();
```

#### Minting Redeemer

```java
// Mint tokens with metadata
PlutusData mintRedeemer = ConstrPlutusData.of(1, // Action: Mint
    BigIntPlutusData.of(1000), // Amount to mint
    MapPlutusData.of(Map.of(    // Token metadata
        BytesPlutusData.of("name"), BytesPlutusData.of("MyToken"),
        BytesPlutusData.of("symbol"), BytesPlutusData.of("MTK")
    ))
);

Redeemer mintingRedeemer = Redeemer.builder()
    .tag(RedeemerTag.Mint)
    .index(BigInteger.valueOf(0)) // First minting policy index
    .data(mintRedeemer)
    .exUnits(ExUnits.builder()
        .mem(BigInteger.valueOf(2000000))
        .steps(BigInteger.valueOf(750000000))
        .build())
    .build();
```

#### Governance Redeemer (V3 only)

```java
// Vote on governance proposal
PlutusData voteRedeemer = ConstrPlutusData.of(0, // Action: Vote
    BigIntPlutusData.of(42),        // Proposal ID
    BigIntPlutusData.of(1),         // Vote: Yes=1, No=0, Abstain=2
    BytesPlutusData.of("rationale") // Voting rationale
);

Redeemer governanceRedeemer = Redeemer.builder()
    .tag(RedeemerTag.Voting)
    .index(BigInteger.valueOf(0))
    .data(voteRedeemer)
    .exUnits(ExUnits.builder()
        .mem(BigInteger.valueOf(500000))
        .steps(BigInteger.valueOf(250000000))
        .build())
    .build();
```

### Redeemer Indexing

Proper redeemer indexing is crucial for script execution:

```java
import com.bloxbean.cardano.client.function.helper.RedeemerUtil;

// Calculate proper index for spending redeemer
List<TransactionInput> inputs = transaction.getBody().getInputs();
TransactionInput scriptInput = inputs.get(2); // Script input is 3rd input

// Get the index among script inputs only (not all inputs)
int redeemerIndex = RedeemerUtil.getScriptInputIndex(inputs, scriptInput);

Redeemer properlyIndexedRedeemer = redeemer.toBuilder()
    .index(BigInteger.valueOf(redeemerIndex))
    .build();
```

## ExUnits and Cost Calculation

ExUnits represent the execution limits for memory and CPU steps that a script is allowed to consume.

### ExUnits Structure

```java
import com.bloxbean.cardano.client.plutus.spec.ExUnits;

// Define execution limits
ExUnits exUnits = ExUnits.builder()
    .mem(BigInteger.valueOf(2000000))    // Memory units (bytes)
    .steps(BigInteger.valueOf(500000000)) // CPU steps
    .build();

// Access values
BigInteger memoryLimit = exUnits.getMem();
BigInteger cpuStepsLimit = exUnits.getSteps();
```

### Manual ExUnits Setting

```java
// Conservative limits for testing
ExUnits testLimits = ExUnits.builder()
    .mem(BigInteger.valueOf(14000000))     // 14MB memory
    .steps(BigInteger.valueOf(10000000000L)) // 10B CPU steps
    .build();

// Production limits (should be calculated)
ExUnits prodLimits = ExUnits.builder()
    .mem(BigInteger.valueOf(1200000))      // Actual usage + buffer
    .steps(BigInteger.valueOf(450000000))   // Actual usage + buffer
    .build();
```

### Automatic Cost Evaluation

The library provides automatic script cost evaluation:

```java
import com.bloxbean.cardano.client.function.helper.ScriptCostEvaluators;
import com.bloxbean.cardano.client.api.TransactionEvaluator;

// Build transaction with script interactions
Transaction tx = // ... transaction with script calls

// Evaluate script costs automatically
TxBuilderContext context = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier);
context.setTransactionEvaluator(transactionEvaluator);

// This function will update ExUnits in all redeemers
Transaction evaluatedTx = ScriptCostEvaluators.evaluateScriptCost(tx, context);

// All redeemers now have accurate ExUnits based on actual execution
```

### Cost Evaluation Process

```java
// The evaluation process works as follows:

// 1. Build transaction with placeholder ExUnits
List<Redeemer> redeemers = Arrays.asList(
    Redeemer.builder()
        .tag(RedeemerTag.Spend)
        .index(BigInteger.ZERO)
        .data(spendRedeemer)
        .exUnits(ExUnits.builder() // Placeholder values
            .mem(BigInteger.valueOf(10000000))
            .steps(BigInteger.valueOf(10000000000L))
            .build())
        .build()
);

// 2. Create transaction evaluator (backend-specific)
TransactionEvaluator evaluator = new BlockfrostTransactionEvaluator(backendService);

// 3. Evaluate transaction
byte[] txBytes = tx.serialize();
Set<Utxo> inputUtxos = // ... collect all input UTXOs including script UTXOs
List<EvaluationResult> results = evaluator.evaluateTx(txBytes, inputUtxos);

// 4. Update redeemers with actual costs
for (EvaluationResult result : results) {
    // Find matching redeemer and update ExUnits
    updateRedeemerExUnits(redeemers, result);
}
```

### Understanding Evaluation Results

```java
import com.bloxbean.cardano.client.api.model.EvaluationResult;

// Evaluation result contains actual execution costs
EvaluationResult result = evaluationResults.get(0);

RedeemerTag tag = result.getRedeemerTag();    // Which redeemer type
int index = result.getIndex();                // Which redeemer index
ExUnits actualCosts = result.getExUnits();    // Actual execution costs

System.out.println("Script used " + actualCosts.getMem() + " memory units");
System.out.println("Script used " + actualCosts.getSteps() + " CPU steps");

// Add safety buffer for production
ExUnits safetyLimits = ExUnits.builder()
    .mem(actualCosts.getMem().multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100))) // +10%
    .steps(actualCosts.getSteps().multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100))) // +10%
    .build();
```

## Cost Models and Version Differences

Each Plutus version uses different cost models for calculating execution costs.

### Cost Model Management

```java
import com.bloxbean.cardano.client.plutus.spec.CostMdls;

// Cost models are provided by protocol parameters
CostMdls costModel = protocolParams.getCostModels();

// Get language-specific cost model encoding
Map<Language, byte[]> languageViews = costModel.getLanguageViewEncoding();

// V1 uses legacy serialization format
byte[] v1CostModel = languageViews.get(Language.PLUTUS_V1);

// V2 and V3 use modern serialization format
byte[] v2CostModel = languageViews.get(Language.PLUTUS_V2);
byte[] v3CostModel = languageViews.get(Language.PLUTUS_V3);
```

### Version-Specific Execution Costs

Different Plutus versions have different cost structures:

```java
// V1 Scripts - Basic operations
// - Limited builtin functions
// - Higher costs for some operations
// - No reference script optimization

ExUnits v1TypicalCosts = ExUnits.builder()
    .mem(BigInteger.valueOf(1500000))      // ~1.5MB typical
    .steps(BigInteger.valueOf(600000000))   // ~600M steps typical
    .build();

// V2 Scripts - Optimized operations  
// - More efficient builtin functions
// - Reference script support reduces costs
// - Inline datum optimization

ExUnits v2TypicalCosts = ExUnits.builder()
    .mem(BigInteger.valueOf(1200000))      // ~1.2MB typical (improved)
    .steps(BigInteger.valueOf(450000000))   // ~450M steps typical (improved)
    .build();

// V3 Scripts - Latest optimizations
// - Additional builtin functions
// - BitWise operations
// - BLS12-381 cryptographic primitives

ExUnits v3TypicalCosts = ExUnits.builder()
    .mem(BigInteger.valueOf(1000000))      // ~1MB typical (further improved)
    .steps(BigInteger.valueOf(400000000))   // ~400M steps typical (further improved)
    .build();
```

## Script Execution Context

Managing the execution context is crucial for proper script validation.

### Basic Execution Context

```java
import com.bloxbean.cardano.client.function.helper.model.ScriptCallContext;

// Create execution context for spending script
ScriptCallContext<PlutusData, PlutusData> spendingContext = 
    ScriptCallContext.<PlutusData, PlutusData>builder()
        .script(plutusScript)
        .utxo(scriptUtxo)               // UTXO being spent
        .datum(datumData)               // Datum attached to UTXO
        .redeemer(redeemerData)         // Redeemer provided by spender
        .redeemerTag(RedeemerTag.Spend)
        .exUnits(exUnits)
        .build();
```

### Advanced Context Management

```java
// Context for minting script
ScriptCallContext<PlutusData, PlutusData> mintingContext = 
    ScriptCallContext.<PlutusData, PlutusData>builder()
        .script(mintingScript)
        .utxo(null)                     // No UTXO for minting
        .datum(null)                    // No datum for minting
        .redeemer(mintRedeemer)         // Minting parameters
        .redeemerTag(RedeemerTag.Mint)
        .exUnits(mintExUnits)
        .build();

// Context for governance script (V3 only)
ScriptCallContext<PlutusData, PlutusData> governanceContext = 
    ScriptCallContext.<PlutusData, PlutusData>builder()
        .script(governanceScript)
        .utxo(null)                     // No UTXO for governance
        .datum(proposalDatum)           // Proposal data
        .redeemer(voteRedeemer)         // Vote decision
        .redeemerTag(RedeemerTag.Voting)
        .exUnits(governanceExUnits)
        .build();
```

### Context Validation

```java
// Validate context before execution
public boolean validateContext(ScriptCallContext<?, ?> context) {
    PlutusScript script = context.getScript();
    RedeemerTag tag = context.getRedeemerTag();
    
    // Check version compatibility
    if (script instanceof PlutusV1Script && tag == RedeemerTag.Voting) {
        throw new IllegalArgumentException("V1 scripts don't support governance");
    }
    
    // Check required fields
    if (tag == RedeemerTag.Spend && context.getUtxo() == null) {
        throw new IllegalArgumentException("Spending scripts require UTXO");
    }
    
    if (tag == RedeemerTag.Mint && context.getUtxo() != null) {
        throw new IllegalArgumentException("Minting scripts should not have UTXO");
    }
    
    // Validate ExUnits limits
    ExUnits exUnits = context.getExUnits();
    if (exUnits.getSteps().compareTo(BigInteger.valueOf(10000000000L)) > 0) {
        throw new IllegalArgumentException("ExUnits steps exceed maximum limit");
    }
    
    return true;
}
```

## Serialization and Conway Era Support

The library supports both pre-Conway and Conway era redeemer serialization formats.

### Conway Era Changes

```java
// Pre-Conway redeemer format (legacy)
// [tag, index, data, ex_units]

// Conway era redeemer format (current)  
// tag => int
// index => int  
// data => plutus_data
// ex_units => [mem, steps]

// Automatic format detection and serialization
Redeemer redeemer = Redeemer.builder()
    .tag(RedeemerTag.Spend)
    .index(BigInteger.ZERO)
    .data(redeemerData)
    .exUnits(exUnits)
    .build();

// Serialize using current format (Conway)
byte[] conwayFormat = redeemer.serialize();

// Serialize using legacy format (pre-Conway) if needed
byte[] legacyFormat = redeemer.serializePreConway();
```

### Deserialization Handling

```java
// Automatic format detection during deserialization
byte[] serializedRedeemer = // ... from transaction or storage

try {
    // Try Conway format first
    Redeemer redeemer = Redeemer.deserialize(serializedRedeemer);
    System.out.println("Successfully parsed Conway format");
} catch (Exception e) {
    try {
        // Fallback to pre-Conway format
        Redeemer redeemer = Redeemer.deserializePreConway(serializedRedeemer);
        System.out.println("Successfully parsed pre-Conway format");
    } catch (Exception e2) {
        throw new RuntimeException("Unable to parse redeemer format", e2);
    }
}
```

## Practical Examples

### Complete Script Execution Example

```java
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;

// 1. Load Plutus script
PlutusV2Script lockingScript = PlutusV2Script.builder()
    .type("PlutusScriptV2")
    .cborHex("590a32590a2f01000...") // Your compiled script
    .build();

// 2. Create datum for locking
PlutusData lockDatum = ConstrPlutusData.of(0,
    BytesPlutusData.of("owner_pubkey_hash"),
    BigIntPlutusData.of(1000000), // Amount
    BigIntPlutusData.of(System.currentTimeMillis() / 1000) // Lock until timestamp
);

// 3. Lock funds at script address
String scriptAddress = AddressUtil.getEnterprise(lockingScript, Networks.testnet()).toBech32();

Tx lockTx = new Tx()
    .payTo(scriptAddress, Amount.ada(2.0))
    .attachDatum(lockDatum);

Result<String> lockResult = quickTxBuilder.compose(lockTx).completeAndSubmit();

// 4. Later, unlock the funds
PlutusData unlockRedeemer = ConstrPlutusData.of(0,
    BytesPlutusData.of("owner_signature_proof"),
    BigIntPlutusData.of(System.currentTimeMillis())
);

// Find the locked UTXO
Utxo lockedUtxo = // ... find UTXO at script address

ScriptTx unlockTx = new ScriptTx()
    .collectFrom(lockedUtxo, unlockRedeemer)
    .payTo(ownerAddress, Amount.ada(1.9)) // Pay back minus fees
    .attachSpendingValidator(lockingScript);

// 5. Evaluate and submit
Result<String> unlockResult = quickTxBuilder
    .compose(unlockTx)
    .withTxEvaluator() // Automatically calculate ExUnits
    .completeAndSubmit();
```

### Multi-Script Transaction

```java
// Transaction with multiple script types
ScriptTx complexTx = new ScriptTx()
    // Spend from script (spending validator)
    .collectFrom(scriptUtxo1, spendRedeemer)
    .attachSpendingValidator(spendingScript)
    
    // Mint tokens (minting policy)
    .mintAssets(mintingScript, mintRedeemer, 
        Asset.builder()
            .policyId(mintingScript.getPolicyId())
            .assetName("MyToken")
            .amount(BigInteger.valueOf(1000))
            .build())
    
    // Pay to new addresses
    .payTo(receiverAddress, Amount.ada(1.5))
    .payTo(changeAddress, Amount.ada(0.3));

// All scripts will be evaluated automatically
Result<String> result = quickTxBuilder
    .compose(complexTx)
    .withTxEvaluator()
    .completeAndSubmit();
```

### Error Handling and Debugging

```java
// Common script execution errors and solutions

try {
    Result<String> result = quickTxBuilder
        .compose(scriptTx)
        .withTxEvaluator()
        .completeAndSubmit();
        
    if (!result.isSuccessful()) {
        System.err.println("Transaction failed: " + result.getResponse());
    }
    
} catch (Exception e) {
    if (e.getMessage().contains("ExUnitsTooBigUTxO")) {
        // ExUnits too large - reduce limits or optimize script
        System.err.println("Script execution limits too high");
        
    } else if (e.getMessage().contains("ScriptWitnessNotValidatingUTxO")) {
        // Script validation failed - check datum/redeemer data
        System.err.println("Script validation failed - check your datum and redeemer");
        
    } else if (e.getMessage().contains("MissingVKeyWitnessesUTXOW")) {
        // Missing required signatures
        System.err.println("Missing required signatures");
        
    } else if (e.getMessage().contains("InsufficientFundsUTxO")) {
        // Not enough funds to cover transaction + script fees
        System.err.println("Insufficient funds for transaction and script fees");
        
    } else {
        System.err.println("Unexpected error: " + e.getMessage());
    }
}
```

## Best Practices

### 1. ExUnits Management

```java
// Always evaluate scripts before production submission
public ExUnits getProductionExUnits(ExUnits evaluatedUnits) {
    // Add 10% safety buffer to evaluated costs
    BigInteger safeMem = evaluatedUnits.getMem()
        .multiply(BigInteger.valueOf(110))
        .divide(BigInteger.valueOf(100));
        
    BigInteger safeSteps = evaluatedUnits.getSteps()
        .multiply(BigInteger.valueOf(110))
        .divide(BigInteger.valueOf(100));
    
    return ExUnits.builder()
        .mem(safeMem)
        .steps(safeSteps)
        .build();
}
```

### 2. Datum Optimization

```java
// Minimize datum size to reduce transaction costs
public PlutusData createOptimalDatum(String owner, long amount, long expiry) {
    // Use the most compact PlutusData representation
    return ConstrPlutusData.of(0,
        // Use bytes instead of strings when possible
        BytesPlutusData.of(HexUtil.decodeHexString(owner)), // 28 bytes vs ~60 chars
        
        // Use smallest integer representation
        BigIntPlutusData.of(amount),
        
        // Pack multiple small values into single constructor
        BigIntPlutusData.of(expiry)
    );
}
```

### 3. Version Selection Strategy

```java
// Choose Plutus version based on requirements
public PlutusScript selectOptimalVersion(ScriptRequirements requirements) {
    if (requirements.needsGovernance()) {
        return createPlutusV3Script(); // Only V3 supports governance
    } else if (requirements.needsReferenceScripts() || requirements.needsInlineDatums()) {
        return createPlutusV2Script(); // V2 has these optimizations
    } else {
        return createPlutusV1Script(); // V1 for basic functionality
    }
}
```

### 4. Redeemer Design Patterns

```java
// Design redeemers for extensibility
public enum ScriptAction {
    UNLOCK(0),
    EXTEND(1), 
    CANCEL(2),
    UPGRADE(3);
    
    private final int value;
    ScriptAction(int value) { this.value = value; }
    
    public PlutusData toRedeemer(PlutusData... args) {
        List<PlutusData> fields = new ArrayList<>();
        fields.add(BigIntPlutusData.of(this.value));
        fields.addAll(Arrays.asList(args));
        
        return ConstrPlutusData.of(0, fields);
    }
}

// Usage
PlutusData unlockRedeemer = ScriptAction.UNLOCK.toRedeemer(
    BytesPlutusData.of("signature_proof")
);

PlutusData extendRedeemer = ScriptAction.EXTEND.toRedeemer(
    BigIntPlutusData.of(3600) // Extend by 1 hour
);
```

This comprehensive guide provides everything needed to work with Plutus script execution effectively, from basic concepts to advanced optimization techniques. The library's abstraction layers make script execution accessible while maintaining the flexibility needed for complex smart contract interactions.
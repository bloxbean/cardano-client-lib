## QuickTx Module (cardano-client-quicktx)

Simple high-level API for creating and sending transactions. The QuickTx module provides two main approaches for transaction building:

1. **Direct API**: Programmatic transaction building using `Tx` and `ScriptTx` objects
2. **TxPlan YAML**: Configuration-driven transactions with YAML serialization

## QuickTx API Usage

### Basic Transaction Building

Create transactions using the fluent API:

```java
// Simple payment transaction
Tx tx = new Tx()
    .from("addr_test1_sender")
    .payToAddress("addr_test1_receiver", Amount.ada(5));

// Script transaction with datum
ScriptTx scriptTx = new ScriptTx()
    .payToContract("addr_test1_contract", Amount.ada(10), datum)
    .collectFrom("utxo_txhash#0", redeemer);
```

### Using QuickTxBuilder

Build and submit transactions:

```java
QuickTxBuilder builder = new QuickTxBuilder(backendService);

Result<String> result = builder
    .compose(tx)
    .feePayer("addr_test1_fee_payer")
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait();
```

## TxPlan YAML Serialization

### Overview

TxPlan provides a powerful YAML-based approach for defining transactions with configuration management capabilities. Benefits include:

- **Configuration-driven**: Define transactions in YAML files
- **Variable resolution**: Use placeholders for dynamic values
- **Context properties**: Centralized fee payer, collateral, and validity settings
- **Multiple transactions**: Define complex multi-step operations
- **Version control friendly**: Human-readable transaction definitions

### Basic Usage

#### Creating a TxPlan

```java
// From a transaction
Tx tx = new Tx()
    .from("addr_test1_sender")
    .payToAddress("addr_test1_receiver", Amount.ada(5));

TxPlan plan = TxPlan.fromTransaction(tx)
    .setFeePayer("addr_test1_fee_payer")
    .setValidToSlot(2000L);

String yaml = plan.toYaml();
```

#### Loading from YAML

```java
// Complete restoration with context
TxPlan plan = TxPlan.fromYamlWithContext(yaml);

// Just transactions (legacy compatibility)
List<AbstractTx<?>> transactions = TxPlan.fromYaml(yaml);
```

#### Integration with QuickTxBuilder

```java
// Direct integration
QuickTxBuilder.TxContext context = builder.compose(plan);
Result<String> result = context
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait();
```

### YAML Format

#### Basic Structure

```yaml
version: 1.0
variables:
  sender: addr_test1_sender_address
  receiver: addr_test1_receiver_address
  amount: 5000000
context:
  fee_payer: addr_test1_fee_payer
  collateral_payer: addr_test1_collateral_payer
  required_signers:
    - ab123def
    - cd456efa
  valid_from_slot: 1000
  valid_to_slot: 2000
transaction:
  - tx:
      from: ${sender}
      intentions:
        - type: payment
          to: ${receiver}
          amount:
            unit: lovelace
            quantity: ${amount}
```

#### Context Properties

- **fee_payer**: Address that pays transaction fees
- **collateral_payer**: Address that provides collateral for script transactions
- **required_signers**: List of required signer credentials (hex-encoded)
- **valid_from_slot**: Transaction validity start slot (optional)
- **valid_to_slot**: Transaction validity end slot (optional)

#### Variable Resolution

Use `${variable_name}` syntax for dynamic values:

```yaml
variables:
  treasury: addr_test1_treasury
  alice: addr_test1_alice
  amount: 10000000

transaction:
  - tx:
      from: ${treasury}
      intentions:
        - type: payment
          to: ${alice}
          amount:
            unit: lovelace
            quantity: ${amount}
```

### Multiple Transactions

Define complex multi-step operations:

```yaml
version: 1.0
variables:
  treasury: addr_test1_treasury
  pool_operator: addr_test1_pool_op
  
context:
  fee_payer: ${treasury}
  valid_to_slot: 5000

transaction:
  - tx:
      from: ${treasury}
      intentions:
        - type: payment
          to: ${pool_operator}
          amount:
            unit: lovelace
            quantity: 500000000
            
  - scriptTx:
      intentions:
        - type: stake_pool_registration
          pool_id: pool1abc123
          # ... pool parameters
```

### Script Transactions in YAML

```yaml
transaction:
  - scriptTx:
      change_address: addr_test1_change
      change_datum: "590a01a1581c..."  # hex-encoded PlutusData
      intentions:
        - type: script_call
          contract_address: addr_test1_contract
          datum_hex: "d87980"
          redeemer_hex: "d87a80"
          amount:
            unit: lovelace
            quantity: 10000000
      validators:
        - script_hex: "590a01..."
          type: plutus_v2
```

## Examples

### Complete Workflow Example

```java
// 1. Define transaction plan in YAML
String yamlPlan = """
    version: 1.0
    variables:
      sender: addr_test1_sender
      receiver: addr_test1_receiver
    context:
      fee_payer: addr_test1_fee_payer
      valid_to_slot: 5000
    transaction:
      - tx:
          from: ${sender}
          intentions:
            - type: payment
              to: ${receiver}
              amount:
                unit: lovelace
                quantity: 5000000
    """;

// 2. Load and execute
QuickTxBuilder builder = new QuickTxBuilder(backendService);
TxPlan plan = TxPlan.fromYamlWithContext(yamlPlan);

Result<String> result = builder
    .compose(plan)
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait();
```

### Dynamic Configuration

```java
// Load base plan
TxPlan basePlan = TxPlan.fromYamlWithContext(yamlContent);

// Customize for environment
TxPlan customPlan = basePlan
    .setFeePayer(environmentConfig.getFeePayer())
    .setValidToSlot(getCurrentSlot() + 3600)
    .addVariable("amount", environmentConfig.getAmount());

// Execute
builder.compose(customPlan)
    .withSigner(signerProvider)
    .completeAndWait();
```

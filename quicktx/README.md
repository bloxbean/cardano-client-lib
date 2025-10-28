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

TxResult result = builder
    .compose(tx)
    .feePayer("addr_test1_fee_payer")
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait();
```

### Signer Registry & URI References

QuickTx resolves senders, fee payers, collateral payers, and extra witnesses from URI-style references using a pluggable `SignerRegistry`. This keeps YAML free from secrets while letting the runtime decide how to sign:

```java
SignerRegistry registry = new InMemorySignerRegistry()
    .addAccount("account://ops", opsAccount)
    .addPolicy("policy://nft", policy);

Tx tx = new Tx()
    .fromRef("account://ops")
    .payToAddress("addr_test1_receiver", Amount.ada(5));

TxResult result = builder
    .withSignerRegistry(registry)
    .compose(tx)
    .feePayerRef("account://ops")
    .withSignerRef("policy://nft", "policy")
    .completeAndWait();
```

- `fromRef` defers sender selection and automatically provides the payment signer.
- `feePayerRef` / `collateralPayerRef` resolve to a wallet or preferred address at compose time.
- `withSignerRef(ref, scope)` adds additional witnesses such as `stake`, `drep`, or `policy`.
- Mixing address-based APIs with references is allowed; conflicts raise a `TxBuildException`.

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

TxPlan plan = TxPlan.from(tx)
    .feePayer("addr_test1_fee_payer")
    .validTo(2000L);

String yaml = plan.toYaml();
```

#### Loading from YAML

```java
// Complete restoration with context
TxPlan plan = TxPlan.from(yaml);

// Just transactions (legacy compatibility)
List<AbstractTx<?>> transactions = plan.getTxs();
```

#### Integration with QuickTxBuilder

```java
// Direct integration
QuickTxBuilder.TxContext context = builder.compose(plan);
TxResult result = context
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait();
```

### YAML Format

#### Basic Structure

```yaml
version: 1.1
variables:
  sender_ref: account://ops_sender
  receiver: addr_test1_receiver_address
  amount: 5000000
context:
  fee_payer_ref: account://ops
  collateral_payer_ref: account://ops
  signers:
    - type: policy
      ref: policy://nft
      scope: policy
  required_signers:
    - ab123def
    - cd456efa
  valid_from_slot: 1000
  valid_to_slot: 2000
transaction:
  - tx:
      from_ref: ${sender_ref}
      intents:
        - type: payment
          to: ${receiver}
          amount:
            unit: lovelace
            quantity: ${amount}
```

Address-based fields (`from`, `fee_payer`, `collateral_payer`) remain valid for backward compatibility; mix and match them with references as needed.

#### Context Properties

- **fee_payer**: Address that pays transaction fees
- **fee_payer_ref**: Registry reference that resolves to a wallet or address for paying fees
- **collateral_payer**: Address that provides collateral for script transactions
- **collateral_payer_ref**: Registry reference used when collateral should be derived at runtime
- **signers**: Array of `{ type, ref, scope }` entries resolved via the registry for extra witnesses
- **required_signers**: List of required signer credentials (hex-encoded)
- **valid_from_slot**: Transaction validity start slot (optional)
- **valid_to_slot**: Transaction validity end slot (optional)

Whenever any `*_ref` field or `signers` are present, make sure a compatible `SignerRegistry` is supplied at runtime. If the registry cannot resolve a reference, `QuickTxBuilder` fails fast with a `TxBuildException`.

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
      intents:
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
      intents:
        - type: payment
          to: ${pool_operator}
          amount:
            unit: lovelace
            quantity: 500000000
            
  - scriptTx:
      intents:
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
      intents:
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
          intents:
            - type: payment
              to: ${receiver}
              amount:
                unit: lovelace
                quantity: 5000000
    """;

// 2. Load and execute
QuickTxBuilder builder = new QuickTxBuilder(backendService);
TxPlan plan = TxPlan.from(yamlPlan);

TxResult result = builder
    .compose(plan)
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait();
```

### Dynamic Configuration

```java
// Load base plan
TxPlan basePlan = TxPlan.from(yamlContent);

// Customize for environment
TxPlan customPlan = basePlan
    .feePayer(environmentConfig.getFeePayer())
    .validTo(getCurrentSlot() + 3600)
    .addVariable("amount", environmentConfig.getAmount());

// Execute
TxResult txResult = builder.compose(customPlan)
    .withSigner(signerProvider)
    .completeAndWait();
```

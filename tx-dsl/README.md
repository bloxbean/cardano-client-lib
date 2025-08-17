# Transaction DSL Module

A declarative Domain-Specific Language (DSL) for defining Cardano transactions in YAML format with variable support and complete serialization capabilities.

## Overview

The Transaction DSL enables developers to define complex transaction intents using human-readable YAML syntax, with full support for variables, context configuration, and transaction chaining. This module provides the foundation for the rollback and rebuild capabilities in the watcher module.

## Features

- **Declarative Syntax**: Define transaction intents rather than imperative steps
- **Variable Support**: Use `${variable}` syntax for reusable and dynamic values  
- **Complete Context**: Capture all TxBuilderContext configuration options
- **API Compliance**: Maps exactly to existing Tx/AbstractTx/ScriptTx APIs
- **Type Safety**: Strong typing with validation and error handling
- **Serialization**: JSON/YAML serialization with round-trip fidelity
- **Transaction Chaining**: Support for dependent transaction sequences

## Basic Example

```yaml
version: "1.0"
id: "basic_payment"

variables:
  sender: "addr1qx2fxv..."
  receiver: "addr1qy8ac7..."
  amount: 10

transactions:
  - id: "simple_payment"
    type: "transaction"
    intents:
      payments:
        - to: "${receiver}"
          amounts:
            - value: "${amount}"
              unit: "ADA"
      
      inputs:
        from_address: "${sender}"
        strategy: "auto"
      
      change:
        address: "${sender}"
    
    context:
      fee_payer: "${sender}"
      utxo_strategy: "LARGEST_FIRST"
      merge_outputs: true
```

## Architecture

### Core Components

- **TransactionIntentCollection**: Root container for transaction definitions
- **TransactionIntent**: Individual transaction specification
- **TransactionIntents**: Container for all intent types (payments, inputs, etc.)
- **TxExecutionContext**: Complete context configuration

### Intent Types

- **PaymentIntent**: Maps to `AbstractTx.payToAddress()`
- **InputIntent**: Maps to `Tx.from()` and `ScriptTx.from()`
- **ChangeIntent**: Maps to `AbstractTx.withChangeAddress()`
- **MintingIntent**: Maps to `Tx.mintAssets()`
- **ScriptInteractionIntent**: Maps to ScriptTx methods
- **MetadataIntent**: Transaction metadata specification

## Usage

### Creating Transaction Intents Programmatically

```java
// Create payment intent
PaymentIntent payment = PaymentIntent.builder()
    .to("${receiver}")
    .amounts(List.of(
        PaymentIntent.AmountIntent.builder()
            .value("${amount}")
            .unit("ADA")
            .build()
    ))
    .build();

// Create transaction intent
TransactionIntent intent = TransactionIntent.builder()
    .id("payment_tx")
    .type("transaction")
    .intents(TransactionIntents.builder()
        .payments(List.of(payment))
        .inputs(InputIntent.builder()
            .fromAddress("${sender}")
            .strategy("auto")
            .build())
        .build())
    .build();

// Create collection with variables
TransactionIntentCollection collection = TransactionIntentCollection.builder()
    .id("example")
    .variables(Map.of(
        "sender", "addr1...",
        "receiver", "addr2...",
        "amount", 10
    ))
    .transactions(List.of(intent))
    .build();
```

### YAML Serialization

```java
YamlIntentSerializer serializer = new YamlIntentSerializer();

// Serialize to YAML
String yaml = serializer.serialize(collection);

// Deserialize from YAML
TransactionIntentCollection deserialized = serializer.deserialize(yaml);

// File operations
serializer.writeToFile(collection, "transaction.yaml");
TransactionIntentCollection fromFile = serializer.readFromFile("transaction.yaml");
```

### Script Transaction Example

```yaml
transactions:
  - id: "script_interaction"
    type: "script_transaction"
    intents:
      script_interactions:
        - action: "collect_from"
          contract: "${contract_addr}"
          selection:
            type: "predicate"
            condition: "utxo.amount >= 2000000"
          redeemer:
            action: "withdraw"
            beneficiary: "${beneficiary}"
          datum:
            inline: true
            value:
              constructor: 0
              fields:
                - bytes: "${beneficiary}"
                - int: 1735689600
      
      payments:
        - to: "${beneficiary}"
          amounts:
            - value: 45
              unit: "ADA"
    
    context:
      fee_payer: "${user}"
      collateral_payer: "${user}"
      ignore_script_cost_evaluation_error: true
```

### Transaction Chaining

```yaml
transactions:
  - id: "setup"
    type: "transaction"
    intents:
      payments:
        - to: "${contract}"
          amounts:
            - value: 100
              unit: "ADA"
  
  - id: "execute" 
    type: "script_transaction"
    depends_on: ["setup"]
    intents:
      script_interactions:
        - action: "collect_from"
          contract: "${contract}"
          selection:
            type: "from_step"
            from_step: "setup"
            output_index: 0
```

## Testing

The module includes comprehensive unit and integration tests:

```bash
# Run all tests
./gradlew :tx-dsl:test

# Run specific test class
./gradlew :tx-dsl:test --tests TransactionIntentCollectionTest

# Run end-to-end examples 
./gradlew :tx-dsl:test --tests EndToEndExampleTest
```

## Future Implementation

This module provides the foundation for:

1. **Variable Resolution Engine**: `${variable}` template processing
2. **Intent → Tx Conversion**: Transform intents to QuickTx objects
3. **Context Application**: Apply TxExecutionContext to TxBuilder
4. **Watcher Integration**: Rollback and rebuild support
5. **Groovy DSL**: Alternative programmatic syntax

## API Compatibility

The DSL is designed to maintain 100% compatibility with existing APIs:

- **Tx.from()** → InputIntent.fromAddress (single address)
- **AbstractTx.payToAddress()** → PaymentIntent
- **AbstractTx.withChangeAddress()** → ChangeIntent.address (single address)
- **ScriptTx methods** → ScriptInteractionIntent
- **TxBuilderContext** → TxExecutionContext (complete mapping)

This ensures that any existing transaction can be expressed as an intent and vice versa.
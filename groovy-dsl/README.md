# Groovy DSL for Cardano Transaction Building

A natural language DSL for building Cardano transactions using Groovy's dynamic features. This module provides a more readable and intuitive syntax on top of the existing TxDsl infrastructure.

## Features

- **Natural Language Syntax**: Write transactions that read like English
- **Closure-based Configuration**: Use Groovy closures for nested configuration
- **Amount Extensions**: Natural syntax like `5.ada` or `100.asset(unit)`
- **Variable Templates**: Support for template variables with `${VAR}` syntax
- **Full TxDsl Integration**: Seamlessly integrates with existing TxDsl and QuickTxBuilder

## Quick Start

### Basic Transaction

```groovy
import static com.bloxbean.cardano.client.dsl.groovy.TxGroovyBuilder.transaction

def tx = transaction {
    from "addr1_sender..."
    send 5.ada to "addr1_receiver..."
}

// Use with QuickTxBuilder
quickTxBuilder.compose(tx.unwrap())
    .withSigner(signer)
    .complete()
```

### Multiple Payments

```groovy
def tx = transaction {
    from treasury
    send 5.ada to alice
    send 3.ada to bob
    send 100.asset(policyId, "MyToken") to charlie
}
```

### Using Closures for Configuration

```groovy
def tx = transaction {
    from "addr1_sender..."
    
    pay {
        to "addr1_receiver..."
        amount 10.ada
    }
}
```

## Amount Extensions

The DSL provides natural amount syntax through Groovy extensions:

```groovy
// ADA amounts
5.ada           // Returns Amount.ada(5)
5.5.ada         // Returns Amount.ada(5.5)

// Lovelace amounts
5000000.lovelace  // Returns Amount.lovelace(5000000)

// Token amounts
100.asset(unit)                    // With full unit string
100.asset(policyId, "TokenName")   // With policy and name
```

## Variable Templates

Create reusable transaction templates with variables:

```groovy
def template = transaction {
    from '${TREASURY}'
    send '${AMOUNT}'.ada to '${RECIPIENT}'
    
    variables {
        TREASURY = "addr1_treasury..."
        RECIPIENT = "addr1_alice..."
        AMOUNT = 5
    }
}

// Or add variables later
template.withVariable("EMPLOYEE", "addr1_bob...")
```

## Address Aliases

Define address aliases for cleaner code:

```groovy
def tx = transaction {
    variables {
        treasury = "addr1_treasury_real_address..."
        alice = "addr1_alice_real_address..."
    }
    
    from treasury     // Uses the alias
    send 5.ada to alice
}
```

## YAML Serialization

The Groovy DSL fully supports YAML serialization through the underlying TxDsl:

```groovy
// Build and serialize
def tx = transaction {
    from "addr1_sender..."
    send 5.ada to "addr1_receiver..."
}
String yaml = tx.toYaml()

// Load from YAML
def loaded = TxGroovyBuilder.fromYaml(yaml)
```

## Integration with QuickTxBuilder

The Groovy DSL is fully compatible with QuickTxBuilder:

```groovy
def tx = transaction {
    from senderAddr
    send 10.ada to receiverAddr
}

// Get the underlying Tx object
Tx rawTx = tx.unwrap()

// Use with QuickTxBuilder
Result<String> result = quickTxBuilder
    .compose(rawTx)
    .withSigner(signer)
    .complete()
```

## Complex Example

```groovy
import static com.bloxbean.cardano.client.dsl.groovy.TxGroovyBuilder.transaction

// Define a payroll transaction
def payrollTx = transaction {
    // Define variables
    variables {
        treasury = "addr1_company_treasury..."
        payrollDate = "2025-08-01"
    }
    
    // Set sender
    from treasury
    
    // Pay employees
    send 5000.ada to "addr1_alice..."
    send 4500.ada to "addr1_bob..."
    send 6000.ada to "addr1_charlie..."
    
    // Pay contractor with tokens
    send 100.asset(contractTokenPolicy, "ServiceToken") to "addr1_contractor..."
}

// Serialize for audit
String payrollYaml = payrollTx.toYaml()
saveToDatabase(payrollYaml)

// Execute transaction
quickTxBuilder.compose(payrollTx.unwrap())
    .withSigner(treasurySigner)
    .complete()
```

## API Reference

### TxGroovyBuilder

- `transaction(Closure)` - Create a new transaction with DSL
- `fromYaml(String)` - Load transaction from YAML
- `template(Closure)` - Create a transaction template

### TxGroovyDsl Methods

- `from(address)` - Set sender address
- `pay(Closure)` - Configure payment with closure
- `payTo(address, amount)` - Direct payment
- `send(amount).to(address)` - Natural language payment
- `variables(Closure)` - Define variables
- `withVariable(name, value)` - Add single variable
- `unwrap()` - Get underlying Tx object
- `toYaml()` - Serialize to YAML
- `build()` - Get underlying TxDsl

### Amount Extensions

- `Number.ada` - Create ADA amount
- `Number.lovelace` - Create lovelace amount
- `Number.asset(unit)` - Create token amount
- `Number.asset(policy, name)` - Create token with policy and name

## Requirements

- Groovy 4.0.15 or higher
- Cardano Client Library tx-dsl module
- Cardano Client Library quicktx module

## Testing

The module includes comprehensive Spock tests demonstrating all features:

```bash
./gradlew :groovy-dsl:test
```

## Future Enhancements

- Support for ScriptTx operations
- Minting and burning operations
- Staking operations (delegation, withdrawal)
- Governance operations (DRep, voting)
- Advanced DSL features (conditionals, loops)
- IDE support with code completion
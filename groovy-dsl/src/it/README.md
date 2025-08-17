# Groovy DSL Integration Tests

This directory contains integration tests for the Groovy DSL that test against real blockchain backends.

## Prerequisites

### Yaci DevKit (Default)
The integration tests use Yaci DevKit by default, which provides a local Cardano blockchain for testing.

**Setup Yaci DevKit:**
1. Download and install Yaci DevKit
2. Start the local cluster:
   ```bash
   # Default ports:
   # - Blockchain API: http://localhost:8080/api/v1/
   # - Admin API: http://localhost:10000/
   ```

### Alternative Backends

#### Blockfrost
To use Blockfrost backend:
```bash
./gradlew :groovy-dsl:integrationTest -Dbackend.type=blockfrost -DBF_PROJECT_ID=your_project_id
```

#### Koios
To use Koios backend:
```bash
./gradlew :groovy-dsl:integrationTest -Dbackend.type=koios
```

## Running Integration Tests

### Default (Yaci DevKit)
```bash
# Ensure Yaci DevKit is running on localhost:8080
./gradlew :groovy-dsl:integrationTest
```

### Specific Test Class
```bash
./gradlew :groovy-dsl:integrationTest --tests TxGroovyDslIT
```

### Specific Test Method
```bash
./gradlew :groovy-dsl:integrationTest --tests "TxGroovyDslIT.testSimplePaymentWithGroovyDsl"
```

### With Different Backend
```bash
./gradlew :groovy-dsl:integrationTest -Dbackend.type=blockfrost -DBF_PROJECT_ID=your_project_id
```

## Test Structure

### Base Test Class
- `TxGroovyDslBaseIT.groovy` - Provides common setup and utilities
  - Account management with pre-funded test accounts
  - Backend service configuration
  - Transaction waiting and confirmation utilities
  - Yaci DevKit admin API integration

### Test Classes
- `TxGroovyDslIT.groovy` - Main integration tests
  - Simple payment transactions
  - Multiple payments
  - Closure syntax testing
  - Amount extensions
  - Variable templates
  - YAML serialization round-trip
  - Complex business scenarios
  - Transaction chaining
  - Error handling

## Test Accounts

The tests use pre-defined mnemonics for consistent test accounts:

- **Sender1**: `drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code`
- **Sender2**: `actress cushion eternal jacket ocean lava stomach armed champion joke arrow erupt fiction distance cream artist swim steak fortune grit blast hood buzz spot`
- **Receivers**: Randomly generated for each test run

When using Yaci DevKit, sender accounts are automatically funded with 50,000 ADA each.

## Test Scenarios

### 1. Simple Payment
Tests basic Groovy DSL syntax for single payments:
```groovy
def tx = transaction {
    from sender1Addr
    send 2.ada to receiver1Addr
}
```

### 2. Multiple Payments
Tests multiple payments in one transaction:
```groovy
def tx = transaction {
    from sender1Addr
    send 1.ada to receiver1Addr
    send 1.5.ada to receiver2Addr
}
```

### 3. Closure Syntax
Tests the pay closure syntax:
```groovy
def tx = transaction {
    from sender2Addr
    pay {
        to receiver1Addr
        amount Amount.ada(3)
    }
}
```

### 4. Amount Extensions
Tests Groovy amount extensions:
```groovy
def tx = transaction {
    from sender1Addr
    send 5000000.lovelace to receiver1Addr
}
```

### 5. Variable Templates
Tests template variables:
```groovy
def template = transaction {
    from '${TREASURY}'
    send '${AMOUNT}'.ada to '${EMPLOYEE}'
    variables {
        TREASURY = sender2Addr
        EMPLOYEE = receiver2Addr
        AMOUNT = 4
    }
}
```

### 6. YAML Serialization
Tests YAML round-trip:
```groovy
def originalTx = transaction { /* ... */ }
String yaml = originalTx.toYaml()
def loadedTx = TxGroovyBuilder.fromYaml(yaml)
```

### 7. Complex Business Logic
Tests realistic business scenarios like payroll:
```groovy
def payrollTx = transaction {
    from sender1Addr
    employees.each { name, address ->
        def salary = (name == "alice") ? 5 : 4
        send salary.ada to address
    }
    send 1.ada to sender2Addr  // Operations
}
```

### 8. Transaction Chaining
Tests dependent transactions:
```groovy
// First transaction funds an account
def firstTx = transaction { /* ... */ }
// Second transaction uses those funds
def secondTx = transaction { /* ... */ }
```

### 9. Error Handling
Tests graceful failure handling:
```groovy
def hugeAmountTx = transaction {
    from receiver1Addr  // Limited funds
    send 999999.ada to receiver2Addr  // Huge amount
}
// Should fail gracefully
```

## Logging

Test execution logs are written to:
- `groovy-dsl-integration.log` - General test logs
- `groovy-dsl-error.log` - Error and warning logs
- Console output with timestamps and log levels

## Backend Service Configuration

### Yaci DevKit
- Base URL: `http://localhost:8080/api/v1/`
- Admin URL: `http://localhost:10000/`
- Uses Blockfrost-compatible API

### Blockfrost
- Requires `BF_PROJECT_ID` environment variable
- Default URL: `https://cardano-preprod.blockfrost.io/api/v0/`
- Custom URL via `BF_URL` system property

### Koios
- Default URL: `https://preprod.koios.rest/api/v1/`
- Custom URL via `KOIOS_URL` system property

### Ogmios/Kupo
- Ogmios URL: `http://localhost:1337` (via `OGMIOS_URL`)
- Kupo URL: `http://localhost:1442` (via `KUPO_URL`)

## Test Data Cleanup

Tests use Yaci DevKit's reset functionality when needed:
```groovy
resetDevNet()  // Resets the local blockchain state
```

## Troubleshooting

### Connection Issues
- Ensure Yaci DevKit is running on the correct ports
- Check firewall settings
- Verify backend URLs are accessible

### Test Failures
- Check account funding (for DevKit)
- Verify transaction confirmation timeouts
- Review logs for detailed error information

### Performance
- Tests may take longer on slower networks
- DevKit provides fastest test execution
- Adjust timeout values if needed for slower backends

This integration test suite ensures the Groovy DSL works correctly with real blockchain interactions and provides confidence in the production deployment.
#!/usr/bin/env groovy

/**
 * Example Groovy script demonstrating the Transaction DSL
 * 
 * This can be run as a standalone Groovy script or integrated
 * into a larger application.
 */

@Grab('com.bloxbean.cardano:groovy-dsl:0.6.0')
@Grab('com.bloxbean.cardano:quicktx:0.6.0')

import static com.bloxbean.cardano.client.dsl.groovy.TxGroovyBuilder.*
import com.bloxbean.cardano.client.api.model.Amount
import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService
import com.bloxbean.cardano.client.common.model.Networks
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder
import com.bloxbean.cardano.client.account.Account

// ============================================
// Example 1: Simple Payment Transaction
// ============================================
println "Example 1: Simple Payment"
println "-" * 40

def simpleTx = transaction {
    from "addr_test1qz7mw8u8dxkw..."
    send 5.ada to "addr_test1qp8cpk..."
}

println "Transaction created with natural syntax"
println "YAML output:"
println simpleTx.toYaml()

// ============================================
// Example 2: Multiple Payments with Variables
// ============================================
println "\nExample 2: Multiple Payments with Variables"
println "-" * 40

def payrollTx = transaction {
    // Define reusable addresses
    variables {
        treasury = "addr_test1_treasury..."
        alice = "addr_test1_alice..."
        bob = "addr_test1_bob..."
        charlie = "addr_test1_charlie..."
    }
    
    // Use the variables
    from treasury
    
    // Multiple payments
    send 100.ada to alice
    send 150.ada to bob
    send 200.ada to charlie
}

println "Payroll transaction created"
println "Can be saved as template and reused"

// ============================================
// Example 3: Token Transactions
// ============================================
println "\nExample 3: Token Transactions"
println "-" * 40

def tokenTx = transaction {
    from "addr_test1_sender..."
    
    // Send ADA
    send 5.ada to "addr_test1_receiver1..."
    
    // Send tokens with policy and name
    def hosky_policy = "1234567890abcdef1234567890abcdef1234567890abcdef12345678"
    send 1000.asset(hosky_policy, "HOSKY") to "addr_test1_receiver2..."
    
    // Send tokens with full unit
    def min_unit = "29d222ce763455e3d7a09a665ce554f00ac89d2e99a1a83d267170c64d494e"
    send 500.asset(min_unit) to "addr_test1_receiver3..."
}

println "Multi-asset transaction created"

// ============================================
// Example 4: Template with Placeholders
// ============================================
println "\nExample 4: Transaction Template"
println "-" * 40

def template = transaction {
    from '${TREASURY}'
    send '${MONTHLY_SALARY}'.ada to '${EMPLOYEE}'
    send '${BONUS}'.ada to '${EMPLOYEE}'
}

// Later, fill in the variables
def concreteTx = template
    .withVariable("TREASURY", "addr_test1_company...")
    .withVariable("EMPLOYEE", "addr_test1_john...")
    .withVariable("MONTHLY_SALARY", 3000)
    .withVariable("BONUS", 500)

println "Template instantiated with concrete values"
println concreteTx.toYaml()

// ============================================
// Example 5: Integration with QuickTxBuilder
// ============================================
println "\nExample 5: QuickTxBuilder Integration"
println "-" * 40

// Note: This example shows the integration pattern
// but requires actual backend service and account setup
def demonstrateIntegration = {
    // Setup (would need real values)
    def backendService = null // new BFBackendService(Constants.BLOCKFROST_TESTNET_URL, projectId)
    def account = null // Account.fromMnemonic(Networks.testnet(), mnemonic)
    
    if (backendService && account) {
        def tx = transaction {
            from account.baseAddress()
            send 2.ada to "addr_test1_recipient..."
        }
        
        def quickTxBuilder = new QuickTxBuilder(backendService)
        def result = quickTxBuilder
            .compose(tx.unwrap())  // Get the underlying Tx object
            .withSigner(account.getSignerProvider())
            .complete()
        
        if (result.isSuccessful()) {
            println "Transaction submitted: ${result.getValue()}"
        } else {
            println "Transaction failed: ${result.getResponse()}"
        }
    } else {
        println "Integration example - requires backend service configuration"
    }
}

// ============================================
// Example 6: Complex Business Logic
// ============================================
println "\nExample 6: Complex Business Logic"
println "-" * 40

def createMonthlyDistribution = { employees, bonuses ->
    transaction {
        from "addr_test1_treasury..."
        
        // Pay each employee
        employees.each { name, address ->
            def salary = lookupSalary(name)  // Business logic
            send salary.ada to address
        }
        
        // Pay bonuses
        bonuses.each { address, amount ->
            send amount.ada to address
        }
        
        // Pay operational costs
        send 100.ada to "addr_test1_operations..."
    }
}

// Helper function (mock)
def lookupSalary = { name ->
    // Mock salary lookup
    return name == "alice" ? 5000 : 4000
}

def employees = [
    alice: "addr_test1_alice...",
    bob: "addr_test1_bob..."
]

def bonuses = [
    "addr_test1_alice...": 500,
    "addr_test1_charlie...": 300
]

def monthlyTx = createMonthlyDistribution(employees, bonuses)
println "Complex monthly distribution transaction created"

// ============================================
// Example 7: YAML Round-trip
// ============================================
println "\nExample 7: YAML Serialization Round-trip"
println "-" * 40

def originalTx = transaction {
    from "addr_test1_sender..."
    send 10.ada to "addr_test1_receiver..."
}

// Serialize to YAML
def yaml = originalTx.toYaml()
println "Serialized to YAML:"
println yaml

// Load back from YAML
def loadedTx = fromYaml(yaml)
println "\nLoaded from YAML successfully"
println "Can be used with QuickTxBuilder: ${loadedTx.unwrap() != null}"

// ============================================
println "\n" + "=" * 50
println "Groovy DSL Examples Complete"
println "=" * 50
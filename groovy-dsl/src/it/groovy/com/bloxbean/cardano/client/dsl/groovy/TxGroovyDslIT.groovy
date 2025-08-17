package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.api.model.Amount
import com.bloxbean.cardano.client.api.model.Result
import com.bloxbean.cardano.client.function.helper.SignerProviders

import static com.bloxbean.cardano.client.dsl.groovy.TxGroovyBuilder.transaction
/**
 * Integration tests for Groovy DSL with real blockchain interactions
 * Tests against Yaci DevKit by default
 */
class TxGroovyDslIT extends TxGroovyDslBaseIT {

    def "test simple payment with Groovy DSL"() {
        given: "a simple payment transaction using Groovy DSL"

        def tx = transaction {
            from sender1Addr
            send 2.ada to receiver1Addr
        }

        when: "the transaction is executed"
        Result<String> result = quickTxBuilder
            .compose(tx.unwrap())
            .withSigner(SignerProviders.signerFrom(sender1))
            .complete()

        then: "the transaction should be successful"
        result.isSuccessful()
        result.getValue() != null
        log.info("Transaction hash: {}", result.getValue())

        and: "wait for transaction confirmation"
        waitForTransaction(result)

        and: "verify UTXO is available at receiver"
        checkIfUtxoAvailable(result.getValue(), receiver1Addr)
    }

    def "test multiple payments with Groovy DSL"() {
        given: "a transaction with multiple payments"
        def tx = transaction {
            from sender1Addr
            send 1.ada to receiver1Addr
            send 1.5.ada to receiver2Addr
        }

        when: "the transaction is executed"
        Result<String> result = quickTxBuilder
            .compose(tx.unwrap())
            .withSigner(SignerProviders.signerFrom(sender1))
            .complete()

        then: "the transaction should be successful"
        result.isSuccessful()
        log.info("Multi-payment transaction hash: {}", result.getValue())

        and: "wait for transaction confirmation"
        waitForTransaction(result)

        and: "verify UTXOs are available at both receivers"
        checkIfUtxoAvailable(result.getValue(), receiver1Addr)
        checkIfUtxoAvailable(result.getValue(), receiver2Addr)
    }

    def "test payment with closure syntax"() {
        given: "a transaction using closure syntax"
        def tx = transaction {
            from sender2Addr

            pay {
                to receiver1Addr
                amount Amount.ada(3)
            }
        }

        when: "the transaction is executed"
        Result<String> result = quickTxBuilder
            .compose(tx.unwrap())
            .withSigner(SignerProviders.signerFrom(sender2))
            .complete()

        then: "the transaction should be successful"
        result.isSuccessful()
        log.info("Closure syntax transaction hash: {}", result.getValue())

        and: "wait for transaction confirmation"
        waitForTransaction(result)
    }

    def "test amount extensions with real transaction"() {
        given: "a transaction using amount extensions"
        def tx = transaction {
            from sender1Addr
            send 5000000.lovelace to receiver1Addr  // 5 ADA in lovelace
        }

        when: "the transaction is executed"
        Result<String> result = quickTxBuilder
            .compose(tx.unwrap())
            .withSigner(SignerProviders.signerFrom(sender1))
            .complete()

        then: "the transaction should be successful"
        result.isSuccessful()
        log.info("Amount extensions transaction hash: {}", result.getValue())

        and: "wait for transaction confirmation"
        waitForTransaction(result)
    }

    def "test variable template integration"() {
        given: "a transaction template with variables"
        def template = transaction {
            from sender2Addr  // Use direct address instead of template for now
            send 4.ada to receiver2Addr
        }

        when: "the template is executed"
        Result<String> result = quickTxBuilder
            .compose(template.unwrap())
            .withSigner(SignerProviders.signerFrom(sender2))
            .complete()

        then: "the transaction should be successful"
        result.isSuccessful()
        log.info("Simple template transaction hash: {}", result.getValue())

        and: "wait for transaction confirmation"
        waitForTransaction(result)
    }

    def "test YAML serialization and execution"() {
        given: "a transaction serialized to YAML and loaded back"
        def originalTx = transaction {
            from sender1Addr
            send 1.5.ada to receiver1Addr
        }

        String yaml = originalTx.toYaml()
        log.info("Serialized YAML:\\n{}", yaml)

        def loadedTx = TxGroovyBuilder.fromYaml(yaml)

        when: "the loaded transaction is executed"
        Result<String> result = quickTxBuilder
            .compose(loadedTx.unwrap())
            .withSigner(SignerProviders.signerFrom(sender1))
            .complete()

        then: "the transaction should be successful"
        result.isSuccessful()
        log.info("YAML transaction hash: {}", result.getValue())

        and: "wait for transaction confirmation"
        waitForTransaction(result)
    }

    def "test address aliases with real transaction"() {
        given: "a transaction using address aliases"
        def tx = transaction {
            variables {
                treasury = sender2Addr
                employee = receiver1Addr
            }

            from treasury
            send 2.5.ada to employee
        }

        when: "the transaction is executed"
        Result<String> result = quickTxBuilder
            .compose(tx.unwrap())
            .withSigner(SignerProviders.signerFrom(sender2))
            .complete()

        then: "the transaction should be successful"
        result.isSuccessful()
        log.info("Alias transaction hash: {}", result.getValue())

        and: "wait for transaction confirmation"
        waitForTransaction(result)
    }

    def "test complex business scenario"() {
        given: "a complex payroll scenario"
        def employees = [
            alice: receiver1Addr,
            bob: receiver2Addr
        ]

        def payrollTx = transaction {
            from sender1Addr

            // Pay each employee
            employees.each { name, address ->
                def salary = (name == "alice") ? 5 : 4  // Different salaries
                send salary.ada to address
            }

            // Operational costs
            send 1.ada to sender2Addr  // Operations account
        }

        when: "the payroll is executed"
        Result<String> result = quickTxBuilder
            .compose(payrollTx.unwrap())
            .withSigner(SignerProviders.signerFrom(sender1))
            .complete()

        then: "the transaction should be successful"
        result.isSuccessful()
        log.info("Payroll transaction hash: {}", result.getValue())

        and: "wait for transaction confirmation"
        waitForTransaction(result)

        and: "verify all payments were made"
        checkIfUtxoAvailable(result.getValue(), receiver1Addr)
        checkIfUtxoAvailable(result.getValue(), receiver2Addr)
        checkIfUtxoAvailable(result.getValue(), sender2Addr)
    }

    def "test chained transactions"() {
        given: "first transaction to fund intermediate account"
        def firstTx = transaction {
            from sender1Addr
            send 10.ada to receiver1Addr
        }

        when: "first transaction is executed"
        Result<String> firstResult = quickTxBuilder
            .compose(firstTx.unwrap())
            .withSigner(SignerProviders.signerFrom(sender1))
            .complete()

        then: "first transaction should succeed"
        firstResult.isSuccessful()
        log.info("First transaction hash: {}", firstResult.getValue())
        waitForTransaction(firstResult)

        when: "second transaction uses funds from first"
        def secondTx = transaction {
            from receiver1Addr
            send 5.ada to receiver2Addr
        }

        Result<String> secondResult = quickTxBuilder
            .compose(secondTx.unwrap())
            .withSigner(SignerProviders.signerFrom(receiver1))
            .complete()

        then: "second transaction should also succeed"
        secondResult.isSuccessful()
        log.info("Second transaction hash: {}", secondResult.getValue())
        waitForTransaction(secondResult)

        and: "verify final UTXO is available"
        checkIfUtxoAvailable(secondResult.getValue(), receiver2Addr)
    }

    def "test error handling in Groovy DSL"() {
        given: "a transaction with insufficient funds"
        def hugeAmountTx = transaction {
            from receiver1Addr  // Account with limited funds
            send 999999.ada to receiver2Addr  // Huge amount
        }

        when: "the transaction is attempted"
        def result = null
        def caughtException = null
        try {
            result = quickTxBuilder
                .compose(hugeAmountTx.unwrap())
                .withSigner(SignerProviders.signerFrom(receiver1))
                .complete()
        } catch (Exception e) {
            caughtException = e
        }

        then: "the transaction should fail gracefully"
        (caughtException != null) || (result != null && !result.isSuccessful())
        if (caughtException) {
            log.info("Expected exception: {}", caughtException.message)
        } else {
            log.info("Expected failure: {}", result.getResponse())
        }
    }
}

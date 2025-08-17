package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.dsl.TxDsl
import com.bloxbean.cardano.client.dsl.TxDslBuilder
/**
 * Main entry point for Groovy DSL
 * Provides the transaction() method that accepts a closure
 * Enhanced with V3 context support via compose() methods
 */
class TxGroovyBuilder {

    /**
     * Create a transaction using Groovy DSL
     * Example:
     *   def tx = transaction {
     *     from "addr1..."
     *     send 5.ada to "addr2..."
     *   }
     */
    static TxGroovyDsl transaction(Closure closure) {
        def dsl = new TxGroovyDsl()
        closure.delegate = dsl
        closure.resolveStrategy = Closure.OWNER_FIRST  // Check owner (test class) first for variables
        closure()
        return dsl
    }

    /**
     * Load a transaction from YAML and enhance with Groovy DSL
     */
    static TxGroovyDsl fromYaml(String yaml) {
        TxDsl txDsl = TxDsl.fromYaml(yaml)
        TxGroovyDsl groovyDsl = new TxGroovyDsl(txDsl: txDsl)
        return groovyDsl
    }

    /**
     * Create a transaction template with variables
     * Example:
     *   def template = template {
     *     from '${TREASURY}'
     *     send '${AMOUNT}'.ada to '${RECIPIENT}'
     *   }
     */
    static TxGroovyDsl template(Closure closure) {
        return transaction(closure)
    }

    // ========== V3 Context Support Methods ==========

    /**
     * Compose transactions with context support (V3 design)
     * Example:
     *   def result = compose(backendService) {
     *     tx {
     *       from treasury
     *       send 5.ada to alice
     *     }
     *
     *     context {
     *       feePayer treasury
     *       signer treasuryAccount
     *     }
     *   }.complete()
     */
    static GroovyTxContext compose(BackendService backendService, Closure closure) {
        if (!backendService) {
            throw new IllegalArgumentException("BackendService is required for transaction composition")
        }

        def txDslBuilder = new TxDslBuilder(backendService)
        def compositionBuilder = new CompositionBuilder(txDslBuilder)

        closure.delegate = compositionBuilder
        closure.resolveStrategy = Closure.DELEGATE_FIRST  // Delegate to CompositionBuilder first
        closure()

        return compositionBuilder.build()
    }

    /**
     * Compose multiple pre-built transactions with shared context
     * Example:
     *   def payment1 = transaction { from treasury; send 100.ada to alice }
     *   def payment2 = transaction { from treasury; send 150.ada to bob }
     *
     *   def result = compose(backendService, payment1, payment2)
     *     .feePayer(treasury)
     *     .signer("treasurySigner")
     *     .complete()
     */
    static GroovyTxContext compose(BackendService backendService, TxGroovyDsl... transactions) {
        if (!backendService) {
            throw new IllegalArgumentException("BackendService is required for transaction composition")
        }

        if (!transactions || transactions.length == 0) {
            throw new IllegalArgumentException("At least one transaction is required")
        }

        def txDslBuilder = new TxDslBuilder(backendService)
        def txDsls = transactions.collect { it.build() }
        def txContext = txDslBuilder.compose(txDsls as TxDsl[])

        return new GroovyTxContext(txContext)
    }

    /**
     * Compose multiple pre-built TxDsl objects (for mixed Java/Groovy usage)
     * Example:
     *   def javaTxDsl = new TxDsl().from("addr1...").payToAddress("addr2...", Amount.ada(5))
     *   def groovyTx = transaction { from treasury; send 3.ada to bob }
     *
     *   def result = compose(backendService, javaTxDsl, groovyTx.build())
     *     .feePayer("addr1_treasury...")
     *     .complete()
     */
    static GroovyTxContext compose(BackendService backendService, TxDsl... txDsls) {
        if (!backendService) {
            throw new IllegalArgumentException("BackendService is required for transaction composition")
        }

        if (!txDsls || txDsls.length == 0) {
            throw new IllegalArgumentException("At least one TxDsl is required")
        }

        def txDslBuilder = new TxDslBuilder(backendService)
        def txContext = txDslBuilder.compose(txDsls)

        return new GroovyTxContext(txContext)
    }
}

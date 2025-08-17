package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.dsl.TxDsl
import com.bloxbean.cardano.client.dsl.TxDslBuilder

/**
 * Groovy DSL builder for transaction composition with context support.
 * Follows V3 wrapper pattern - delegates to proven TxDslBuilder implementation.
 */
class CompositionBuilder {
    private TxDslBuilder txDslBuilder
    private List<TxGroovyDsl> transactions = []
    
    CompositionBuilder(TxDslBuilder txDslBuilder) {
        this.txDslBuilder = txDslBuilder
    }
    
    /**
     * Define a transaction with natural Groovy syntax
     * Example:
     *   tx {
     *     from treasury
     *     send 5.ada to alice
     *   }
     */
    def tx(Closure closure) {
        def groovyDsl = new TxGroovyDsl()
        closure.delegate = groovyDsl
        closure.resolveStrategy = Closure.OWNER_FIRST  // Check owner for variables first
        closure()
        transactions.add(groovyDsl)
        return this
    }
    
    /**
     * Define execution context with natural Groovy syntax
     * Example:
     *   context {
     *     feePayer treasury
     *     signer treasuryAccount
     *   }
     */
    def context(Closure closure) {
        def contextBuilder = new ContextBuilder(txDslBuilder)
        closure.delegate = contextBuilder
        closure.resolveStrategy = Closure.DELEGATE_FIRST  // Delegate to ContextBuilder first
        closure()
        return this
    }
    
    /**
     * Build the final execution context
     * Converts TxGroovyDsl objects to TxDsl and composes with context
     */
    GroovyTxContext build() {
        if (transactions.isEmpty()) {
            throw new IllegalStateException("No transactions defined. Use tx { } blocks to define transactions.")
        }
        
        // Convert TxGroovyDsl to TxDsl objects
        def txDsls = transactions.collect { it.build() }
        
        // Delegate to V3 TxDslBuilder implementation
        def txContext = txDslBuilder.compose(txDsls as TxDsl[])
        
        return new GroovyTxContext(txContext)
    }
    
    /**
     * Handle property-style access to support alternative syntax
     */
    def propertyMissing(String name) {
        // Allow access to variables from the owner (test class, script, etc.)
        throw new MissingPropertyException("Property '$name' not found. Use tx { } and context { } blocks for transaction composition.")
    }
}
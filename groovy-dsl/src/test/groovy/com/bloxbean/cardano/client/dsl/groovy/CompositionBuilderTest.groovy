package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.dsl.TxDslBuilder
import com.bloxbean.cardano.client.dsl.context.TxHandlerRegistry
import spock.lang.Specification

import static com.bloxbean.cardano.client.dsl.groovy.TxGroovyBuilder.*

/**
 * Test CompositionBuilder functionality for Groovy DSL context support
 */
class CompositionBuilderTest extends Specification {

    BackendService mockBackendService
    TxDslBuilder txDslBuilder
    CompositionBuilder compositionBuilder
    
    // Test variables
    def treasury = "addr1_treasury..."
    def alice = "addr1_alice..."
    def bob = "addr1_bob..."

    def setup() {
        mockBackendService = Mock(BackendService)
        txDslBuilder = new TxDslBuilder(mockBackendService)
        compositionBuilder = new CompositionBuilder(txDslBuilder)
        TxHandlerRegistry.clear() // Clean registry for each test
    }

    def "should create CompositionBuilder with TxDslBuilder"() {
        when:
        def builder = new CompositionBuilder(txDslBuilder)
        
        then:
        builder != null
    }

    def "should define single transaction with tx block"() {
        when:
        def result = compositionBuilder.tx {
            from treasury
            send 5.ada to alice
        }
        
        then:
        result == compositionBuilder // Should return self for chaining
    }

    def "should define multiple transactions with tx blocks"() {
        when:
        compositionBuilder
            .tx {
                from treasury
                send 100.ada to alice
            }
            .tx {
                from treasury
                send 150.ada to bob
            }
        
        then:
        noExceptionThrown()
    }

    def "should define context with context block"() {
        when:
        compositionBuilder
            .tx {
                from treasury
                send 5.ada to alice
            }
            .context {
                feePayer treasury
                signer "treasurySigner"
            }
        
        then:
        noExceptionThrown()
    }

    def "should build GroovyTxContext when transactions are defined"() {
        when:
        def groovyTxContext = compositionBuilder
            .tx {
                from treasury
                send 5.ada to alice
            }
            .build()
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should throw exception when building without transactions"() {
        when:
        compositionBuilder.build()
        
        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("No transactions defined")
    }

    def "should support variables in transactions and context"() {
        when:
        def groovyTxContext = compositionBuilder
            .tx {
                variables {
                    treasury = "addr1_treasury_resolved..."
                    alice = "addr1_alice_resolved..."
                }
                from treasury
                send 5.ada to alice
            }
            .context {
                feePayer treasury
                signer "treasurySigner"
            }
            .build()
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should handle property missing gracefully"() {
        when:
        compositionBuilder.unknownProperty
        
        then:
        def ex = thrown(MissingPropertyException)
        ex.message.contains("Property 'unknownProperty' not found")
    }

    def "should support complex transaction composition"() {
        when:
        def groovyTxContext = compositionBuilder
            .tx {
                from treasury
                send 100.ada to alice
                send 50.ada to bob
            }
            .tx {
                from alice
                send 25.ada to bob
            }
            .context {
                feePayer treasury
                collateralPayer treasury
                utxoSelection "LARGEST_FIRST"
                signer "treasurySigner"
            }
            .build()
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should support property-style context assignment"() {
        when:
        def groovyTxContext = compositionBuilder
            .tx {
                from treasury
                send 5.ada to alice
            }
            .context {
                feePayer = treasury
                collateralPayer = treasury
                utxoSelection = "LARGEST_FIRST"
                signer = "treasurySigner"
            }
            .build()
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should work with closure resolution strategies"() {
        // This test verifies that variables from the test class are accessible
        when:
        def groovyTxContext = compositionBuilder
            .tx {
                from treasury  // Should resolve to test class variable
                send 5.ada to alice  // Should resolve to test class variable
            }
            .context {
                feePayer treasury  // Should resolve to test class variable
            }
            .build()
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }
}
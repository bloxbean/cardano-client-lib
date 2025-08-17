package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.dsl.TxDsl
import com.bloxbean.cardano.client.dsl.context.TxHandlerRegistry
import com.bloxbean.cardano.client.function.TxSigner
import spock.lang.Specification

import static com.bloxbean.cardano.client.dsl.groovy.TxGroovyBuilder.*

/**
 * End-to-end integration tests for Groovy DSL context support
 */
class GroovyContextIntegrationTest extends Specification {

    BackendService mockBackendService
    
    // Test variables
    def treasury = "addr1_treasury..."
    def alice = "addr1_alice..."
    def bob = "addr1_bob..."
    def treasuryAccount = Mock(TxSigner)

    def setup() {
        mockBackendService = Mock(BackendService)
        TxHandlerRegistry.clear() // Clean registry for each test
    }

    def "should compose single transaction with context using V3 design"() {
        when:
        def groovyTxContext = compose(mockBackendService) {
            tx {
                from treasury
                send 5.ada to alice
            }
            
            context {
                feePayer treasury
                signer treasuryAccount
            }
        }
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should compose multiple transactions with shared context"() {
        when:
        def groovyTxContext = compose(mockBackendService) {
            tx {
                from treasury
                send 100.ada to alice
            }
            
            tx {
                from treasury
                send 150.ada to bob
            }
            
            context {
                feePayer treasury
                collateralPayer treasury
                utxoSelection "LARGEST_FIRST"
                signer treasuryAccount
            }
        }
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should support property-style context configuration"() {
        when:
        def groovyTxContext = compose(mockBackendService) {
            tx {
                from treasury
                send 5.ada to alice
            }
            
            context {
                feePayer = treasury
                collateralPayer = treasury
                utxoSelection = "LARGEST_FIRST"
                signer = treasuryAccount
            }
        }
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should compose pre-built transactions with shared context"() {
        given:
        def payment1 = transaction {
            from treasury
            send 100.ada to alice
        }
        
        def payment2 = transaction {
            from treasury
            send 150.ada to bob
        }
        
        when:
        def groovyTxContext = compose(mockBackendService, payment1, payment2)
            .feePayer(treasury)
            .signer(treasuryAccount)
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should support mixed Java/Groovy transaction composition"() {
        given:
        def javaTxDsl = new TxDsl()
            .from(treasury)
            .payToAddress(alice, 5.ada)
        
        def groovyTx = transaction {
            from treasury
            send 3.ada to bob
        }
        
        when:
        def groovyTxContext = compose(mockBackendService, javaTxDsl, groovyTx.build())
            .feePayer(treasury)
            .signer(treasuryAccount)
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should support variables in transactions and context"() {
        when:
        def groovyTxContext = compose(mockBackendService) {
            tx {
                variables {
                    treasury = "addr1_treasury_resolved..."
                    alice = "addr1_alice_resolved..."
                }
                
                from treasury
                send 5.ada to alice
            }
            
            context {
                feePayer treasury
                signer treasuryAccount
            }
        }
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should support complex multi-step transaction composition"() {
        when:
        def groovyTxContext = compose(mockBackendService) {
            // First transaction: Treasury distributes to users
            tx {
                from treasury
                send 1000.ada to alice
                send 1500.ada to bob
            }
            
            // Second transaction: Alice sends to Bob
            tx {
                from alice
                send 100.ada to bob
            }
            
            // Third transaction: Bob sends back to treasury
            tx {
                from bob
                send 50.ada to treasury
            }
            
            context {
                feePayer treasury
                collateralPayer treasury
                utxoSelection "LARGEST_FIRST"
                signer treasuryAccount
            }
        }
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should chain additional context configuration after compose"() {
        when:
        def groovyTxContext = compose(mockBackendService) {
            tx {
                from treasury
                send 5.ada to alice
            }
        }
        .feePayer(treasury)
        .collateralPayer(treasury)
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should maintain backward compatibility with transaction method"() {
        when:
        def traditionalTx = transaction {
            from treasury
            send 5.ada to alice
        }
        
        then:
        traditionalTx instanceof TxGroovyDsl
        traditionalTx.unwrap() != null
    }

    def "should work with fromYaml integration"() {
        given:
        def yaml = """
version: 1.0
transaction:
  - tx:
      intentions:
        - type: from
          address: "addr1_treasury..."
        - type: payment
          address: "addr1_alice..."
          amounts: 
            - unit: lovelace
              quantity: "5000000"
"""
        
        when:
        def loadedTx = fromYaml(yaml)
        def groovyTxContext = compose(mockBackendService, loadedTx)
            .feePayer(treasury)
            .signer(treasuryAccount)
        
        then:
        groovyTxContext instanceof GroovyTxContext
    }

    def "should throw meaningful errors for invalid usage"() {
        when:
        compose(null) {
            tx {
                from treasury
                send 5.ada to alice
            }
        }
        
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("BackendService is required")
    }

    def "should throw error when no transactions defined"() {
        when:
        compose(mockBackendService) {
            context {
                feePayer treasury
            }
        }
        
        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("No transactions defined")
    }

    def "should throw error for empty pre-built transaction array"() {
        when:
        compose(mockBackendService, new TxGroovyDsl[0])
        
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("At least one transaction is required")
    }

    def "should support template-style usage with variables"() {
        when:
        def template = compose(mockBackendService) {
            tx {
                from '${TREASURY}'
                send 5.ada to '${RECIPIENT}'
            }
            
            context {
                feePayer '${TREASURY}'
                signer treasuryAccount
            }
        }
        
        then:
        template instanceof GroovyTxContext
    }
}
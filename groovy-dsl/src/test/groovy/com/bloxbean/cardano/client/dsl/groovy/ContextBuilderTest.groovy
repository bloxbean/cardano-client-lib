package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.dsl.TxDslBuilder
import spock.lang.Specification

/**
 * Test ContextBuilder functionality for Groovy-style context configuration
 */
class ContextBuilderTest extends Specification {

    BackendService mockBackendService
    TxDslBuilder txDslBuilder
    ContextBuilder contextBuilder
    
    // Test variables
    def treasury = "addr1_treasury..."
    def treasuryAccount = "treasurySigner"

    def setup() {
        mockBackendService = Mock(BackendService)
        txDslBuilder = new TxDslBuilder(mockBackendService)
        contextBuilder = new ContextBuilder(txDslBuilder)
    }

    def "should create ContextBuilder with TxDslBuilder"() {
        when:
        def builder = new ContextBuilder(txDslBuilder)
        
        then:
        builder != null
    }

    def "should set feePayer with method call"() {
        when:
        def result = contextBuilder.feePayer(treasury)
        
        then:
        result == contextBuilder // Should return self for chaining
    }

    def "should set collateralPayer with method call"() {
        when:
        def result = contextBuilder.collateralPayer(treasury)
        
        then:
        result == contextBuilder // Should return self for chaining
    }

    def "should set utxoSelection with method call"() {
        when:
        def result = contextBuilder.utxoSelection("LARGEST_FIRST")
        
        then:
        result == contextBuilder // Should return self for chaining
    }

    def "should set utxoSelectionStrategy with method call"() {
        when:
        def result = contextBuilder.utxoSelectionStrategy("LARGEST_FIRST")
        
        then:
        result == contextBuilder // Should return self for chaining
    }

    def "should set signer with method call"() {
        when:
        def result = contextBuilder.signer(treasuryAccount)
        
        then:
        result == contextBuilder // Should return self for chaining
    }

    def "should support property-style assignment"() {
        when:
        contextBuilder.feePayer = treasury
        contextBuilder.collateralPayer = treasury
        contextBuilder.utxoSelection = "LARGEST_FIRST"
        contextBuilder.signer = treasuryAccount
        
        then:
        noExceptionThrown()
    }

    def "should support method chaining"() {
        when:
        def result = contextBuilder
            .feePayer(treasury)
            .collateralPayer(treasury)
            .utxoSelection("LARGEST_FIRST")
            .signer(treasuryAccount)
        
        then:
        result == contextBuilder
    }

    def "should handle unknown property assignment gracefully"() {
        when:
        contextBuilder.unknownProperty = "value"
        
        then:
        def ex = thrown(MissingPropertyException)
        ex.message.contains("Unknown context property: unknownProperty")
    }

    def "should list valid properties in error message"() {
        when:
        contextBuilder.invalidProperty = "value"
        
        then:
        def ex = thrown(MissingPropertyException)
        ex.message.contains("Valid properties: feePayer, collateralPayer, utxoSelection, signer")
    }

    def "should support propertyMissing for alternative syntax"() {
        when:
        contextBuilder.with {
            feePayer = treasury
            collateralPayer = treasury
        }
        
        then:
        noExceptionThrown()
    }

    def "should handle methodMissing for property-like calls"() {
        when:
        def result = contextBuilder.feePayer(treasury)
        
        then:
        result == contextBuilder
    }

    def "should throw exception for invalid method calls"() {
        when:
        contextBuilder.invalidMethod("arg1", "arg2")
        
        then:
        def ex = thrown(MissingMethodException)
        ex.message.contains("invalidMethod")
    }

    def "should convert address objects to strings"() {
        given:
        def addressObject = [toString: { -> treasury }] as Object
        
        when:
        contextBuilder.feePayer(addressObject)
        
        then:
        noExceptionThrown()
    }

    def "should handle null values gracefully"() {
        when:
        contextBuilder.feePayer(null)
        
        then:
        noExceptionThrown()
    }

    def "should support all context properties"() {
        when:
        contextBuilder.with {
            feePayer treasury
            collateralPayer treasury
            utxoSelection "LARGEST_FIRST"
            utxoSelectionStrategy "RANDOM"  // Alternative name
            signer treasuryAccount
        }
        
        then:
        noExceptionThrown()
    }
}
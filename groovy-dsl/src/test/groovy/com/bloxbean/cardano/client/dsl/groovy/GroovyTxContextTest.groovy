package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.api.model.Result
import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.common.model.Networks
import com.bloxbean.cardano.client.function.TxSigner
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder
import com.bloxbean.cardano.client.quicktx.TxResult
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
/**
 * Test GroovyTxContext functionality for execution wrapper
 */
class GroovyTxContextTest extends Specification {

    BackendService mockBackendService
    QuickTxBuilder.TxContext mockTxContext
    GroovyTxContext groovyTxContext

    // Test variables
    def treasury = "addr1_treasury..."

    def setup() {
        mockBackendService = Mock(BackendService)
        mockTxContext = Mock(QuickTxBuilder.TxContext)
        groovyTxContext = new GroovyTxContext(mockTxContext)
    }

    def "should create GroovyTxContext with TxContext"() {
        when:
        def context = new GroovyTxContext(mockTxContext)

        then:
        context != null
    }

    def "should delegate complete() to underlying TxContext"() {
        given:
        def expectedResult = TxResult.fromResult(Result.success("tx_hash_123"))
        mockTxContext.complete() >> expectedResult

        when:
        def result = groovyTxContext.complete()

        then:
        result == expectedResult
    }

    def "should delegate completeAsync() to underlying TxContext"() {
        given:
        def expectedResult = TxResult.fromResult(Result.success("tx_hash_123"))
        def expectedFuture = CompletableFuture.completedFuture(expectedResult)
        mockTxContext.completeAndWaitAsync() >> expectedFuture

        when:
        def result = groovyTxContext.completeAsync()

        then:
        result == expectedFuture
    }

    def "should delegate completeAndWait() to underlying TxContext"() {
        given:
        def expectedResult = TxResult.fromResult(Result.success("tx_hash_123"))
        mockTxContext.completeAndWait() >> expectedResult

        when:
        def result = groovyTxContext.completeAndWait()

        then:
        result == expectedResult
    }

    def "should support additional feePayer configuration with chaining"() {
        when:
        def result = groovyTxContext.feePayer(treasury)

        then:
        1 * mockTxContext.feePayer(treasury)
        result == groovyTxContext // Should return self for chaining
    }

    def "should support additional collateralPayer configuration with chaining"() {
        when:
        def result = groovyTxContext.collateralPayer(treasury)

        then:
        1 * mockTxContext.collateralPayer(treasury)
        result == groovyTxContext // Should return self for chaining
    }

    def "should support withSigner configuration"() {
        given:
        def mockSigner = Mock(TxSigner)

        when:
        def result = groovyTxContext.withSigner(mockSigner)

        then:
        1 * mockTxContext.withSigner(mockSigner)
        result == groovyTxContext
    }

    def "should support withNetwork configuration"() {
        given:
        def network = Networks.testnet()

        when:
        def result = groovyTxContext.withNetwork(network)

        then:
        // withNetwork is a no-op since QuickTxBuilder.TxContext doesn't support it
        0 * mockTxContext.withNetwork(_)
        result == groovyTxContext
    }

    def "should support withAccount convenience method"() {
        given:
        def mockSigner = Mock(TxSigner)
        def mockAccount = new Object() {
            def getSignerProvider() { return mockSigner }
        }

        when:
        def result = groovyTxContext.withAccount(mockAccount)

        then:
        1 * mockTxContext.withSigner(mockSigner)
        result == groovyTxContext
    }

    def "should support withAccount with signerProvider method"() {
        given:
        def mockSigner = Mock(TxSigner)
        def mockAccount = new Object() {
            def signerProvider() { return mockSigner }
        }

        when:
        def result = groovyTxContext.withAccount(mockAccount)

        then:
        1 * mockTxContext.withSigner(mockSigner)
        result == groovyTxContext
    }

    def "should throw exception for invalid account"() {
        given:
        def invalidAccount = [:] as Object  // Object with no methods

        when:
        groovyTxContext.withAccount(invalidAccount)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Account must have getSignerProvider() or signerProvider() method")
    }

    def "should support method chaining for context configuration"() {
        given:
        def mockSigner = Mock(TxSigner)
        def network = Networks.testnet()

        when:
        def result = groovyTxContext
            .feePayer(treasury)
            .collateralPayer(treasury)
            .withSigner(mockSigner)
            .withNetwork(network)

        then:
        1 * mockTxContext.feePayer(treasury)
        1 * mockTxContext.collateralPayer(treasury)
        1 * mockTxContext.withSigner(mockSigner)
        0 * mockTxContext.withNetwork(_)  // withNetwork is a no-op
        result == groovyTxContext
    }

    def "should support property-style assignment"() {
        when:
        groovyTxContext.feePayer = treasury
        groovyTxContext.collateralPayer = treasury

        then:
        1 * mockTxContext.feePayer(treasury)
        1 * mockTxContext.collateralPayer(treasury)
    }

    def "should delegate methodMissing to underlying TxContext"() {
        given:
        mockTxContext.metaClass.someMethod = { String arg1, String arg2 -> "result" }

        when:
        def result = groovyTxContext.someMethod("arg1", "arg2")

        then:
        result == "result"
    }

    def "should delegate methodMissing with chaining"() {
        given:
        mockTxContext.metaClass.someChainableMethod = { String arg -> mockTxContext }

        when:
        def result = groovyTxContext.someChainableMethod("arg")

        then:
        result == groovyTxContext // Should wrap and return self
    }

    def "should handle methodMissing exceptions"() {
        when:
        groovyTxContext.nonExistentMethod()

        then:
        def ex = thrown(MissingMethodException)
        ex.message.contains("nonExistentMethod")
    }

    def "should delegate propertyMissing to underlying TxContext"() {
        given:
        mockTxContext.metaClass.getSomeProperty = { -> "property_value" }

        when:
        def result = groovyTxContext.someProperty

        then:
        result == "property_value"
    }

    def "should handle propertyMissing exceptions"() {
        when:
        groovyTxContext.nonExistentProperty

        then:
        def ex = thrown(MissingPropertyException)
        ex.message.contains("nonExistentProperty")
    }

    def "should delegate property assignment to underlying TxContext"() {
        given:
        mockTxContext.metaClass.setSomeProperty = { String value -> }

        when:
        groovyTxContext.someProperty = "value"

        then:
        notThrown(Exception)
    }

    def "should provide access to underlying TxContext via unwrap"() {
        when:
        def unwrapped = groovyTxContext.unwrap()

        then:
        unwrapped == mockTxContext
    }

    def "should provide meaningful toString"() {
        given:
        mockTxContext.toString() >> "MockTxContext[...]"

        when:
        def str = groovyTxContext.toString()

        then:
        str == "GroovyTxContext[MockTxContext[...]]"
    }
}

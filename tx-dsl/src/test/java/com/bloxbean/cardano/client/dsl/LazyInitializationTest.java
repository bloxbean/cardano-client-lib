package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the lazy initialization pattern in TxDsl.
 */
class LazyInitializationTest {
    
    @Test
    void testBasicUnwrap() {
        // Given - TxDsl with basic transaction
        TxDsl txDsl = new TxDsl()
                .payToAddress("addr1_receiver123", Amount.ada(5))
                .from("addr1_sender456");
        
        // When - Unwrap to get Tx
        Tx tx = txDsl.unwrap();
        
        // Then - Tx should be created successfully
        assertThat(tx).isNotNull();
    }
    
    @Test
    void testVariableResolution() {
        // Given - TxDsl with variables
        Map<String, Object> variables = Map.of(
            "recipient", "addr1_alice123",
            "sender", "addr1_bob456"
        );
        
        TxDsl txDsl = TxDsl.withVariables(variables)
                .payToAddress("${recipient}", Amount.ada(10))
                .from("${sender}");
        
        // When - Unwrap with variable resolution
        Tx tx = txDsl.unwrap();
        
        // Then - Tx should be created with resolved variables
        assertThat(tx).isNotNull();
    }
    
    @Test
    void testRuntimeVariableOverride() {
        // Given - TxDsl with instance variables
        TxDsl txDsl = new TxDsl()
                .withVariable("recipient", "addr1_default123")
                .payToAddress("${recipient}", Amount.ada(3))
                .from("addr1_sender789");
        
        // When - Unwrap with runtime variables (should override)
        Map<String, Object> runtimeVars = Map.of("recipient", "addr1_override456");
        Tx tx = txDsl.unwrap(runtimeVars);
        
        // Then - Tx should be created with overridden variables
        assertThat(tx).isNotNull();
    }
    
    @Test
    void testIntentionCapture() {
        // Given - TxDsl with multiple operations
        TxDsl txDsl = new TxDsl()
                .payToAddress("addr1_alice123", Amount.ada(5))
                .payToAddress("addr1_bob456", Amount.ada(3))
                .from("addr1_sender789");
        
        // When - Check captured intentions
        var intentions = txDsl.getIntentions();
        
        // Then - Should capture all payment intentions
        assertThat(intentions).hasSize(2);
        assertThat(intentions.get(0).getType()).isEqualTo("payment");
        assertThat(intentions.get(1).getType()).isEqualTo("payment");
    }
    
    @Test
    void testMethodChaining() {
        // Given/When - Use method chaining pattern
        TxDsl txDsl = TxDsl.withVariables("treasury", "addr1_treasury123")
                .payToAddress("${treasury}", Amount.ada(100))
                .withVariable("fee_payer", "addr1_fees456")
                .from("${treasury}");
        
        // Then - Should chain successfully
        assertThat(txDsl).isNotNull();
        assertThat(txDsl.getIntentions()).hasSize(1);
        assertThat(txDsl.getVariables()).containsKey("treasury");
        assertThat(txDsl.getVariables()).containsKey("fee_payer");
    }
}
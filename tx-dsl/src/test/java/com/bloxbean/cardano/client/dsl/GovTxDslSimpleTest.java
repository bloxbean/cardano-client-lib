package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.dsl.intention.TxIntention;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test for TxDsl governance functionality.
 * This test focuses on the DSL structure and composition without complex governance object construction.
 */
class GovTxDslSimpleTest {

    @Test
    void testTxDslCreation() {
        // When
        TxDsl txDsl = new TxDsl();
        
        // Then
        assertThat(txDsl).isNotNull();
        assertThat(txDsl.getIntentions()).isEmpty();
    }

    @Test
    void testTxDslWithFromAddress() {
        // Given
        TxDsl txDsl = new TxDsl();
        
        // When
        txDsl.from("addr1qyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gxsxkxkxkx");
        
        // Then
        assertThat(txDsl).isNotNull();
        assertThat(txDsl.getIntentions()).isEmpty();
    }

    @Test
    void testYamlSerialization() {
        // Given
        TxDsl txDsl = new TxDsl();
        txDsl.from("addr1qyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gxsxkxkxkx");
        
        // When
        String yaml = txDsl.toYaml();
        
        // Then
        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("from: addr1qyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gxsxkxkxkx");
        assertThat(yaml).contains("version: 1.0");
    }

    @Test
    void testFromYaml() {
        // Given
        String yaml = "version: 1.0\n" +
                "transaction:\n" +
                "  - tx:\n" +
                "      from: addr1qyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gxsxkxkxkx\n" +
                "      intentions: []";
        
        // When
        TxDsl txDsl = TxDsl.fromYaml(yaml);
        
        // Then
        assertThat(txDsl).isNotNull();
        assertThat(txDsl.getIntentions()).isEmpty();
    }

    @Test
    void testTxDslFunctionality() {
        // Given
        TxDsl txDsl = new TxDsl();
        
        // When - use TxDsl functionality directly
        txDsl.from("addr1qyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gxsxkxkxkx")
             .payToAddress("addr1qyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gxsxkxkxkx", 
                           com.bloxbean.cardano.client.api.model.Amount.ada(5));
        
        // Then
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(1);
        assertThat(intentions.get(0).getType()).isEqualTo("payment");
    }
    
    @Test
    void testTxDslFluentChaining() {
        // Given
        TxDsl txDsl = new TxDsl();
        
        // When - test that TxDsl can be chained fluently
        String yaml = txDsl.from("addr1qyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gxsxkxkxkx")
                           .payToAddress("addr1qyehkck0lajq8gr28t9uxnuvgcqrc6070x3k9r8048z8y5gxsxkxkxkx", 
                                         com.bloxbean.cardano.client.api.model.Amount.ada(10))
                           .toYaml();
                
        // Then
        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("from:");
        assertThat(yaml).contains("intentions:");
        assertThat(yaml).contains("type: payment");
    }
}
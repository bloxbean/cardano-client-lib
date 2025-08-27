package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.dsl.intention.TxIntention;
import com.bloxbean.cardano.client.quicktx.Tx;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TxDslTest {

    @Test
    void testTxDslCreation() {
        // Given/When
        TxDsl txDsl = new TxDsl();
        
        // Then
        assertThat(txDsl).isNotNull();
    }
    
    @Test
    void testUnwrapReturnsTx() {
        // Given
        TxDsl txDsl = new TxDsl();
        
        // When
        Tx tx = txDsl.unwrap();
        
        // Then
        assertThat(tx).isNotNull();
        assertThat(tx).isInstanceOf(Tx.class);
    }
    
    @Test
    void testPayToAddressDelegation() {
        // Given
        TxDsl txDsl = new TxDsl();
        String address = "addr1qxxx...";
        Amount amount = Amount.ada(5);
        
        // When
        TxDsl result = txDsl.payToAddress(address, amount);
        
        // Then
        assertThat(result).isSameAs(txDsl); // Fluent API
        Tx tx = txDsl.unwrap();
        // The Tx should have the payment (we'll verify this works when we implement)
        assertThat(tx).isNotNull();
    }
    
    @Test
    void testFromDelegation() {
        // Given
        TxDsl txDsl = new TxDsl();
        String sender = "addr1_sender...";
        
        // When
        TxDsl result = txDsl.from(sender);
        
        // Then
        assertThat(result).isSameAs(txDsl); // Fluent API
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();
    }
    
    @Test
    void testMethodChaining() {
        // Given
        String sender = "addr1_sender...";
        String receiver = "addr1_receiver...";
        Amount amount = Amount.ada(10);
        
        // When
        TxDsl txDsl = new TxDsl()
            .from(sender)
            .payToAddress(receiver, amount);
        
        // Then
        assertThat(txDsl).isNotNull();
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();
    }
    
    @Test
    void testPaymentIntentionCapture() {
        // Given
        TxDsl txDsl = new TxDsl();
        String address = "addr1_receiver...";
        Amount amount = Amount.ada(5);
        
        // When
        txDsl.payToAddress(address, amount);
        
        // Then
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(1);
        TxIntention intention = intentions.get(0);
        assertThat(intention.getType()).isEqualTo("payment");
    }
    
    @Test
    void testFromIntentionCapture() {
        // Given
        TxDsl txDsl = new TxDsl();
        String sender = "addr1_sender...";
        
        // When
        txDsl.from(sender);
        
        // Then
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(0); // from is now an attribute, not an intention
    }
    
    @Test
    void testMultipleIntentionsCapture() {
        // Given
        TxDsl txDsl = new TxDsl();
        String sender = "addr1_sender...";
        String receiver1 = "addr1_receiver1...";
        String receiver2 = "addr1_receiver2...";
        
        // When
        txDsl.from(sender)
            .payToAddress(receiver1, Amount.ada(5))
            .payToAddress(receiver2, Amount.ada(3));
        
        // Then
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(2); // only payment intentions, from is an attribute
        assertThat(intentions.get(0).getType()).isEqualTo("payment");
        assertThat(intentions.get(1).getType()).isEqualTo("payment");
    }
}
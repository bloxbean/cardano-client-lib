package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.dsl.intention.TxIntention;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TxDslPaymentWithScriptTest {

    @Test
    void testPayToAddressWithScriptSingleAmount() throws CborSerializationException {
        // Given
        TxDsl txDsl = new TxDsl();
        String address = "addr1_receiver...";
        Amount amount = Amount.ada(5);
        Script script = ScriptPubkey.createWithNewKey()._1;

        // When
        TxDsl result = txDsl.payToAddress(address, amount, script);

        // Then
        assertThat(result).isSameAs(txDsl); // Fluent API
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();

        // Verify intention captured
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(1);
        TxIntention intention = intentions.get(0);
        assertThat(intention.getType()).isEqualTo("payment");
    }

    @Test
    void testPayToAddressWithScriptMultipleAmounts() {
        // Given
        TxDsl txDsl = new TxDsl();
        String address = "addr1_receiver...";
        List<Amount> amounts = Arrays.asList(
            Amount.ada(5),
            Amount.asset("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736f6c6164", "746f6b656e31", 100)
        );
        Script script = mock(Script.class);

        // When
        TxDsl result = txDsl.payToAddress(address, amounts, script);

        // Then
        assertThat(result).isSameAs(txDsl); // Fluent API
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();

        // Verify intention captured
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(1);
        TxIntention intention = intentions.get(0);
        assertThat(intention.getType()).isEqualTo("payment");
    }

    @Test
    void testPayToAddressWithScriptRefBytes() {
        // Given
        TxDsl txDsl = new TxDsl();
        String address = "addr1_receiver...";
        List<Amount> amounts = Arrays.asList(Amount.ada(10));
        byte[] scriptRefBytes = new byte[]{1, 2, 3, 4, 5};

        // When
        TxDsl result = txDsl.payToAddress(address, amounts, scriptRefBytes);

        // Then
        assertThat(result).isSameAs(txDsl); // Fluent API
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();

        // Verify intention captured
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(1);
        TxIntention intention = intentions.get(0);
        assertThat(intention.getType()).isEqualTo("payment");
    }

    @Test
    void testMethodChainingWithScriptPayments() {
        // Given
        String sender = "addr1_sender...";
        String receiver1 = "addr1_receiver1...";
        String receiver2 = "addr1_receiver2...";
        Script script = mock(Script.class);
        byte[] scriptRefBytes = new byte[]{1, 2, 3};

        // When
        TxDsl txDsl = new TxDsl()
            .from(sender)
            .payToAddress(receiver1, Amount.ada(5), script)
            .payToAddress(receiver2, Arrays.asList(Amount.ada(3)), scriptRefBytes);

        // Then
        assertThat(txDsl).isNotNull();
        Tx tx = txDsl.unwrap();
        assertThat(tx).isNotNull();

        // Verify all intentions captured (from is now an attribute)
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(2); // only payment intentions
        assertThat(intentions.get(0).getType()).isEqualTo("payment");
        assertThat(intentions.get(1).getType()).isEqualTo("payment");
    }

    @Test
    void testMixedPaymentTypes() {
        // Given
        TxDsl txDsl = new TxDsl();
        String receiver1 = "addr1_receiver1...";
        String receiver2 = "addr1_receiver2...";
        Script script = mock(Script.class);

        // When
        txDsl.payToAddress(receiver1, Amount.ada(5))  // Regular payment
             .payToAddress(receiver2, Amount.ada(3), script);  // Payment with script

        // Then
        List<TxIntention> intentions = txDsl.getIntentions();
        assertThat(intentions).hasSize(2);
        assertThat(intentions.get(0).getType()).isEqualTo("payment");
        assertThat(intentions.get(1).getType()).isEqualTo("payment");
    }
}

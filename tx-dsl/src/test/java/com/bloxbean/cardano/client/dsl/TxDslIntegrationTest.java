package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TxDslIntegrationTest {

    @Test
    void testTxDslWithQuickTxBuilder() {
        // Given - Mock backend service
        BackendService backendService = mock(BackendService.class);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        // Create TxDsl instance
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
            .payToAddress("addr1_receiver...", Amount.ada(5));

        // When - Use unwrap() to get Tx for QuickTxBuilder
        Tx tx = txDsl.unwrap();

        // Then - Verify we can pass it to QuickTxBuilder
        assertThat(tx).isNotNull();
        // In a real test, we would compose and verify, but we need mock setup
        // This proves the API compatibility
        QuickTxBuilder.TxContext context = quickTxBuilder.compose(tx);
        assertThat(context).isNotNull();
    }

    @Test
    void testYamlRoundTripWithQuickTxBuilder() {
        // Given - Create and serialize TxDsl
        TxDsl original = new TxDsl()
            .from("addr1_treasury...")
            .payToAddress("addr1_alice...", Amount.ada(10))
            .payToAddress("addr1_bob...", Amount.ada(5));

        String yaml = original.toYaml();

//        System.out.println(yaml);

        // When - Deserialize and use with QuickTxBuilder
        TxDsl restored = TxDsl.fromYaml(yaml);
        BackendService backendService = mock(BackendService.class);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        // Then - Verify restored TxDsl works with QuickTxBuilder
        Tx tx = restored.unwrap();
        assertThat(tx).isNotNull();
        QuickTxBuilder.TxContext context = quickTxBuilder.compose(tx);
        assertThat(context).isNotNull();

        // Verify intentions were preserved (from is now an attribute)
        assertThat(restored.getIntentions()).hasSize(2);
        assertThat(restored.getIntentions().get(0).getType()).isEqualTo("payment");
        assertThat(restored.getIntentions().get(1).getType()).isEqualTo("payment");
    }

    @Test
    void testMultipleTxDslComposition() {
        // Given - Multiple TxDsl instances
        TxDsl txDsl1 = new TxDsl()
            .from("addr1_treasury...")
            .payToAddress("addr1_alice...", Amount.ada(5));

        TxDsl txDsl2 = new TxDsl()
            .payToAddress("addr1_bob...", Amount.ada(3));

        // When - Compose multiple transactions
        BackendService backendService = mock(BackendService.class);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        QuickTxBuilder.TxContext context = quickTxBuilder
            .compose(txDsl1.unwrap(), txDsl2.unwrap());

        // Then - Verify composition works
        assertThat(context).isNotNull();
    }

    @Test
    void testTxDslAsDropInReplacement() {
        // This test demonstrates that TxDsl can be used as a drop-in replacement for Tx

        // Original code pattern with Tx
        Tx originalTx = new Tx()
            .from("addr1_sender...")
            .payToAddress("addr1_receiver...", Amount.ada(5));

        // New code pattern with TxDsl - same API
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
            .payToAddress("addr1_receiver...", Amount.ada(5));

        // Both work with QuickTxBuilder
        BackendService backendService = mock(BackendService.class);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        QuickTxBuilder.TxContext contextOriginal = quickTxBuilder.compose(originalTx);
        QuickTxBuilder.TxContext contextDsl = quickTxBuilder.compose(txDsl.unwrap());

        assertThat(contextOriginal).isNotNull();
        assertThat(contextDsl).isNotNull();
    }
}

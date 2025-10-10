package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for TxPlan integration with real QuickTxBuilder (using mock backend).
 */
class TxPlanRealQuickTxBuilderTest {

    @Test
    void real_quicktxbuilder_compose_with_txplan_works() {
        // Given
        BackendService mockBackend = Mockito.mock(BackendService.class);
        QuickTxBuilder builder = new QuickTxBuilder(mockBackend);

        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan plan = TxPlan.from(tx)
            .feePayer("addr1_fee_payer")
            .collateralPayer("addr1_collateral_payer")
            .withRequiredSigners("ab123def", "cd456efa")
            .validFrom(1000L)
            .validTo(2000L);

        // When - this should not throw any exceptions
        QuickTxBuilder.TxContext context = builder.compose(plan);

        // Then - verify the context exists and is properly configured
        assertThat(context).isNotNull();

        // We can't directly test the internal state since TxContext doesn't expose getters,
        // but we can verify that the compose method works without errors and returns a valid context
        assertThat(context).isInstanceOf(QuickTxBuilder.TxContext.class);
    }

    @Test
    void real_quicktxbuilder_compose_with_null_plan_throws_exception() {
        // Given
        BackendService mockBackend = Mockito.mock(BackendService.class);
        QuickTxBuilder builder = new QuickTxBuilder(mockBackend);

        // When/Then
        assertThrows(RuntimeException.class, () -> builder.compose((TxPlan) null));
    }

    @Test
    void real_quicktxbuilder_compose_with_empty_plan_throws_exception() {
        // Given
        BackendService mockBackend = Mockito.mock(BackendService.class);
        QuickTxBuilder builder = new QuickTxBuilder(mockBackend);
        TxPlan emptyPlan = new TxPlan();

        // When/Then
        assertThrows(RuntimeException.class, () -> builder.compose(emptyPlan));
    }

    @Test
    void yaml_to_quicktxbuilder_pipeline_works() {
        // Given
        String yaml = "version: 1.0\n" +
                "context:\n" +
                "  fee_payer: addr1_fee_payer\n" +
                "  valid_to_slot: 2000\n" +
                "transaction:\n" +
                "  - tx:\n" +
                "      from: addr1_sender\n" +
                "      intents:\n" +
                "        - type: payment\n" +
                "          to: addr1_receiver\n" +
                "          amount:\n" +
                "            unit: lovelace\n" +
                "            quantity: 5000000\n";

        BackendService mockBackend = Mockito.mock(BackendService.class);
        QuickTxBuilder builder = new QuickTxBuilder(mockBackend);

        // When - complete pipeline: YAML → TxPlan → TxContext
        TxPlan plan = TxPlan.from(yaml);
        QuickTxBuilder.TxContext context = builder.compose(plan);

        // Then - verify pipeline works end-to-end
        assertThat(plan.getFeePayer()).isEqualTo("addr1_fee_payer");
        assertThat(plan.getValidToSlot()).isEqualTo(2000L);
        assertThat(plan.getTxs()).hasSize(1);

        assertThat(context).isNotNull();
        assertThat(context).isInstanceOf(QuickTxBuilder.TxContext.class);
    }

    @Test
    void multiple_transactions_with_context_properties() {
        // Given
        Tx tx1 = new Tx()
            .from("addr1_sender1")
            .payToAddress("addr1_receiver1", Amount.ada(5));

        Tx tx2 = new Tx()
            .from("addr1_sender2")
            .payToAddress("addr1_receiver2", Amount.ada(3));

        TxPlan plan = new TxPlan()
            .addTransaction(tx1)
            .addTransaction(tx2)
            .feePayer("addr1_fee_payer")
            .validFrom(1000L);

        BackendService mockBackend = Mockito.mock(BackendService.class);
        QuickTxBuilder builder = new QuickTxBuilder(mockBackend);

        // When
        QuickTxBuilder.TxContext context = builder.compose(plan);

        // Then
        assertThat(context).isNotNull();
        assertThat(plan.getTxs()).hasSize(2);
        assertThat(plan.getFeePayer()).isEqualTo("addr1_fee_payer");
        assertThat(plan.getValidFromSlot()).isEqualTo(1000L);
    }
}

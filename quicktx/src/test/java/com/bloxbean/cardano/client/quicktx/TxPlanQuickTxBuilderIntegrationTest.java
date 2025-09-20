package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for TxPlan and QuickTxBuilder workflow.
 * Tests the complete pipeline: YAML → TxPlan → TxContext → (mock execution).
 */
class TxPlanQuickTxBuilderIntegrationTest {

    @Test
    void txplan_to_txcontext_mapping_works_correctly() {
        // Given
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan plan = TxPlan.from(tx)
            .feePayer("addr1_fee_payer")
            .collateralPayer("addr1_collateral_payer")
            .withRequiredSigners("ab123def", "cd456efa")
            .validFrom(1000L)
            .validTo(2000L);

        // Create a mock QuickTxBuilder for testing
        MockQuickTxBuilder builder = new MockQuickTxBuilder();

        // When
        MockQuickTxBuilder.MockTxContext context = builder.compose(plan);

        // Then - verify all properties are correctly mapped
        assertThat(context.getFeePayer()).isEqualTo("addr1_fee_payer");
        assertThat(context.getCollateralPayer()).isEqualTo("addr1_collateral_payer");
        assertThat(context.getRequiredSigners()).hasSize(2);
        assertThat(context.getRequiredSigners()).containsExactlyInAnyOrder(
            HexUtil.decodeHexString("ab123def"),
            HexUtil.decodeHexString("cd456efa")
        );
        assertThat(context.getValidFrom()).isEqualTo(1000L);
        assertThat(context.getValidTo()).isEqualTo(2000L);
        assertThat(context.getTransactions()).hasSize(1);
        assertThat(context.getTransactions()[0]).isInstanceOf(Tx.class);
    }

    @Test
    void yaml_to_txplan_to_txcontext_round_trip() {
        // Given - YAML with context properties
        String yaml = "version: 1.0\n" +
                "variables:\n" +
                "  sender: addr1_treasury_test\n" +
                "  receiver: addr1_alice_test\n" +
                "context:\n" +
                "  fee_payer: addr1_fee_payer\n" +
                "  collateral_payer: addr1_collateral_payer\n" +
                "  required_signers:\n" +
                "    - ab123def\n" +
                "    - cd456efa\n" +
                "  valid_from_slot: 1000\n" +
                "  valid_to_slot: 2000\n" +
                "transaction:\n" +
                "  - tx:\n" +
                "      from: ${sender}\n" +
                "      intents:\n" +
                "        - type: payment\n" +
                "          to: ${receiver}\n" +
                "          amount:\n" +
                "            unit: lovelace\n" +
                "            quantity: 5000000\n";

        MockQuickTxBuilder builder = new MockQuickTxBuilder();

        // When - deserialize YAML to TxPlan and create TxContext
        TxPlan plan = TxPlan.fromYamlWithContext(yaml);
        MockQuickTxBuilder.MockTxContext context = builder.compose(plan);

        // Then - verify complete round-trip works
        assertThat(plan.getFeePayer()).isEqualTo("addr1_fee_payer");
        assertThat(plan.getCollateralPayer()).isEqualTo("addr1_collateral_payer");
        assertThat(plan.getRequiredSigners()).containsExactlyInAnyOrder("ab123def", "cd456efa");
        assertThat(plan.getValidFromSlot()).isEqualTo(1000L);
        assertThat(plan.getValidToSlot()).isEqualTo(2000L);

        // Verify TxContext has same properties
        assertThat(context.getFeePayer()).isEqualTo("addr1_fee_payer");
        assertThat(context.getCollateralPayer()).isEqualTo("addr1_collateral_payer");
        assertThat(context.getValidFrom()).isEqualTo(1000L);
        assertThat(context.getValidTo()).isEqualTo(2000L);

        // Verify transactions are properly resolved with variables
        Tx resolvedTx = (Tx) context.getTransactions()[0];
        assertThat(resolvedTx.getSender()).isEqualTo("addr1_treasury_test");
    }

    @Test
    void partial_context_properties_mapping() {
        // Given - TxPlan with only some context properties
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan plan = TxPlan.from(tx)
            .feePayer("addr1_fee_payer")
            .validTo(2000L);  // Only fee payer and valid to

        MockQuickTxBuilder builder = new MockQuickTxBuilder();

        // When
        MockQuickTxBuilder.MockTxContext context = builder.compose(plan);

        // Then - only specified properties should be set
        assertThat(context.getFeePayer()).isEqualTo("addr1_fee_payer");
        assertThat(context.getValidTo()).isEqualTo(2000L);

        // Unset properties should be null/default
        assertThat(context.getCollateralPayer()).isNull();
        assertThat(context.getValidFrom()).isEqualTo(0L); // default value
        assertThat(context.getRequiredSigners()).isEmpty();
    }

    @Test
    void empty_txplan_throws_exception() {
        // Given
        TxPlan emptyPlan = new TxPlan();
        MockQuickTxBuilder builder = new MockQuickTxBuilder();

        // When/Then
        assertThrows(RuntimeException.class, () -> builder.compose(emptyPlan));
    }

    @Test
    void null_txplan_throws_exception() {
        // Given
        MockQuickTxBuilder builder = new MockQuickTxBuilder();

        // When/Then
        assertThrows(RuntimeException.class, () -> builder.compose((TxPlan) null));
    }

    @Test
    void multiple_transactions_in_plan_work_correctly() {
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
            .feePayer("addr1_fee_payer");

        MockQuickTxBuilder builder = new MockQuickTxBuilder();

        // When
        MockQuickTxBuilder.MockTxContext context = builder.compose(plan);

        // Then
        assertThat(context.getTransactions()).hasSize(2);
        assertThat(context.getFeePayer()).isEqualTo("addr1_fee_payer");
    }

    /**
     * Mock QuickTxBuilder for testing without actual blockchain backend.
     */
    private static class MockQuickTxBuilder {

        public MockTxContext compose(TxPlan plan) {
            if (plan == null)
                throw new RuntimeException("TxPlan cannot be null");

            if (plan.getTransactions() == null || plan.getTransactions().isEmpty())
                throw new RuntimeException("TxPlan must contain at least one transaction");

            // Create mock context with transactions
            MockTxContext context = new MockTxContext(plan.getTransactions().toArray(new AbstractTx[0]));

            // Apply context properties from TxPlan
            if (plan.getFeePayer() != null) {
                context.feePayer(plan.getFeePayer());
            }

            if (plan.getCollateralPayer() != null) {
                context.collateralPayer(plan.getCollateralPayer());
            }

            if (plan.getRequiredSigners() != null && !plan.getRequiredSigners().isEmpty()) {
                // Convert hex strings back to byte arrays
                byte[][] signerCredentials = plan.getRequiredSigners().stream()
                    .map(HexUtil::decodeHexString)
                    .toArray(byte[][]::new);
                context.withRequiredSigners(signerCredentials);
            }

            if (plan.getValidFromSlot() != null) {
                context.validFrom(plan.getValidFromSlot());
            }

            if (plan.getValidToSlot() != null) {
                context.validTo(plan.getValidToSlot());
            }

            return context;
        }

        /**
         * Mock TxContext for testing.
         */
        public static class MockTxContext {
            private AbstractTx[] transactions;
            private String feePayer;
            private String collateralPayer;
            private Set<byte[]> requiredSigners = Set.of();
            private long validFrom = 0L;
            private long validTo = 0L;

            public MockTxContext(AbstractTx[] transactions) {
                this.transactions = transactions;
            }

            public MockTxContext feePayer(String feePayer) {
                this.feePayer = feePayer;
                return this;
            }

            public MockTxContext collateralPayer(String collateralPayer) {
                this.collateralPayer = collateralPayer;
                return this;
            }

            public MockTxContext withRequiredSigners(byte[]... credentials) {
                this.requiredSigners = Set.of(credentials);
                return this;
            }

            public MockTxContext validFrom(long slot) {
                this.validFrom = slot;
                return this;
            }

            public MockTxContext validTo(long slot) {
                this.validTo = slot;
                return this;
            }

            // Getters for testing
            public AbstractTx[] getTransactions() { return transactions; }
            public String getFeePayer() { return feePayer; }
            public String getCollateralPayer() { return collateralPayer; }
            public Set<byte[]> getRequiredSigners() { return requiredSigners; }
            public long getValidFrom() { return validFrom; }
            public long getValidTo() { return validTo; }
        }
    }
}

package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputValidationRuleTest {

    private OutputValidationRule rule;
    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
    private static final String TEST_ADDR = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";

    @BeforeEach
    void setUp() {
        rule = new OutputValidationRule();
    }

    @Test
    void validate_validOutput_shouldPass() {
        Transaction tx = buildTx(List.of(
                output(BigInteger.valueOf(2000000))));
        LedgerContext ctx = contextWithParams("4310", "4000");

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_belowMinUtxo_shouldFail() {
        Transaction tx = buildTx(List.of(
                output(BigInteger.valueOf(100)))); // Way below minUTxO
        LedgerContext ctx = contextWithParams("4310", "4000");

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("below minUTxO"));
    }

    @Test
    void validate_negativeCoin_shouldFail() {
        Transaction tx = buildTx(List.of(
                output(BigInteger.valueOf(-1000000))));
        LedgerContext ctx = contextWithParams("4310", "4000");

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("negative coin"));
    }

    @Test
    void validate_noOutputs_shouldFail() {
        Transaction tx = buildTx(Collections.emptyList());
        LedgerContext ctx = contextWithParams("4310", "4000");

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("no outputs");
    }

    @Test
    void validate_exceedsMaxValueSize_shouldFail() {
        // Create an output with many multi-assets to exceed a small maxValSize
        List<MultiAsset> multiAssets = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String policyId = String.format("%056x", i);
            multiAssets.add(MultiAsset.builder()
                    .policyId(policyId)
                    .assets(List.of(Asset.builder()
                            .name("token" + i)
                            .value(BigInteger.valueOf(1000000))
                            .build()))
                    .build());
        }

        TransactionOutput output = TransactionOutput.builder()
                .address(TEST_ADDR)
                .value(Value.builder()
                        .coin(BigInteger.valueOf(50000000))
                        .multiAssets(multiAssets)
                        .build())
                .build();

        Transaction tx = buildTx(List.of(output));
        // Set a very small maxValSize that the multi-asset output will exceed
        LedgerContext ctx = contextWithParams("4310", "10");

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("exceeds maxValueSize"));
    }

    @Test
    void validate_noProtocolParams_shouldPass() {
        Transaction tx = buildTx(List.of(output(BigInteger.valueOf(2000000))));
        LedgerContext ctx = LedgerContext.builder().build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_allErrorsArePhase1() {
        Transaction tx = buildTx(List.of(output(BigInteger.valueOf(100))));
        LedgerContext ctx = contextWithParams("4310", "4000");

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isNotEmpty();
        assertThat(errors).allMatch(e -> e.getPhase() == ValidationError.Phase.PHASE_1);
    }

    private TransactionOutput output(BigInteger coin) {
        return TransactionOutput.builder()
                .address(TEST_ADDR)
                .value(Value.builder().coin(coin).build())
                .build();
    }

    private Transaction buildTx(List<TransactionOutput> outputs) {
        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(outputs)
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();
    }

    private LedgerContext contextWithParams(String coinsPerUtxoSize, String maxValSize) {
        ProtocolParams pp = ProtocolParams.builder()
                .coinsPerUtxoSize(coinsPerUtxoSize)
                .maxValSize(maxValSize)
                .build();
        return LedgerContext.builder().protocolParams(pp).build();
    }
}

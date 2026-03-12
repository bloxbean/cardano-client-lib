package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TxSizeValidationRuleTest {

    private TxSizeValidationRule rule;
    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";

    @BeforeEach
    void setUp() {
        rule = new TxSizeValidationRule();
    }

    @Test
    void validate_txWithinMaxSize_shouldPass() {
        Transaction tx = buildSimpleTx();
        ProtocolParams pp = ProtocolParams.builder().maxTxSize(16384).build();
        LedgerContext ctx = LedgerContext.builder().protocolParams(pp).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_txExceedsMaxSize_shouldFail() {
        Transaction tx = buildSimpleTx();
        // Set max size to 1 byte — any tx will exceed this
        ProtocolParams pp = ProtocolParams.builder().maxTxSize(1).build();
        LedgerContext ctx = LedgerContext.builder().protocolParams(pp).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("exceeds maxTxSize");
        assertThat(errors.get(0).getPhase()).isEqualTo(ValidationError.Phase.PHASE_1);
    }

    @Test
    void validate_noProtocolParams_shouldPass() {
        Transaction tx = buildSimpleTx();
        LedgerContext ctx = LedgerContext.builder().build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_nullMaxTxSize_shouldPass() {
        Transaction tx = buildSimpleTx();
        ProtocolParams pp = ProtocolParams.builder().maxTxSize(null).build();
        LedgerContext ctx = LedgerContext.builder().protocolParams(pp).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    private Transaction buildSimpleTx() {
        TransactionInput input = new TransactionInput(TX_HASH, 0);
        TransactionOutput output = TransactionOutput.builder()
                .address("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y")
                .value(Value.builder().coin(BigInteger.valueOf(2000000)).build())
                .build();

        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(output))
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();
    }
}

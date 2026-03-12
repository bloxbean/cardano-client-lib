package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidityIntervalRuleTest {

    private ValidityIntervalRule rule;
    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";

    @BeforeEach
    void setUp() {
        rule = new ValidityIntervalRule();
    }

    @Test
    void validate_slotWithinInterval_shouldPass() {
        Transaction tx = buildTx(100, 500);
        LedgerContext ctx = LedgerContext.builder().currentSlot(200).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_slotBeforeStart_shouldFail() {
        Transaction tx = buildTx(200, 500);
        LedgerContext ctx = LedgerContext.builder().currentSlot(100).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("before validity start interval");
    }

    @Test
    void validate_slotAtTtl_shouldFail() {
        Transaction tx = buildTx(0, 500);
        LedgerContext ctx = LedgerContext.builder().currentSlot(500).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("at or past TTL");
    }

    @Test
    void validate_slotPastTtl_shouldFail() {
        Transaction tx = buildTx(0, 500);
        LedgerContext ctx = LedgerContext.builder().currentSlot(600).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("at or past TTL");
    }

    @Test
    void validate_noInterval_shouldPass() {
        // No validity start, no TTL (both 0)
        Transaction tx = buildTx(0, 0);
        LedgerContext ctx = LedgerContext.builder().currentSlot(1000).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_onlyTtl_shouldPass() {
        Transaction tx = buildTx(0, 1000);
        LedgerContext ctx = LedgerContext.builder().currentSlot(500).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_onlyStart_shouldPass() {
        Transaction tx = buildTx(100, 0);
        LedgerContext ctx = LedgerContext.builder().currentSlot(200).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_slotExactlyAtStart_shouldPass() {
        Transaction tx = buildTx(200, 500);
        LedgerContext ctx = LedgerContext.builder().currentSlot(200).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_allErrorsArePhase1() {
        Transaction tx = buildTx(300, 100); // start > ttl → both will fail
        LedgerContext ctx = LedgerContext.builder().currentSlot(200).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isNotEmpty();
        assertThat(errors).allMatch(e -> e.getPhase() == ValidationError.Phase.PHASE_1);
    }

    private Transaction buildTx(long validityStart, long ttl) {
        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of(TransactionOutput.builder()
                                .address("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y")
                                .value(Value.builder().coin(BigInteger.valueOf(2000000)).build())
                                .build()))
                        .fee(BigInteger.valueOf(200000))
                        .validityStartInterval(validityStart)
                        .ttl(ttl)
                        .build())
                .build();
    }
}

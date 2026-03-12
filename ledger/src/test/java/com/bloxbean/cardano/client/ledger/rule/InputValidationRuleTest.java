package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.SimpleUtxoSlice;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class InputValidationRuleTest {

    private InputValidationRule rule;
    private static final String TX_HASH_1 = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
    private static final String TX_HASH_2 = "11223344556677889900aabbccddeeff00112233445566778899aabbccddeeff";

    @BeforeEach
    void setUp() {
        rule = new InputValidationRule();
    }

    @Test
    void validate_emptyInputs_shouldFail() {
        Transaction tx = buildTx(Collections.emptyList(), null);
        LedgerContext ctx = contextWithUtxos(Collections.emptyMap());

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("no inputs");
    }

    @Test
    void validate_nullInputs_shouldFail() {
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder().inputs(null)
                        .outputs(List.of()).fee(BigInteger.ZERO).build())
                .build();
        LedgerContext ctx = contextWithUtxos(Collections.emptyMap());

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("no inputs");
    }

    @Test
    void validate_validInputs_shouldPass() {
        TransactionInput input1 = new TransactionInput(TX_HASH_1, 0);
        Transaction tx = buildTx(List.of(input1), null);

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input1, dummyOutput());

        LedgerContext ctx = contextWithUtxos(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_inputNotInUtxo_shouldFail() {
        TransactionInput input1 = new TransactionInput(TX_HASH_1, 0);
        Transaction tx = buildTx(List.of(input1), null);
        LedgerContext ctx = contextWithUtxos(Collections.emptyMap());

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("not found in UTxO set");
    }

    @Test
    void validate_duplicateInputs_shouldFail() {
        TransactionInput input1 = new TransactionInput(TX_HASH_1, 0);
        Transaction tx = buildTx(List.of(input1, input1), null);

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input1, dummyOutput());

        LedgerContext ctx = contextWithUtxos(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("duplicate spending inputs"));
    }

    @Test
    void validate_refInputsDisjoint_shouldPass() {
        TransactionInput input1 = new TransactionInput(TX_HASH_1, 0);
        TransactionInput refInput = new TransactionInput(TX_HASH_2, 0);
        Transaction tx = buildTx(List.of(input1), List.of(refInput));

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input1, dummyOutput());
        utxoMap.put(refInput, dummyOutput());

        LedgerContext ctx = contextWithUtxos(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_refInputsOverlap_shouldFail() {
        TransactionInput input1 = new TransactionInput(TX_HASH_1, 0);
        Transaction tx = buildTx(List.of(input1), List.of(input1));

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input1, dummyOutput());

        LedgerContext ctx = contextWithUtxos(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("overlap with spending inputs"));
    }

    @Test
    void validate_refInputNotInUtxo_shouldFail() {
        TransactionInput input1 = new TransactionInput(TX_HASH_1, 0);
        TransactionInput refInput = new TransactionInput(TX_HASH_2, 0);
        Transaction tx = buildTx(List.of(input1), List.of(refInput));

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input1, dummyOutput());
        // refInput NOT in utxoMap

        LedgerContext ctx = contextWithUtxos(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("Reference input not found"));
    }

    @Test
    void validate_duplicateRefInputs_shouldFail() {
        TransactionInput input1 = new TransactionInput(TX_HASH_1, 0);
        TransactionInput refInput = new TransactionInput(TX_HASH_2, 0);
        Transaction tx = buildTx(List.of(input1), List.of(refInput, refInput));

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input1, dummyOutput());
        utxoMap.put(refInput, dummyOutput());

        LedgerContext ctx = contextWithUtxos(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("duplicate reference inputs"));
    }

    @Test
    void validate_allErrorsArePhase1() {
        Transaction tx = buildTx(Collections.emptyList(), null);
        LedgerContext ctx = contextWithUtxos(Collections.emptyMap());

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).allMatch(e -> e.getPhase() == ValidationError.Phase.PHASE_1);
    }

    // Helpers

    private Transaction buildTx(List<TransactionInput> inputs, List<TransactionInput> refInputs) {
        TransactionBody.TransactionBodyBuilder bodyBuilder = TransactionBody.builder()
                .inputs(inputs)
                .outputs(List.of(dummyOutput()))
                .fee(BigInteger.valueOf(200000));

        if (refInputs != null) {
            bodyBuilder.referenceInputs(refInputs);
        }

        return Transaction.builder()
                .body(bodyBuilder.build())
                .build();
    }

    private LedgerContext contextWithUtxos(Map<TransactionInput, TransactionOutput> utxoMap) {
        return LedgerContext.builder()
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();
    }

    private TransactionOutput dummyOutput() {
        return TransactionOutput.builder()
                .address("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y")
                .value(Value.builder().coin(BigInteger.valueOf(2000000)).build())
                .build();
    }
}

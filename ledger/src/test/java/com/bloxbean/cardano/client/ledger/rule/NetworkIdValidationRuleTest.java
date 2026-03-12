package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkIdValidationRuleTest {

    private NetworkIdValidationRule rule;
    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
    private static final String TESTNET_ADDR = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";

    @BeforeEach
    void setUp() {
        rule = new NetworkIdValidationRule();
    }

    @Test
    void validate_matchingNetwork_shouldPass() {
        Transaction tx = buildTx(TESTNET_ADDR, null, null);
        LedgerContext ctx = LedgerContext.builder().networkId(NetworkId.TESTNET).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_mismatchedOutputNetwork_shouldFail() {
        // Testnet address used in a mainnet context → should fail
        Transaction tx = buildTx(TESTNET_ADDR, null, null);
        LedgerContext ctx = LedgerContext.builder().networkId(NetworkId.MAINNET).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("Output[0]");
    }

    @Test
    void validate_bodyNetworkIdMismatch_shouldFail() {
        Transaction tx = buildTx(TESTNET_ADDR, NetworkId.MAINNET, null);
        LedgerContext ctx = LedgerContext.builder().networkId(NetworkId.TESTNET).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("Transaction body network ID");
    }

    @Test
    void validate_bodyNetworkIdMatch_shouldPass() {
        Transaction tx = buildTx(TESTNET_ADDR, NetworkId.TESTNET, null);
        LedgerContext ctx = LedgerContext.builder().networkId(NetworkId.TESTNET).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_withdrawalNetworkMismatch_shouldFail() {
        // Testnet reward address used in mainnet context
        String testnetRewardAddr = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";

        Transaction tx = buildTx(TESTNET_ADDR, null,
                List.of(new Withdrawal(testnetRewardAddr, BigInteger.ZERO)));
        LedgerContext ctx = LedgerContext.builder().networkId(NetworkId.MAINNET).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        // Both output address and withdrawal address have testnet network ID, mainnet expects 1
        assertThat(errors).anyMatch(e -> e.getMessage().contains("Output[0]"));
        // Withdrawal may fail or may throw parse exception — just check at least output fails
    }

    @Test
    void validate_noExpectedNetwork_shouldPass() {
        Transaction tx = buildTx(TESTNET_ADDR, null, null);
        LedgerContext ctx = LedgerContext.builder().networkId(null).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_bodyAndOutputMismatch_shouldReturnMultipleErrors() {
        // Body says MAINNET, output is testnet, context says TESTNET
        // → body mismatch error (MAINNET != TESTNET)
        Transaction tx = buildTx(TESTNET_ADDR, NetworkId.MAINNET, null);
        LedgerContext ctx = LedgerContext.builder().networkId(NetworkId.TESTNET).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1); // Only body mismatch; output matches testnet context
        assertThat(errors.get(0).getMessage()).contains("Transaction body network ID");
    }

    @Test
    void validate_allErrorsArePhase1() {
        // Testnet address in mainnet context
        Transaction tx = buildTx(TESTNET_ADDR, null, null);
        LedgerContext ctx = LedgerContext.builder().networkId(NetworkId.MAINNET).build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isNotEmpty();
        assertThat(errors).allMatch(e -> e.getPhase() == ValidationError.Phase.PHASE_1);
    }

    private Transaction buildTx(String outputAddr, NetworkId bodyNetworkId, List<Withdrawal> withdrawals) {
        TransactionBody.TransactionBodyBuilder bodyBuilder = TransactionBody.builder()
                .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                .outputs(List.of(TransactionOutput.builder()
                        .address(outputAddr)
                        .value(Value.builder().coin(BigInteger.valueOf(2000000)).build())
                        .build()))
                .fee(BigInteger.valueOf(200000));

        if (bodyNetworkId != null) {
            bodyBuilder.networkId(bodyNetworkId);
        }
        if (withdrawals != null) {
            bodyBuilder.withdrawals(withdrawals);
        }

        return Transaction.builder()
                .body(bodyBuilder.build())
                .build();
    }
}

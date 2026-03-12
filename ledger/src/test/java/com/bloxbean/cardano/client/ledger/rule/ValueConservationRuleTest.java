package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.SimpleUtxoSlice;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ValueConservationRuleTest {

    private ValueConservationRule rule;

    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
    private static final String TX_HASH_2 = "11223344556677889900aabbccddeeff00112233445566778899aabbccddeeff";
    private static final String POLICY_ID = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef01";
    private static final String TEST_ADDR = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";

    @BeforeEach
    void setUp() {
        rule = new ValueConservationRule();
    }

    @Test
    void validate_balancedTx_shouldPass() {
        // Input: 10 ADA, Output: 9.8 ADA, Fee: 0.2 ADA
        TransactionInput input = new TransactionInput(TX_HASH, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(10000000));

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(9800000)))
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        LedgerContext ctx = context(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_unbalancedTx_shouldFail() {
        // Input: 10 ADA, Output: 9 ADA, Fee: 0.2 ADA (0.8 ADA missing from produced)
        TransactionInput input = new TransactionInput(TX_HASH, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(10000000));

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(9000000)))
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        LedgerContext ctx = context(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("not conserved");
    }

    @Test
    void validate_multipleInputsAndOutputs_balanced() {
        TransactionInput input1 = new TransactionInput(TX_HASH, 0);
        TransactionInput input2 = new TransactionInput(TX_HASH_2, 1);

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input1, outputWithCoin(5000000));
        utxoMap.put(input2, outputWithCoin(3000000));

        // 8M in, 4M + 3.8M out + 0.2M fee = 8M
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input1, input2))
                        .outputs(List.of(
                                outputWithCoin(4000000),
                                outputWithCoin(3800000)))
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        LedgerContext ctx = context(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_withMintAndBurn_balanced() {
        TransactionInput input = new TransactionInput(TX_HASH, 0);

        // Input has 10 ADA + 50 token1 + 10 token2
        Asset inputAsset1 = Asset.builder().name("token1").value(BigInteger.valueOf(50)).build();
        Asset inputAsset2 = Asset.builder().name("token2").value(BigInteger.valueOf(10)).build();
        MultiAsset inputMA = MultiAsset.builder()
                .policyId(POLICY_ID).assets(List.of(inputAsset1, inputAsset2)).build();
        TransactionOutput inputUtxo = TransactionOutput.builder()
                .address(TEST_ADDR)
                .value(new Value(BigInteger.valueOf(10000000), List.of(inputMA)))
                .build();

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, inputUtxo);

        // Mint +20 token1, burn -10 token2
        Asset mintAsset = Asset.builder().name("token1").value(BigInteger.valueOf(20)).build();
        Asset burnAsset = Asset.builder().name("token2").value(BigInteger.valueOf(-10)).build();
        MultiAsset mint = MultiAsset.builder()
                .policyId(POLICY_ID).assets(List.of(mintAsset, burnAsset)).build();

        // Consumed: 10M ADA + {token1: 50, token2: 10} (inputs) + {token1: 20} (positive mint)
        //         = 10M ADA + {token1: 70, token2: 10}
        // Produced: 9.8M ADA + {token1: 70} (outputs) + {token2: 10} (negated burn) + 0.2M (fee)
        //         = 10M ADA + {token1: 70, token2: 10}
        Asset outputAsset = Asset.builder().name("token1").value(BigInteger.valueOf(70)).build();
        MultiAsset outputMA = MultiAsset.builder()
                .policyId(POLICY_ID).assets(List.of(outputAsset)).build();
        TransactionOutput txOutput = TransactionOutput.builder()
                .address(TEST_ADDR)
                .value(new Value(BigInteger.valueOf(9800000), List.of(outputMA)))
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(txOutput))
                        .fee(BigInteger.valueOf(200000))
                        .mint(List.of(mint))
                        .build())
                .build();

        LedgerContext ctx = context(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_withWithdrawals_balanced() {
        TransactionInput input = new TransactionInput(TX_HASH, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(5000000));

        Withdrawal w = Withdrawal.builder()
                .rewardAddress("stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27")
                .coin(BigInteger.valueOf(1500000))
                .build();

        // 5M input + 1.5M withdrawal = 6.3M output + 0.2M fee
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(6300000)))
                        .fee(BigInteger.valueOf(200000))
                        .withdrawals(List.of(w))
                        .build())
                .build();

        LedgerContext ctx = context(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_withDeposits_balanced() {
        TransactionInput input = new TransactionInput(TX_HASH, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(12200000));

        StakeRegistration reg = new StakeRegistration(StakeCredential.fromKeyHash(new byte[]{(byte)0xab, (byte)0xcd, (byte)0xef}));

        // 12.2M input = 10M output + 0.2M fee + 2M stake deposit
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(10000000)))
                        .fee(BigInteger.valueOf(200000))
                        .certs(List.of(reg))
                        .build())
                .build();

        LedgerContext ctx = context(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_missingInput_shouldSkip() {
        // Missing input → consumed returns null → rule skips (InputValidationRule handles this)
        TransactionInput input = new TransactionInput(TX_HASH, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        // NOT adding input to utxoMap

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(9800000)))
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        LedgerContext ctx = context(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty(); // Skipped, not errored
    }

    @Test
    void validate_noProtocolParams_shouldSkip() {
        TransactionInput input = new TransactionInput(TX_HASH, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(10000000));

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(9800000)))
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(null)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_allErrorsArePhase1() {
        TransactionInput input = new TransactionInput(TX_HASH, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(10000000));

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(5000000))) // unbalanced
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        LedgerContext ctx = context(utxoMap);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).allMatch(e -> e.getPhase() == ValidationError.Phase.PHASE_1);
    }

    // --- Helpers ---

    private LedgerContext context(Map<TransactionInput, TransactionOutput> utxoMap) {
        ProtocolParams pp = new ProtocolParams();
        pp.setKeyDeposit("2000000");
        pp.setPoolDeposit("500000000");
        pp.setMinFeeA(44);
        pp.setMinFeeB(155381);

        return LedgerContext.builder()
                .protocolParams(pp)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();
    }

    private TransactionOutput outputWithCoin(long lovelace) {
        return TransactionOutput.builder()
                .address(TEST_ADDR)
                .value(Value.builder().coin(BigInteger.valueOf(lovelace)).build())
                .build();
    }
}

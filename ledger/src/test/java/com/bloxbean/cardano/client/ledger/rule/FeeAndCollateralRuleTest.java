package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.SimpleUtxoSlice;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class FeeAndCollateralRuleTest {

    private FeeAndCollateralRule rule;

    private static final String TX_HASH_1 = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
    private static final String TX_HASH_2 = "11223344556677889900aabbccddeeff00112233445566778899aabbccddeeff";
    private static final String TX_HASH_3 = "ff223344556677889900aabbccddeeff00112233445566778899aabbccddeeff";
    private static final String VKEY_ADDR = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";

    @BeforeEach
    void setUp() {
        rule = new FeeAndCollateralRule();
    }

    @Test
    void validate_feeAboveMinimum_shouldPass() {
        Transaction tx = simpleTxWithFee(BigInteger.valueOf(500000));
        LedgerContext ctx = contextWithParams(defaultPP());

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_feeBelowMinimum_shouldFail() {
        // Fee of 1 lovelace should be below min fee
        Transaction tx = simpleTxWithFee(BigInteger.ONE);
        LedgerContext ctx = contextWithParams(defaultPP());

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("below minimum"));
    }

    @Test
    void validate_nullFee_shouldFail() {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(null)
                        .build())
                .build();

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(5000000));

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(defaultPP())
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        // Either "below minimum" or "Failed to compute" (if serialization fails with null fee)
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e ->
                e.getMessage().contains("below minimum") || e.getMessage().contains("Failed to compute"));
    }

    @Test
    void validate_noCollateralWithRedeemers_shouldFail() {
        Transaction tx = txWithRedeemers(BigInteger.valueOf(500000), null);
        LedgerContext ctx = contextWithParams(defaultPP());

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("No collateral inputs"));
    }

    @Test
    void validate_collateralExceedsMax_shouldFail() {
        ProtocolParams pp = defaultPP();
        pp.setMaxCollateralInputs(1);

        TransactionInput col1 = new TransactionInput(TX_HASH_2, 0);
        TransactionInput col2 = new TransactionInput(TX_HASH_3, 0);

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(new TransactionInput(TX_HASH_1, 0), outputWithCoin(5000000));
        utxoMap.put(col1, outputWithCoin(2000000));
        utxoMap.put(col2, outputWithCoin(2000000));

        Transaction tx = txWithCollateral(
                BigInteger.valueOf(500000),
                List.of(col1, col2),
                null);

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(pp)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("exceeds max"));
    }

    @Test
    void validate_insufficientCollateral_shouldFail() {
        ProtocolParams pp = defaultPP();
        pp.setCollateralPercent(BigDecimal.valueOf(150));

        TransactionInput col = new TransactionInput(TX_HASH_2, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(new TransactionInput(TX_HASH_1, 0), outputWithCoin(5000000));
        utxoMap.put(col, outputWithCoin(100)); // very small collateral

        Transaction tx = txWithCollateral(
                BigInteger.valueOf(500000), // fee
                List.of(col),
                null);

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(pp)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("Insufficient collateral"));
    }

    @Test
    void validate_sufficientCollateral_shouldPass() {
        ProtocolParams pp = defaultPP();
        pp.setCollateralPercent(BigDecimal.valueOf(150));

        TransactionInput col = new TransactionInput(TX_HASH_2, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(new TransactionInput(TX_HASH_1, 0), outputWithCoin(5000000));
        utxoMap.put(col, outputWithCoin(10000000)); // ample collateral

        Transaction tx = txWithCollateral(
                BigInteger.valueOf(200000), // small fee
                List.of(col),
                null);

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(pp)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        // Should only have fee-related errors (if any), not collateral errors
        assertThat(errors).noneMatch(e -> e.getMessage().contains("collateral"));
    }

    @Test
    void validate_totalCollateralMismatch_shouldFail() {
        ProtocolParams pp = defaultPP();
        pp.setCollateralPercent(BigDecimal.valueOf(150));

        TransactionInput col = new TransactionInput(TX_HASH_2, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(new TransactionInput(TX_HASH_1, 0), outputWithCoin(5000000));
        utxoMap.put(col, outputWithCoin(10000000));

        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(100000))
                        .steps(BigInteger.valueOf(100000000))
                        .build())
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(BigInteger.valueOf(500000))
                        .collateral(List.of(col))
                        .totalCollateral(BigInteger.valueOf(99999)) // wrong
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(pp)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("totalCollateral"));
    }

    @Test
    void validate_collateralNotInUtxo_shouldFail() {
        ProtocolParams pp = defaultPP();
        pp.setCollateralPercent(BigDecimal.valueOf(150));

        TransactionInput col = new TransactionInput(TX_HASH_2, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(new TransactionInput(TX_HASH_1, 0), outputWithCoin(5000000));
        // col NOT in utxoMap

        Transaction tx = txWithCollateral(
                BigInteger.valueOf(500000),
                List.of(col),
                null);

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(pp)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("not found in UTxO"));
    }

    @Test
    void validate_noRedeemerNoCollateralNeeded_shouldPass() {
        // No redeemers = no collateral checks
        Transaction tx = simpleTxWithFee(BigInteger.valueOf(500000));
        LedgerContext ctx = contextWithParams(defaultPP());

        List<ValidationError> errors = rule.validate(ctx, tx);
        // Only fee check matters, no collateral errors
        assertThat(errors).noneMatch(e -> e.getMessage().contains("collateral"));
    }

    @Test
    void validate_collateralReturnSubtracted() {
        ProtocolParams pp = defaultPP();
        pp.setCollateralPercent(BigDecimal.valueOf(150));

        TransactionInput col = new TransactionInput(TX_HASH_2, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(new TransactionInput(TX_HASH_1, 0), outputWithCoin(5000000));
        utxoMap.put(col, outputWithCoin(10000000));

        TransactionOutput collReturn = outputWithCoin(9000000); // 10M - 9M = 1M net

        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(100000))
                        .steps(BigInteger.valueOf(100000000))
                        .build())
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(BigInteger.valueOf(500000))
                        .collateral(List.of(col))
                        .collateralReturn(collReturn)
                        .totalCollateral(BigInteger.valueOf(1000000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(pp)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        // totalCollateral=1M, net=10M-9M=1M, so should match
        assertThat(errors).noneMatch(e -> e.getMessage().contains("totalCollateral"));
    }

    @Test
    void validate_allErrorsArePhase1() {
        Transaction tx = simpleTxWithFee(BigInteger.ONE);
        LedgerContext ctx = contextWithParams(defaultPP());

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).allMatch(e -> e.getPhase() == ValidationError.Phase.PHASE_1);
    }

    // ---- ExUnitsTooBigUTxO ----

    @Test
    void validate_exUnitsWithinLimits_shouldPass() {
        ProtocolParams pp = defaultPP();
        pp.setMaxTxExMem("10000000");
        pp.setMaxTxExSteps("10000000000");

        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(5000000))
                        .steps(BigInteger.valueOf(5000000000L))
                        .build())
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(BigInteger.valueOf(500000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();

        LedgerContext ctx = contextWithParams(pp);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).noneMatch(e -> e.getMessage().contains("ExUnits"));
    }

    @Test
    void validate_exUnitsMemExceedsMax_shouldFail() {
        ProtocolParams pp = defaultPP();
        pp.setMaxTxExMem("1000000");
        pp.setMaxTxExSteps("10000000000");

        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(5000000)) // exceeds 1000000
                        .steps(BigInteger.valueOf(100000))
                        .build())
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(BigInteger.valueOf(500000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();

        LedgerContext ctx = contextWithParams(pp);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("ExUnits memory") && e.getMessage().contains("exceeds max"));
    }

    @Test
    void validate_exUnitsStepsExceedsMax_shouldFail() {
        ProtocolParams pp = defaultPP();
        pp.setMaxTxExMem("10000000");
        pp.setMaxTxExSteps("1000000");

        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(100000))
                        .steps(BigInteger.valueOf(5000000)) // exceeds 1000000
                        .build())
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(BigInteger.valueOf(500000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();

        LedgerContext ctx = contextWithParams(pp);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("ExUnits steps") && e.getMessage().contains("exceeds max"));
    }

    @Test
    void validate_multipleRedeemersExUnitsSummed() {
        ProtocolParams pp = defaultPP();
        pp.setMaxTxExMem("1000000");
        pp.setMaxTxExSteps("10000000000");

        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        // Two redeemers: 600000 + 500000 = 1100000 > 1000000
        Redeemer r1 = Redeemer.builder()
                .tag(RedeemerTag.Spend).index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder().mem(BigInteger.valueOf(600000)).steps(BigInteger.valueOf(1000)).build())
                .build();
        Redeemer r2 = Redeemer.builder()
                .tag(RedeemerTag.Mint).index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder().mem(BigInteger.valueOf(500000)).steps(BigInteger.valueOf(1000)).build())
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(BigInteger.valueOf(500000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(r1, r2))
                        .build())
                .build();

        LedgerContext ctx = contextWithParams(pp);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("ExUnits memory") && e.getMessage().contains("1100000"));
    }

    @Test
    void validate_noRedeemers_exUnitsCheckSkipped() {
        ProtocolParams pp = defaultPP();
        pp.setMaxTxExMem("1000000");
        pp.setMaxTxExSteps("1000000");

        Transaction tx = simpleTxWithFee(BigInteger.valueOf(500000));
        LedgerContext ctx = contextWithParams(pp);
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).noneMatch(e -> e.getMessage().contains("ExUnits"));
    }

    // ---- ConwayTxRefScriptsSizeTooBig ----

    @Test
    void validate_refScriptSizeWithinLimit_shouldPass() {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        TransactionInput refInput = new TransactionInput(TX_HASH_2, 0);

        byte[] smallScript = new byte[1000]; // 1 KB — well within 200 KB limit
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(5000000));
        utxoMap.put(refInput, TransactionOutput.builder()
                .address(VKEY_ADDR)
                .value(Value.builder().coin(BigInteger.valueOf(5000000)).build())
                .scriptRef(smallScript)
                .build());

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(BigInteger.valueOf(500000))
                        .referenceInputs(List.of(refInput))
                        .build())
                .build();

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(defaultPP())
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).noneMatch(e -> e.getMessage().contains("reference script size"));
    }

    @Test
    void validate_refScriptSizeExceedsLimit_shouldFail() {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        TransactionInput refInput = new TransactionInput(TX_HASH_2, 0);

        byte[] largeScript = new byte[210000]; // 210 KB > 200 KB limit
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(5000000));
        utxoMap.put(refInput, TransactionOutput.builder()
                .address(VKEY_ADDR)
                .value(Value.builder().coin(BigInteger.valueOf(5000000)).build())
                .scriptRef(largeScript)
                .build());

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(BigInteger.valueOf(500000))
                        .referenceInputs(List.of(refInput))
                        .build())
                .build();

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(defaultPP())
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("reference script size")
                && e.getMessage().contains("exceeds maximum"));
    }

    @Test
    void validate_multipleRefScriptsSummed() {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        TransactionInput refInput1 = new TransactionInput(TX_HASH_2, 0);
        TransactionInput refInput2 = new TransactionInput(TX_HASH_3, 0);

        byte[] script1 = new byte[120000]; // 120 KB
        byte[] script2 = new byte[100000]; // 100 KB — total 220 KB > 200 KB
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(5000000));
        utxoMap.put(refInput1, TransactionOutput.builder()
                .address(VKEY_ADDR)
                .value(Value.builder().coin(BigInteger.valueOf(5000000)).build())
                .scriptRef(script1).build());
        utxoMap.put(refInput2, TransactionOutput.builder()
                .address(VKEY_ADDR)
                .value(Value.builder().coin(BigInteger.valueOf(5000000)).build())
                .scriptRef(script2).build());

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(BigInteger.valueOf(500000))
                        .referenceInputs(List.of(refInput1, refInput2))
                        .build())
                .build();

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(defaultPP())
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("reference script size")
                && e.getMessage().contains("220000"));
    }

    @Test
    void validate_noRefInputs_refScriptCheckSkipped() {
        Transaction tx = simpleTxWithFee(BigInteger.valueOf(500000));
        LedgerContext ctx = contextWithParams(defaultPP());
        List<ValidationError> errors = rule.validate(ctx, tx);
        assertThat(errors).noneMatch(e -> e.getMessage().contains("reference script"));
    }

    // --- Helpers ---

    private ProtocolParams defaultPP() {
        ProtocolParams pp = new ProtocolParams();
        pp.setMinFeeA(44);
        pp.setMinFeeB(155381);
        pp.setPriceMem(BigDecimal.ZERO);
        pp.setPriceStep(BigDecimal.ZERO);
        pp.setMaxCollateralInputs(3);
        pp.setCollateralPercent(BigDecimal.valueOf(150));
        return pp;
    }

    private LedgerContext contextWithParams(ProtocolParams pp) {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(5000000));

        return LedgerContext.builder()
                .protocolParams(pp)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();
    }

    private Transaction simpleTxWithFee(BigInteger fee) {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(2000000)))
                        .fee(fee)
                        .build())
                .build();
    }

    private Transaction txWithRedeemers(BigInteger fee, List<TransactionInput> collateral) {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(100000))
                        .steps(BigInteger.valueOf(100000000))
                        .build())
                .build();

        TransactionBody.TransactionBodyBuilder bodyBuilder = TransactionBody.builder()
                .inputs(List.of(input))
                .outputs(List.of(outputWithCoin(2000000)))
                .fee(fee);

        if (collateral != null) {
            bodyBuilder.collateral(collateral);
        }

        return Transaction.builder()
                .body(bodyBuilder.build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();
    }

    private Transaction txWithCollateral(BigInteger fee, List<TransactionInput> collateral,
                                          TransactionOutput collateralReturn) {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(100000))
                        .steps(BigInteger.valueOf(100000000))
                        .build())
                .build();

        TransactionBody.TransactionBodyBuilder bodyBuilder = TransactionBody.builder()
                .inputs(List.of(input))
                .outputs(List.of(outputWithCoin(2000000)))
                .fee(fee)
                .collateral(collateral);

        if (collateralReturn != null) {
            bodyBuilder.collateralReturn(collateralReturn);
        }

        return Transaction.builder()
                .body(bodyBuilder.build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();
    }

    private TransactionOutput outputWithCoin(long lovelace) {
        return TransactionOutput.builder()
                .address(VKEY_ADDR)
                .value(Value.builder().coin(BigInteger.valueOf(lovelace)).build())
                .build();
    }
}

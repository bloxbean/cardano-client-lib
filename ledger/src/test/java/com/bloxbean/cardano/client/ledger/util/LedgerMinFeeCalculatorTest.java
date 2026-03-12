package com.bloxbean.cardano.client.ledger.util;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.SimpleUtxoSlice;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerMinFeeCalculatorTest {

    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";

    @Test
    void calculateTransactionSizeFee_basic() {
        ProtocolParams pp = defaultPP();
        Transaction tx = simpleTx();

        BigInteger fee = LedgerMinFeeCalculator.calculateTransactionSizeFee(pp, tx);

        // fee = txSize * minFeeA + minFeeB
        // We just verify it's positive and consistent
        assertThat(fee).isPositive();
    }

    @Test
    void calculateTransactionSizeFee_zeroFeeParams() {
        ProtocolParams pp = defaultPP();
        pp.setMinFeeA(0);
        pp.setMinFeeB(0);
        Transaction tx = simpleTx();

        BigInteger fee = LedgerMinFeeCalculator.calculateTransactionSizeFee(pp, tx);
        assertThat(fee).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void calculateExUnitsFee_noRedeemers() {
        ProtocolParams pp = defaultPP();
        Transaction tx = simpleTx();

        BigInteger fee = LedgerMinFeeCalculator.calculateExUnitsFee(pp, tx);
        assertThat(fee).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void calculateExUnitsFee_withRedeemers() {
        ProtocolParams pp = defaultPP();
        pp.setPriceMem(new BigDecimal("0.0577"));
        pp.setPriceStep(new BigDecimal("0.0000721"));

        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(1000000))
                        .steps(BigInteger.valueOf(500000000))
                        .build())
                .build();

        Transaction tx = simpleTx();
        tx.setWitnessSet(TransactionWitnessSet.builder()
                .redeemers(List.of(redeemer))
                .build());

        BigInteger fee = LedgerMinFeeCalculator.calculateExUnitsFee(pp, tx);

        // fee = ceil(0.0577 * 1000000 + 0.0000721 * 500000000)
        //     = ceil(57700 + 36050) = ceil(93750) = 93750
        assertThat(fee).isEqualTo(BigInteger.valueOf(93750));
    }

    @Test
    void calculateExUnitsFee_multipleRedeemers() {
        ProtocolParams pp = defaultPP();
        pp.setPriceMem(new BigDecimal("0.0577"));
        pp.setPriceStep(new BigDecimal("0.0000721"));

        Redeemer r1 = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(500000))
                        .steps(BigInteger.valueOf(250000000))
                        .build())
                .build();

        Redeemer r2 = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ONE)
                .data(PlutusData.unit())
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(500000))
                        .steps(BigInteger.valueOf(250000000))
                        .build())
                .build();

        Transaction tx = simpleTx();
        tx.setWitnessSet(TransactionWitnessSet.builder()
                .redeemers(List.of(r1, r2))
                .build());

        BigInteger fee = LedgerMinFeeCalculator.calculateExUnitsFee(pp, tx);

        // Same total as single redeemer test: mem=1M, steps=500M
        assertThat(fee).isEqualTo(BigInteger.valueOf(93750));
    }

    @Test
    void calculateRefScriptsFee_noRefScripts() {
        ProtocolParams pp = defaultPP();
        pp.setMinFeeRefScriptCostPerByte(new BigDecimal("15"));
        Transaction tx = simpleTx();
        SimpleUtxoSlice slice = new SimpleUtxoSlice(Collections.emptyMap());

        BigInteger fee = LedgerMinFeeCalculator.calculateRefScriptsFee(pp, slice, tx);
        assertThat(fee).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void calculateRefScriptsFee_nullParam() {
        ProtocolParams pp = defaultPP();
        pp.setMinFeeRefScriptCostPerByte(null);
        Transaction tx = simpleTx();
        SimpleUtxoSlice slice = new SimpleUtxoSlice(Collections.emptyMap());

        BigInteger fee = LedgerMinFeeCalculator.calculateRefScriptsFee(pp, slice, tx);
        assertThat(fee).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void tierRefScriptFee_singleTier() {
        // 10000 bytes at base price 15 per byte, single tier (< 25600)
        BigInteger fee = LedgerMinFeeCalculator.tierRefScriptFee(
                new BigDecimal("1.2"), 25600,
                new BigDecimal("15"), 10000);

        // 10000 * 15 = 150000
        assertThat(fee).isEqualTo(BigInteger.valueOf(150000));
    }

    @Test
    void tierRefScriptFee_twoTiers() {
        // 30000 bytes: first tier 25600 * 15, second tier 4400 * (15 * 1.2)
        BigInteger fee = LedgerMinFeeCalculator.tierRefScriptFee(
                new BigDecimal("1.2"), 25600,
                new BigDecimal("15"), 30000);

        // tier1 = 25600 * 15 = 384000
        // tier2 = 4400 * 18 = 79200
        // total = 463200
        assertThat(fee).isEqualTo(BigInteger.valueOf(463200));
    }

    @Test
    void tierRefScriptFee_zeroBytes() {
        BigInteger fee = LedgerMinFeeCalculator.tierRefScriptFee(
                new BigDecimal("1.2"), 25600,
                new BigDecimal("15"), 0);
        assertThat(fee).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void computeMinFee_allComponents() {
        ProtocolParams pp = defaultPP();
        pp.setPriceMem(new BigDecimal("0.0577"));
        pp.setPriceStep(new BigDecimal("0.0000721"));
        pp.setMinFeeRefScriptCostPerByte(new BigDecimal("15"));

        TransactionInput input = new TransactionInput(TX_HASH, 0);
        TransactionOutput output = TransactionOutput.builder()
                .address("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y")
                .value(Value.builder().coin(BigInteger.valueOf(5000000)).build())
                .build();

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, output);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(output))
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        LedgerContext ctx = LedgerContext.builder()
                .protocolParams(pp)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();

        BigInteger minFee = LedgerMinFeeCalculator.computeMinFee(ctx, tx);

        // Should be size fee only (no redeemers, no ref scripts)
        BigInteger expectedSizeFee = LedgerMinFeeCalculator.calculateTransactionSizeFee(pp, tx);
        assertThat(minFee).isEqualTo(expectedSizeFee);
    }

    // --- Helpers ---

    private ProtocolParams defaultPP() {
        ProtocolParams pp = new ProtocolParams();
        pp.setMinFeeA(44);
        pp.setMinFeeB(155381);
        pp.setPriceMem(BigDecimal.ZERO);
        pp.setPriceStep(BigDecimal.ZERO);
        return pp;
    }

    private Transaction simpleTx() {
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

package com.bloxbean.cardano.client.ledger.util;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.UtxoSlice;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/**
 * Computes the Conway-era minimum transaction fee with three components:
 * <ol>
 *   <li>Transaction size fee: txFeeFixed + txSize * txFeePerByte</li>
 *   <li>Execution units fee: ceil(priceMem * totalMem + priceStep * totalSteps)</li>
 *   <li>Reference script tiered fee: exponential tiers (multiplier 1.2, stride 25600 bytes)</li>
 * </ol>
 *
 * Reference: Scalus MinTransactionFee.scala, Haskell Conway.Rules.Utxo
 */
public class LedgerMinFeeCalculator {

    private static final BigDecimal REF_SCRIPT_COST_MULTIPLIER = new BigDecimal("1.2");
    private static final int REF_SCRIPT_COST_STRIDE = 25600; // 25 KB per tier

    private LedgerMinFeeCalculator() {}

    /**
     * Compute the minimum transaction fee.
     *
     * @param context     ledger context with protocol params and UTxO slice
     * @param transaction the transaction
     * @return minimum fee in lovelace
     */
    public static BigInteger computeMinFee(LedgerContext context, Transaction transaction) {
        ProtocolParams pp = context.getProtocolParams();

        BigInteger sizeFee = calculateTransactionSizeFee(pp, transaction);
        BigInteger exUnitsFee = calculateExUnitsFee(pp, transaction);
        BigInteger refScriptFee = calculateRefScriptsFee(pp, context.getUtxoSlice(), transaction);

        return sizeFee.add(exUnitsFee).add(refScriptFee);
    }

    /**
     * fee = txSize * txFeePerByte + txFeeFixed
     */
    static BigInteger calculateTransactionSizeFee(ProtocolParams pp, Transaction transaction) {
        int txFeePerByte = pp.getMinFeeA() != null ? pp.getMinFeeA() : 0;
        int txFeeFixed = pp.getMinFeeB() != null ? pp.getMinFeeB() : 0;

        int txSize;
        try {
            txSize = transaction.serialize().length;
        } catch (CborSerializationException e) {
            throw new CborRuntimeException("Failed to serialize transaction for fee calculation", e);
        }

        return BigInteger.valueOf((long) txSize * txFeePerByte + txFeeFixed);
    }

    /**
     * exUnitsFee = ceil(priceMem * totalMem + priceStep * totalSteps)
     */
    static BigInteger calculateExUnitsFee(ProtocolParams pp, Transaction transaction) {
        if (transaction.getWitnessSet() == null
                || transaction.getWitnessSet().getRedeemers() == null
                || transaction.getWitnessSet().getRedeemers().isEmpty()) {
            return BigInteger.ZERO;
        }

        BigDecimal priceMem = pp.getPriceMem() != null ? pp.getPriceMem() : BigDecimal.ZERO;
        BigDecimal priceStep = pp.getPriceStep() != null ? pp.getPriceStep() : BigDecimal.ZERO;

        BigInteger totalMem = BigInteger.ZERO;
        BigInteger totalSteps = BigInteger.ZERO;

        for (Redeemer redeemer : transaction.getWitnessSet().getRedeemers()) {
            ExUnits exUnits = redeemer.getExUnits();
            if (exUnits != null) {
                if (exUnits.getMem() != null) totalMem = totalMem.add(exUnits.getMem());
                if (exUnits.getSteps() != null) totalSteps = totalSteps.add(exUnits.getSteps());
            }
        }

        BigDecimal fee = priceMem.multiply(new BigDecimal(totalMem))
                .add(priceStep.multiply(new BigDecimal(totalSteps)));

        // Ceiling
        return fee.setScale(0, RoundingMode.CEILING).toBigInteger();
    }

    /**
     * Reference script tiered fee.
     * Each tier of 25600 bytes costs 1.2x more per byte than the previous tier.
     * Base price is minFeeRefScriptCostPerByte.
     */
    static BigInteger calculateRefScriptsFee(ProtocolParams pp, UtxoSlice utxoSlice, Transaction transaction) {
        if (pp.getMinFeeRefScriptCostPerByte() == null || utxoSlice == null) {
            return BigInteger.ZERO;
        }

        int totalRefScriptSize = computeTotalRefScriptSize(utxoSlice, transaction);
        if (totalRefScriptSize == 0) {
            return BigInteger.ZERO;
        }

        BigDecimal basePricePerByte = pp.getMinFeeRefScriptCostPerByte();
        return tierRefScriptFee(REF_SCRIPT_COST_MULTIPLIER, REF_SCRIPT_COST_STRIDE,
                basePricePerByte, totalRefScriptSize);
    }

    /**
     * Tiered ref-script fee algorithm from the Conway spec.
     * Each stride of bytes costs more by the multiplier factor.
     */
    static BigInteger tierRefScriptFee(BigDecimal multiplier, int stride,
                                       BigDecimal curTierPrice, int totalBytes) {
        BigDecimal acc = BigDecimal.ZERO;
        int remaining = totalBytes;

        while (remaining >= stride) {
            acc = acc.add(curTierPrice.multiply(BigDecimal.valueOf(stride)));
            curTierPrice = curTierPrice.multiply(multiplier);
            remaining -= stride;
        }

        // Last partial tier
        if (remaining > 0) {
            acc = acc.add(curTierPrice.multiply(BigDecimal.valueOf(remaining)));
        }

        return acc.setScale(0, RoundingMode.FLOOR).toBigInteger();
    }

    /**
     * Collect total size of reference scripts from all spending and reference inputs.
     * Non-distinct: same script at two inputs counts twice per the spec.
     */
    private static int computeTotalRefScriptSize(UtxoSlice utxoSlice, Transaction transaction) {
        int totalSize = 0;

        // Spending inputs
        List<TransactionInput> inputs = transaction.getBody().getInputs();
        if (inputs != null) {
            for (TransactionInput input : inputs) {
                totalSize += getScriptRefSize(utxoSlice, input);
            }
        }

        // Reference inputs
        List<TransactionInput> refInputs = transaction.getBody().getReferenceInputs();
        if (refInputs != null) {
            for (TransactionInput input : refInputs) {
                totalSize += getScriptRefSize(utxoSlice, input);
            }
        }

        return totalSize;
    }

    private static int getScriptRefSize(UtxoSlice utxoSlice, TransactionInput input) {
        return utxoSlice.lookup(input)
                .map(TransactionOutput::getScriptRef)
                .map(scriptRef -> scriptRef.length)
                .orElse(0);
    }
}

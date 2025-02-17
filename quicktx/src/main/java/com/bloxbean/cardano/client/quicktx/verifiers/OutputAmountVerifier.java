package com.bloxbean.cardano.client.quicktx.verifiers;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.quicktx.Verifier;
import com.bloxbean.cardano.client.quicktx.VerifierException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.Tuple;

import java.math.BigInteger;
import java.util.function.Predicate;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Verifies the output amount for a given address
 */
public class OutputAmountVerifier implements Verifier {

    private final String address;
    private final Amount amount;
    private final Predicate<Amount> predicate;
    private final String unit;
    private final String customMsg;

    /**
     * Verifies the output amount for a given address
     * @param address address to verify
     * @param amount amount to verify
     * @param customMsg custom message to throw in case of verification failure
     */
    public OutputAmountVerifier(String address, Amount amount, String customMsg) {
        this.address = address;
        this.amount = amount;
        this.unit = amount.getUnit();
        this.predicate = null;
        this.customMsg = customMsg;
    }

    /**
     * Constructor to create an OutputAmountVerifier instance with a specific unit and predicate for validation.
     * This verifier is used to verify the output amount for a given address.
     *
     * @param address the address for which the output amount is to be verified
     * @param unit the unit of the amount to be verified
     * @param predicate a predicate to define the condition for amount verification
     * @param customMsg a custom message to throw in case of verification failure
     */
    public OutputAmountVerifier(String address, String unit, Predicate<Amount> predicate, String customMsg) {
        this.address = address;
        this.amount = null;
        this.unit = unit;
        this.predicate = predicate;
        this.customMsg = customMsg;
    }

    @Override
    public void verify(Transaction txn) throws VerifierException {
        if (LOVELACE.equals(unit)) {
            BigInteger lovelaceAmt = txn.getBody()
                    .getOutputs().stream()
                    .filter(o -> o.getAddress().equals(address))
                    .map(o -> o.getValue().getCoin())
                    .reduce(BigInteger.ZERO, (amount1, amount2) -> amount1.add(amount2));

            if (predicate != null) {
                if (!predicate.test(Amount.lovelace(lovelaceAmt))) {
                    String expectedMsg = formatExceptionMessage(customMsg, address, unit, lovelaceAmt);
                    throw new VerifierException(expectedMsg);
                }
            } else if (lovelaceAmt.compareTo(amount.getQuantity()) != 0) {
                String expectedMsg = formatExceptionMessage(customMsg, address, amount, lovelaceAmt);
                throw new VerifierException(expectedMsg);
            }
        } else {
            BigInteger assetAmount = txn.getBody()
                    .getOutputs().stream()
                    .filter(o -> o.getAddress().equals(address) && o.getValue().getMultiAssets() != null)
                    .flatMap(transactionOutput -> transactionOutput.getValue().getMultiAssets().stream())
                    .flatMap(multiAsset -> multiAsset.getAssets().stream().map(asset -> new Tuple<>(multiAsset.getPolicyId(), asset)))
                    .filter(assetTuple -> {
                        String unit = AssetUtil.getUnit(assetTuple._1, assetTuple._2);
                        return unit != null && unit.equals(amount.getUnit());
                    }).map(assetTuple -> assetTuple._2.getValue())
                    .reduce(BigInteger.ZERO, (amount1, amount2) -> amount1.add(amount2));

            if (predicate != null) {
                if (!predicate.test(Amount.asset(amount.getUnit(), assetAmount))) {
                    String expectedMsg = formatExceptionMessage(customMsg, address, unit, assetAmount);
                    throw new VerifierException(expectedMsg);
                }
            } else if (assetAmount.compareTo(amount.getQuantity()) != 0) {
                String expectedMsg = formatExceptionMessage(customMsg, address, amount, assetAmount);
                throw new VerifierException(expectedMsg);
            }
        }
    }

    private String formatExceptionMessage(String customMsg, String address, Amount expectedAmount, BigInteger actualAmount) {
        String expectedMsg = String.format("Expected amount %s(%s) for address %s, \nbut got %s",
                expectedAmount.getQuantity(), expectedAmount.getUnit(), address, actualAmount);
        if(customMsg != null)
            expectedMsg = customMsg + ".\n" + expectedMsg;

        return expectedMsg;
    }

    private String formatExceptionMessage(String customMsg, String address, String unit, BigInteger actualAmount) {
        String expectedMsg = String.format("Amount for  address %s and unit %s doesn't match the condition. Actual amount: %s",
                 address, unit, actualAmount);
        if(customMsg != null)
            expectedMsg = customMsg + ".\n" + expectedMsg;

        return expectedMsg;
    }
}

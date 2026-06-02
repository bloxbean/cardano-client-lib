package com.bloxbean.cardano.client.cip.cip102;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Utility class for CIP-102 royalty fee conversions and calculations.
 *
 * <h2>On-chain fee encoding</h2>
 * <p>Fees are stored as integers using the formula:
 * <pre>  stored_fee = floor(10 / fee_fraction) = floor(1000 / fee_percent)</pre>
 * where {@code fee_fraction} is the fee as a decimal (e.g. {@code 0.016} for 1.6%) and
 * {@code fee_percent} is the fee as a percentage (e.g. {@code 1.6} for 1.6%).
 *
 * <p>To recover the fee percentage from the stored value:
 * <pre>  fee_percent ≈ 1000 / stored_fee</pre>
 *
 * <h2>Fee calculation</h2>
 * <p>The royalty amount for a given sale price is:
 * <pre>  royalty = max(min_fee, min(max_fee, (10 / stored_fee) * sale_price))</pre>
 * where {@code min_fee} and {@code max_fee} are in the same unit as the sale price.
 */
public class RoyaltyFeeUtil {

    private RoyaltyFeeUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Converts a human-readable fee percentage to the on-chain integer representation.
     *
     * <p>Formula: {@code floor(1000 / feePercent)}
     *
     * @param feePercent royalty fee as a percentage (e.g. {@code 1.6} for 1.6%),
     *                   must be in the range [0.1, 100]
     * @return on-chain fee integer
     * @throws IllegalArgumentException if feePercent is outside [0.1, 100]
     */
    public static BigInteger toChainFee(double feePercent) {
        if (feePercent < 0.1 || feePercent > 100.0)
            throw new IllegalArgumentException("Fee percent must be in the range [0.1, 100], got: " + feePercent);
        return BigInteger.valueOf((long) Math.floor(1000.0 / feePercent));
    }

    /**
     * Converts an on-chain fee integer back to a human-readable percentage.
     *
     * <p>Formula: {@code ceil(10000 / chainFee) / 10.0}
     *
     * @param chainFee on-chain fee integer (must be &gt; 0)
     * @return fee as a percentage (e.g. {@code 1.6} for 1.6%)
     * @throws IllegalArgumentException if chainFee is zero or negative
     */
    public static double fromChainFee(BigInteger chainFee) {
        if (chainFee.compareTo(BigInteger.ZERO) <= 0)
            throw new IllegalArgumentException("Chain fee must be positive, got: " + chainFee);
        // ceil(10000 / chainFee) / 10.0  — matches the TypeScript reference implementation
        BigInteger[] divRem = BigInteger.valueOf(10000).divideAndRemainder(chainFee);
        long ceiled = divRem[0].longValue() + (divRem[1].equals(BigInteger.ZERO) ? 0 : 1);
        return ceiled / 10.0;
    }

    /**
     * Calculates the royalty amount owed for a given sale price, applying min/max caps.
     *
     * <p>Formula: {@code max(minFee, min(maxFee, (10 * salePrice) / chainFee))}
     *
     * <p>The result is in the same monetary unit as {@code salePrice} (lovelace for ADA sales,
     * or the base unit of the sale currency for non-ADA sales per version 2).
     *
     * @param chainFee  on-chain fee integer from the datum
     * @param salePrice sale price in the smallest unit of the sale currency
     * @param minFee    optional absolute minimum royalty (same unit as salePrice)
     * @param maxFee    optional absolute maximum royalty (same unit as salePrice)
     * @return royalty amount to pay
     */
    public static BigInteger calculateRoyaltyAmount(BigInteger chainFee,
                                                    BigInteger salePrice,
                                                    Optional<BigInteger> minFee,
                                                    Optional<BigInteger> maxFee) {
        // pct = (10 / chainFee) * salePrice  — integer arithmetic avoids floating point
        BigInteger pct = BigInteger.TEN.multiply(salePrice).divide(chainFee);

        if (maxFee.isPresent() && pct.compareTo(maxFee.get()) > 0)
            pct = maxFee.get();
        if (minFee.isPresent() && pct.compareTo(minFee.get()) < 0)
            pct = minFee.get();

        return pct;
    }
}

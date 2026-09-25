package com.bloxbean.cardano.client.common;

import java.math.BigDecimal;
import java.math.BigInteger;

public class ADAConversionUtil {
    private static final int ADA_DECIMAL = 6;

    public static BigInteger adaToLovelace(BigDecimal amount) {
        return assetFromDecimal(amount, ADA_DECIMAL);
    }

    public static BigDecimal lovelaceToAda(BigInteger amount) {
        return assetToDecimal(amount, ADA_DECIMAL);
    }

    public static BigDecimal assetToDecimal(BigInteger amount, long decimals) {
        if(decimals == 0)
            return new BigDecimal(amount);

        BigDecimal bigDecimalAmt = new BigDecimal(amount);
        BigDecimal decimalAmt = bigDecimalAmt.divide(unit(decimals));

        return decimalAmt;
    }

    public static BigInteger assetFromDecimal(BigDecimal doubleAmout, long decimals) {
        if(decimals == 0)
            return doubleAmout.toBigInteger();

        BigDecimal amount = unit(decimals).multiply(doubleAmout);

        return amount.toBigInteger();
    }

    // Math.pow(10, n) is not an integer once n is 23 or higher.
    private static BigDecimal unit(long decimals) {
        if (decimals > 0 && decimals <= Integer.MAX_VALUE) {
            return new BigDecimal(BigInteger.TEN.pow((int) decimals));
        }
        return BigDecimal.valueOf(Math.pow(10, decimals));
    }

    public static BigInteger adaToLovelace(double amount) {
        return adaToLovelace(BigDecimal.valueOf(amount));
    }
}

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

        double oneUnit = Math.pow(10, decimals);

        BigDecimal bigDecimalAmt = new BigDecimal(amount);
        BigDecimal decimalAmt = bigDecimalAmt.divide(new BigDecimal(oneUnit));

        return decimalAmt;
    }

    public static BigInteger assetFromDecimal(BigDecimal doubleAmout, long decimals) {
        if(decimals == 0)
            return doubleAmout.toBigInteger();

        double oneUnit = Math.pow(10, decimals);

        BigDecimal amount = new BigDecimal(oneUnit).multiply(doubleAmout);

        return amount.toBigInteger();
    }

    public static BigInteger adaToLovelace(double amount) {
        return adaToLovelace(BigDecimal.valueOf(amount));
    }
}

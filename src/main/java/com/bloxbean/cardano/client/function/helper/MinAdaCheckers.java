package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.function.MinAdaChecker;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;

import java.math.BigInteger;

/**
 * Provides helper method to get {@link MinAdaChecker} function
 */
public class MinAdaCheckers {

    public static MinAdaChecker minAdaChecker() {
        return (MinAdaCheckers::checkMinAdaRequirement);
    }

    private static BigInteger checkMinAdaRequirement(TxBuilderContext context, TransactionOutput output) {
        MinAdaCalculator minAdaCalculator = new MinAdaCalculator(context.getProtocolParams());
        BigInteger minAda = minAdaCalculator.calculateMinAda(output);

        if (minAda.compareTo(output.getValue().getCoin()) == 1) {
            //output.getValue().setCoin(minAda);
            return minAda.subtract(output.getValue().getCoin());
        } else {
            return BigInteger.ZERO;
        }
    }

}

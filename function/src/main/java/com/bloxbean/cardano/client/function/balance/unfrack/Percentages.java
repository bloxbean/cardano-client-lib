package com.bloxbean.cardano.client.function.balance.unfrack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Percentage split shared by percentage based strategies.
 */
final class Percentages {
    private static final BigInteger HUNDRED = BigInteger.valueOf(100);

    private Percentages() {
    }

    static void validate(List<Integer> percentages) {
        if (percentages == null || percentages.isEmpty())
            throw new IllegalArgumentException("percentages must not be empty");
        if (percentages.stream().anyMatch(p -> p == null || p <= 0))
            throw new IllegalArgumentException("percentages must be positive: " + percentages);
        if (percentages.stream().mapToInt(Integer::intValue).sum() != 100)
            throw new IllegalArgumentException("percentages must sum to 100: " + percentages);
    }

    /**
     * Split lovelace by percentages, the last slice gets the rounding remainder.
     */
    static List<BigInteger> split(BigInteger lovelace, List<Integer> percentages) {
        List<BigInteger> slices = new ArrayList<>(percentages.size());
        BigInteger remaining = lovelace;
        for (int i = 0; i < percentages.size(); i++) {
            BigInteger amount = i == percentages.size() - 1
                    ? remaining
                    : lovelace.multiply(BigInteger.valueOf(percentages.get(i))).divide(HUNDRED);
            remaining = remaining.subtract(amount);
            slices.add(amount);
        }
        return slices;
    }

    static BigInteger smallestSlice(BigInteger lovelace, List<Integer> percentages) {
        int smallest = percentages.stream().mapToInt(Integer::intValue).min().orElseThrow();
        return lovelace.multiply(BigInteger.valueOf(smallest)).divide(HUNDRED);
    }
}

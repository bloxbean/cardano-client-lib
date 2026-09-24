package com.bloxbean.cardano.client.function.balance.unfrack;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for {@link Unfrack}. Defaults follow the Evolution SDK unfrack defaults.
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class UnfrackConfig {

    /**
     * Lovelace amount above which ada is subdivided into multiple ada-only outputs. Default: 100 ADA.
     */
    @Builder.Default
    private final BigInteger subdivideThreshold = BigInteger.valueOf(100_000_000L);

    /**
     * Percentages used to subdivide ada-only change. Must be positive and sum to 100.
     * Default: 50, 15, 10, 10, 5, 5, 5.
     */
    @Builder.Default
    private final List<Integer> subdividePercentages = List.of(50, 15, 10, 10, 5, 5, 5);

    /**
     * Maximum number of tokens (of the same policy) per bundle output. Default: 10.
     */
    @Builder.Default
    private final int bundleSize = 10;

    /**
     * Lovelace reserved on the largest change piece to pay the transaction fee, so that after fee deduction
     * all other pieces still meet min-ada. Default: 2 ADA.
     */
    @Builder.Default
    private final BigInteger feeReserve = BigInteger.valueOf(2_000_000L);

    public static UnfrackConfig defaults() {
        return UnfrackConfig.builder().build();
    }

    void validate() {
        Objects.requireNonNull(subdivideThreshold, "subdivideThreshold");
        Objects.requireNonNull(subdividePercentages, "subdividePercentages");
        Objects.requireNonNull(feeReserve, "feeReserve");
        if (subdividePercentages.isEmpty())
            throw new IllegalArgumentException("subdividePercentages must not be empty");
        if (subdividePercentages.stream().anyMatch(p -> p == null || p <= 0))
            throw new IllegalArgumentException("subdividePercentages must be positive: " + subdividePercentages);
        if (subdividePercentages.stream().mapToInt(Integer::intValue).sum() != 100)
            throw new IllegalArgumentException("subdividePercentages must sum to 100: " + subdividePercentages);
        if (bundleSize < 1)
            throw new IllegalArgumentException("bundleSize must be >= 1");
        if (subdivideThreshold.signum() < 0 || feeReserve.signum() < 0)
            throw new IllegalArgumentException("subdivideThreshold and feeReserve must be >= 0");
    }
}

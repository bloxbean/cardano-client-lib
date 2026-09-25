package com.bloxbean.cardano.client.function.balance.unfrack;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigInteger;
import java.util.List;

/**
 * Evolution-style percentage split with the fixes from the ADR: ADA is always kept apart from tokens when it covers
 * min-ada, and tokens are bundled by byte size by default.
 * <p>
 * ADA at or above {@code subdivideThreshold} is split by {@code subdividePercentages} (last slice gets the
 * remainder) when the smallest slice meets min-ada. Otherwise it stays one ADA-only output.
 */
@Getter
@ToString
public class PercentageSplitStrategy extends AbstractChangeSplitStrategy {
    private final BigInteger subdivideThreshold;
    private final List<Integer> subdividePercentages;

    public PercentageSplitStrategy() {
        this(null, null, null);
    }

    /**
     * @param subdivideThreshold   lovelace from which ADA is subdivided. Default: 100 ADA
     * @param subdividePercentages positive percentages summing to 100. Default: 50, 15, 10, 10, 5, 5, 5
     * @param tokenBundling        token bundling. Default: {@link ByteBudgetBundling}
     */
    @Builder
    public PercentageSplitStrategy(BigInteger subdivideThreshold, List<Integer> subdividePercentages,
                                   TokenBundlingStrategy tokenBundling) {
        super(tokenBundling);
        this.subdivideThreshold = subdivideThreshold != null ?
                subdivideThreshold : EvolutionStrategy.DEFAULT_SUBDIVIDE_THRESHOLD;
        this.subdividePercentages = subdividePercentages != null ?
                List.copyOf(subdividePercentages) : EvolutionStrategy.DEFAULT_SUBDIVIDE_PERCENTAGES;

        if (this.subdivideThreshold.signum() < 0)
            throw new IllegalArgumentException("subdivideThreshold must be >= 0");
        Percentages.validate(this.subdividePercentages);
    }

    @Override
    protected List<BigInteger> splitAda(BigInteger lovelace, ChangeSplitRequest request) {
        if (lovelace.compareTo(subdivideThreshold) < 0)
            return List.of(lovelace);
        if (Percentages.smallestSlice(lovelace, subdividePercentages).compareTo(request.adaOnlyMinAda()) < 0)
            return List.of(lovelace);
        return Percentages.split(lovelace, subdividePercentages);
    }
}

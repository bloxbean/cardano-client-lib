package com.bloxbean.cardano.client.function.balance.unfrack;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits ADA into up to {@code lanes} equal ADA-only outputs ("lanes"), each at least {@code minLaneAmount}.
 * The last lane gets the rounding remainder. Fewer lanes are created when ADA is not enough for all of them.
 * <p>
 * Meant for wallets that send many payments of a similar size, e.g. bots and services.
 */
@Getter
@ToString
public class EqualLanesStrategy extends AbstractChangeSplitStrategy {
    public static final int DEFAULT_LANES = 5;
    public static final BigInteger DEFAULT_MIN_LANE_AMOUNT = BigInteger.valueOf(10_000_000L);

    private final int lanes;
    private final BigInteger minLaneAmount;

    public EqualLanesStrategy() {
        this(null, null, null);
    }

    /**
     * @param lanes         max number of ADA-only outputs. Default: 5
     * @param minLaneAmount min lovelace per lane, raised to min-ada if lower. Default: 10 ADA
     * @param tokenBundling token bundling. Default: {@link ByteBudgetBundling}
     */
    @Builder
    public EqualLanesStrategy(Integer lanes, BigInteger minLaneAmount, TokenBundlingStrategy tokenBundling) {
        super(tokenBundling);
        this.lanes = lanes != null ? lanes : DEFAULT_LANES;
        this.minLaneAmount = minLaneAmount != null ? minLaneAmount : DEFAULT_MIN_LANE_AMOUNT;

        if (this.lanes < 1)
            throw new IllegalArgumentException("lanes must be >= 1");
        if (this.minLaneAmount.signum() < 0)
            throw new IllegalArgumentException("minLaneAmount must be >= 0");
    }

    @Override
    protected List<BigInteger> splitAda(BigInteger lovelace, ChangeSplitRequest request) {
        BigInteger laneFloor = minLaneAmount.max(request.adaOnlyMinAda());
        long affordable = lovelace.divide(laneFloor).min(BigInteger.valueOf(lanes)).longValueExact();
        if (affordable <= 1)
            return List.of(lovelace);

        BigInteger n = BigInteger.valueOf(affordable);
        BigInteger perLane = lovelace.divide(n);
        List<BigInteger> amounts = new ArrayList<>((int) affordable);
        for (int i = 0; i < affordable - 1; i++)
            amounts.add(perLane);
        amounts.add(lovelace.subtract(perLane.multiply(n.subtract(BigInteger.ONE))));
        return amounts;
    }
}

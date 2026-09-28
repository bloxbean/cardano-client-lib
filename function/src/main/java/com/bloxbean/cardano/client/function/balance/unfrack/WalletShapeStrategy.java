package com.bloxbean.cardano.client.function.balance.unfrack;

import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.math.BigInteger;
import java.util.List;

/**
 * {@link ChangeSplitStrategy} that shapes change towards a {@link WalletShape}: tokens are grouped by the shape's
 * {@link TokenBundlingStrategy} with exactly their min-ada, and the remaining ADA is split by its {@link AdaShape}.
 * Consolidation is done by {@link Unfrack}, not by this strategy.
 */
@Getter
@ToString
public class WalletShapeStrategy extends AbstractChangeSplitStrategy {
    private final WalletShape shape;

    public WalletShapeStrategy(@NonNull WalletShape shape) {
        super(shape.getTokens());
        this.shape = shape;
    }

    @Override
    protected List<BigInteger> splitAda(BigInteger lovelace, ChangeSplitRequest request) {
        return shape.getAda().split(lovelace, request);
    }
}

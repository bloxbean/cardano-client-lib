package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.Getter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for strategies which keep ADA and tokens apart and only differ in how ADA is split.
 * <ol>
 *     <li>Tokens are grouped by a {@link TokenBundlingStrategy}. Each bundle gets exactly its min-ada.</li>
 *     <li>The remaining ADA goes to ADA-only outputs, split by {@link #splitAda(BigInteger, ChangeSplitRequest)}.</li>
 *     <li>Only when the remaining ADA is below the ADA-only min-ada it is added to the last bundle.</li>
 * </ol>
 * Unlike Evolution SDK, ADA below the subdivision threshold is not spread across token bundles, so plain ADA
 * payments don't have to spend token UTxOs.
 */
@Getter
public abstract class AbstractChangeSplitStrategy implements ChangeSplitStrategy {
    private static final BigInteger ONE_ADA = BigInteger.valueOf(1_000_000L);

    private final TokenBundlingStrategy tokenBundling;

    protected AbstractChangeSplitStrategy(TokenBundlingStrategy tokenBundling) {
        this.tokenBundling = tokenBundling != null ? tokenBundling : new ByteBudgetBundling();
    }

    /**
     * Split an ADA-only amount.
     *
     * @param lovelace amount to split, at least the ADA-only min-ada
     * @param request  split request
     * @return amounts which sum up to {@code lovelace}, each at least {@link ChangeSplitRequest#adaOnlyMinAda()}.
     * A single amount means "do not split".
     */
    protected abstract List<BigInteger> splitAda(BigInteger lovelace, ChangeSplitRequest request);

    @Override
    public final List<Value> split(ChangeSplitRequest request) {
        Value change = request.getChange();
        BigInteger lovelace = ChangeValues.coinOf(change);
        List<MultiAsset> policies = ChangeValues.nonEmptyPolicies(change.getMultiAssets());

        if (lovelace.signum() <= 0 || ChangeValues.hasNegativeAsset(policies))
            return List.of(change);

        if (policies.isEmpty()) {
            if (lovelace.compareTo(request.adaOnlyMinAda()) < 0)
                return List.of(change);
            List<BigInteger> amounts = checkedSplitAda(lovelace, request);
            return amounts.size() <= 1 ? List.of(change) : toAdaValues(amounts);
        }

        List<List<MultiAsset>> bundles = tokenBundling.bundle(policies);
        List<Value> result = new ArrayList<>(bundles.size());
        BigInteger remaining = lovelace;
        for (List<MultiAsset> bundle : bundles) {
            BigInteger minAda = request.minAda(new Value(ONE_ADA, bundle)); // placeholder coin of realistic CBOR size
            result.add(new Value(minAda, bundle));
            remaining = remaining.subtract(minAda);
        }

        if (remaining.signum() < 0)
            return List.of(change);

        if (remaining.compareTo(request.adaOnlyMinAda()) >= 0) {
            result.addAll(toAdaValues(checkedSplitAda(remaining, request)));
        } else if (remaining.signum() > 0) {
            int last = result.size() - 1;
            Value lastBundle = result.get(last);
            result.set(last, new Value(lastBundle.getCoin().add(remaining), lastBundle.getMultiAssets()));
        }

        return result.size() <= 1 ? List.of(change) : result;
    }

    private List<BigInteger> checkedSplitAda(BigInteger lovelace, ChangeSplitRequest request) {
        List<BigInteger> amounts = splitAda(lovelace, request);
        if (amounts == null || amounts.isEmpty())
            throw new IllegalStateException(getClass().getSimpleName() + " returned no ada amounts");
        BigInteger sum = amounts.stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (!sum.equals(lovelace))
            throw new IllegalStateException(getClass().getSimpleName() + " split " + lovelace + " into " + amounts);
        return amounts;
    }

    private static List<Value> toAdaValues(List<BigInteger> amounts) {
        List<Value> values = new ArrayList<>(amounts.size());
        for (BigInteger amount : amounts)
            values.add(Value.fromCoin(amount));
        return values;
    }
}

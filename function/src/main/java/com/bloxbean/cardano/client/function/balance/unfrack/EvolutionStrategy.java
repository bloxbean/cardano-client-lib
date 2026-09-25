package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of Evolution SDK's {@code createUnfrackedChangeOutputs}. Default strategy of {@link Unfrack}.
 * <ul>
 *     <li>No tokens: if ada &gt;= subdivideThreshold and the smallest percentage slice meets min-ada, split ada by
 *     subdividePercentages (last slice gets the remainder), otherwise a single output.</li>
 *     <li>Tokens: one bundle per policy, chunked by bundleSize, each with its min-ada. If the remaining ada is
 *     &gt;= subdivideThreshold it becomes separate ada output(s), otherwise it is spread evenly across the bundles.</li>
 *     <li>If bundles are unaffordable, a single output.</li>
 * </ul>
 */
@Slf4j
@Getter
@ToString
public class EvolutionStrategy implements ChangeSplitStrategy {
    public static final BigInteger DEFAULT_SUBDIVIDE_THRESHOLD = BigInteger.valueOf(100_000_000L);
    public static final List<Integer> DEFAULT_SUBDIVIDE_PERCENTAGES = List.of(50, 15, 10, 10, 5, 5, 5);

    private static final BigInteger ONE_ADA = BigInteger.valueOf(1_000_000L);

    private final BigInteger subdivideThreshold;
    private final List<Integer> subdividePercentages;
    private final int bundleSize;

    public EvolutionStrategy() {
        this(null, null, null);
    }

    /**
     * @param subdivideThreshold   lovelace from which ada is subdivided. Default: 100 ADA
     * @param subdividePercentages positive percentages summing to 100. Default: 50, 15, 10, 10, 5, 5, 5
     * @param bundleSize           max tokens of one policy per bundle. Default: 10
     */
    @Builder
    public EvolutionStrategy(BigInteger subdivideThreshold, List<Integer> subdividePercentages, Integer bundleSize) {
        this.subdivideThreshold = subdivideThreshold != null ? subdivideThreshold : DEFAULT_SUBDIVIDE_THRESHOLD;
        this.subdividePercentages = subdividePercentages != null ?
                List.copyOf(subdividePercentages) : DEFAULT_SUBDIVIDE_PERCENTAGES;
        this.bundleSize = bundleSize != null ? bundleSize : PolicyBundling.DEFAULT_BUNDLE_SIZE;

        if (this.subdivideThreshold.signum() < 0)
            throw new IllegalArgumentException("subdivideThreshold must be >= 0");
        Percentages.validate(this.subdividePercentages);
        if (this.bundleSize < 1)
            throw new IllegalArgumentException("bundleSize must be >= 1");
    }

    @Override
    public List<Value> split(ChangeSplitRequest request) {
        Value change = request.getChange();
        BigInteger lovelace = ChangeValues.coinOf(change);
        List<MultiAsset> policies = ChangeValues.nonEmptyPolicies(change.getMultiAssets());

        if (lovelace.signum() <= 0 || ChangeValues.hasNegativeAsset(policies))
            return List.of(change);

        if (policies.isEmpty())
            return planAdaOnly(request, change, lovelace);

        return planWithTokens(request, change, lovelace, policies);
    }

    private List<Value> planAdaOnly(ChangeSplitRequest request, Value original, BigInteger lovelace) {
        if (lovelace.compareTo(subdivideThreshold) < 0)
            return List.of(original);

        List<BigInteger> slices = subdivide(lovelace, request.adaOnlyMinAda());
        return slices != null ? toAdaValues(slices) : List.of(original);
    }

    private List<Value> planWithTokens(ChangeSplitRequest request, Value original, BigInteger lovelace,
                                       List<MultiAsset> policies) {
        List<List<MultiAsset>> bundles = new PolicyBundling(bundleSize).bundle(policies);
        List<BigInteger> bundleMinAda = new ArrayList<>(bundles.size());
        BigInteger bundlesMinAda = BigInteger.ZERO;
        for (List<MultiAsset> bundle : bundles) {
            BigInteger min = request.minAda(new Value(ONE_ADA, bundle));
            bundleMinAda.add(min);
            bundlesMinAda = bundlesMinAda.add(min);
        }

        BigInteger remaining = lovelace.subtract(bundlesMinAda);
        if (remaining.signum() < 0) {
            log.debug("Unfrack: {} bundles need {} lovelace, only {} available. Using single output.",
                    bundles.size(), bundlesMinAda, lovelace);
            return List.of(original);
        }

        if (remaining.compareTo(subdivideThreshold) >= 0) {
            BigInteger adaMinUtxo = request.minAda(Value.fromCoin(remaining));
            if (remaining.compareTo(adaMinUtxo) >= 0) {
                List<Value> result = new ArrayList<>();
                for (int i = 0; i < bundles.size(); i++)
                    result.add(new Value(bundleMinAda.get(i), bundles.get(i)));

                List<BigInteger> slices = subdivide(remaining, adaMinUtxo);
                result.addAll(toAdaValues(slices != null ? slices : List.of(remaining)));
                return result;
            }
        }

        // Spread remaining ada evenly across bundles, last bundle gets the remainder
        BigInteger n = BigInteger.valueOf(bundles.size());
        BigInteger perBundle = remaining.divide(n);
        BigInteger extraForLast = remaining.mod(n);
        List<Value> result = new ArrayList<>(bundles.size());
        for (int i = 0; i < bundles.size(); i++) {
            BigInteger coin = bundleMinAda.get(i).add(perBundle);
            if (i == bundles.size() - 1)
                coin = coin.add(extraForLast);
            result.add(new Value(coin, bundles.get(i)));
        }
        return result.size() <= 1 ? List.of(original) : result;
    }

    /**
     * Split lovelace by subdividePercentages. Returns null if the smallest slice doesn't meet min-ada.
     */
    private List<BigInteger> subdivide(BigInteger lovelace, BigInteger adaMinUtxo) {
        if (Percentages.smallestSlice(lovelace, subdividePercentages).compareTo(adaMinUtxo) < 0)
            return null;
        return Percentages.split(lovelace, subdividePercentages);
    }

    private static List<Value> toAdaValues(List<BigInteger> amounts) {
        List<Value> values = new ArrayList<>(amounts.size());
        for (BigInteger amount : amounts)
            values.add(Value.fromCoin(amount));
        return values;
    }
}

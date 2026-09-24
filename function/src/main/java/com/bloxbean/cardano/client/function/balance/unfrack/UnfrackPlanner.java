package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure unfrack algorithm: splits a change {@link Value} into multiple values, each of which is a valid output
 * (meets min-ada) at the given address. Port of Evolution SDK's {@code createUnfrackedChangeOutputs}.
 * <ul>
 *     <li>No tokens: if ada &gt;= subdivideThreshold and the smallest percentage slice meets min-ada, split ada by
 *     subdividePercentages (last slice gets the remainder), otherwise a single output.</li>
 *     <li>Tokens: group by policy, chunk each policy into bundles of at most bundleSize tokens, give each bundle its
 *     min-ada. If the remaining ada is &gt;= subdivideThreshold and meets ada-only min-ada, emit it as separate ada
 *     output(s) (subdivided if affordable). Otherwise spread it evenly across the bundles.</li>
 *     <li>If bundles are unaffordable, fall back to a single output.</li>
 * </ul>
 * The returned values always sum up to the input value. A single-element result means "do not split".
 */
@Slf4j
public class UnfrackPlanner {
    private static final BigInteger ONE_ADA = BigInteger.valueOf(1_000_000L);
    private static final BigInteger HUNDRED = BigInteger.valueOf(100);

    private final UnfrackConfig config;
    private final MinAdaCalculator minAdaCalculator;

    public UnfrackPlanner(UnfrackConfig config, ProtocolParams protocolParams) {
        config.validate();
        this.config = config;
        this.minAdaCalculator = new MinAdaCalculator(protocolParams);
    }

    public List<Value> plan(String address, Value value) {
        BigInteger lovelace = value.getCoin() == null ? BigInteger.ZERO : value.getCoin();
        List<MultiAsset> policies = nonEmptyPolicies(value.getMultiAssets());

        if (lovelace.signum() <= 0 || hasNonPositiveAsset(policies))
            return List.of(value);

        if (policies.isEmpty())
            return planAdaOnly(address, value, lovelace);

        return planWithTokens(address, value, lovelace, policies);
    }

    private List<Value> planAdaOnly(String address, Value original, BigInteger lovelace) {
        if (lovelace.compareTo(config.getSubdivideThreshold()) < 0)
            return List.of(original);

        BigInteger adaMinUtxo = minAda(address, Value.fromCoin(ONE_ADA));
        List<Value> slices = subdivide(lovelace, adaMinUtxo);
        return slices != null ? slices : List.of(original);
    }

    private List<Value> planWithTokens(String address, Value original, BigInteger lovelace, List<MultiAsset> policies) {
        List<MultiAsset> bundles = bundle(policies);
        List<BigInteger> bundleMinAda = new ArrayList<>(bundles.size());
        BigInteger bundlesMinAda = BigInteger.ZERO;
        for (MultiAsset bundle : bundles) {
            BigInteger min = minAda(address, new Value(ONE_ADA, List.of(bundle)));
            bundleMinAda.add(min);
            bundlesMinAda = bundlesMinAda.add(min);
        }

        BigInteger remaining = lovelace.subtract(bundlesMinAda);
        if (remaining.signum() < 0) {
            log.debug("Unfrack: {} bundles need {} lovelace, only {} available. Using single output.",
                    bundles.size(), bundlesMinAda, lovelace);
            return List.of(original);
        }

        if (remaining.compareTo(config.getSubdivideThreshold()) >= 0) {
            BigInteger adaMinUtxo = minAda(address, Value.fromCoin(remaining));
            if (remaining.compareTo(adaMinUtxo) >= 0) {
                List<Value> result = new ArrayList<>();
                for (int i = 0; i < bundles.size(); i++)
                    result.add(new Value(bundleMinAda.get(i), List.of(bundles.get(i))));

                List<Value> slices = subdivide(remaining, adaMinUtxo);
                result.addAll(slices != null ? slices : List.of(Value.fromCoin(remaining)));
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
            result.add(new Value(coin, List.of(bundles.get(i))));
        }
        return result;
    }

    /**
     * Split lovelace by subdividePercentages. Returns null if the smallest slice doesn't meet min-ada.
     */
    private List<Value> subdivide(BigInteger lovelace, BigInteger adaMinUtxo) {
        List<Integer> percentages = config.getSubdividePercentages();
        int smallest = percentages.stream().mapToInt(Integer::intValue).min().orElseThrow();
        BigInteger smallestAmount = lovelace.multiply(BigInteger.valueOf(smallest)).divide(HUNDRED);
        if (smallestAmount.compareTo(adaMinUtxo) < 0)
            return null;

        List<Value> slices = new ArrayList<>(percentages.size());
        BigInteger remaining = lovelace;
        for (int i = 0; i < percentages.size(); i++) {
            BigInteger amount = i == percentages.size() - 1
                    ? remaining
                    : lovelace.multiply(BigInteger.valueOf(percentages.get(i))).divide(HUNDRED);
            remaining = remaining.subtract(amount);
            slices.add(Value.fromCoin(amount));
        }
        return slices;
    }

    private List<MultiAsset> bundle(List<MultiAsset> policies) {
        int bundleSize = config.getBundleSize();
        List<MultiAsset> bundles = new ArrayList<>();
        for (MultiAsset policy : policies) {
            List<Asset> assets = policy.getAssets();
            for (int i = 0; i < assets.size(); i += bundleSize) {
                List<Asset> chunk = new ArrayList<>();
                for (Asset asset : assets.subList(i, Math.min(i + bundleSize, assets.size())))
                    chunk.add(new Asset(asset.getName(), asset.getValue()));
                bundles.add(new MultiAsset(policy.getPolicyId(), chunk));
            }
        }
        return bundles;
    }

    private static List<MultiAsset> nonEmptyPolicies(List<MultiAsset> multiAssets) {
        List<MultiAsset> result = new ArrayList<>();
        if (multiAssets == null)
            return result;
        for (MultiAsset ma : multiAssets) {
            if (ma.getAssets() == null)
                continue;
            List<Asset> assets = new ArrayList<>();
            for (Asset asset : ma.getAssets()) {
                if (asset.getValue() != null && asset.getValue().signum() != 0)
                    assets.add(asset);
            }
            if (!assets.isEmpty())
                result.add(new MultiAsset(ma.getPolicyId(), assets));
        }
        return result;
    }

    private static boolean hasNonPositiveAsset(List<MultiAsset> policies) {
        return policies.stream()
                .flatMap(ma -> ma.getAssets().stream())
                .anyMatch(asset -> asset.getValue().signum() < 0);
    }

    private BigInteger minAda(String address, Value value) {
        return minAdaCalculator.calculateMinAda(new TransactionOutput(address, value));
    }
}

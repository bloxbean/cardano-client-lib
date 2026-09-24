package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnfrackPlannerTest {
    static final String ADDRESS = "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";
    static final String POLICY_1 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8";
    static final String POLICY_2 = "b1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8";

    ProtocolParams protocolParams = ProtocolParams.builder().coinsPerUtxoSize("4310").build();
    MinAdaCalculator minAdaCalculator = new MinAdaCalculator(protocolParams);
    UnfrackPlanner planner = new UnfrackPlanner(UnfrackConfig.defaults(), protocolParams);

    @Test
    void adaOnly_belowThreshold_singleOutput() {
        Value value = Value.fromCoin(adaToLovelace(99));

        List<Value> result = planner.plan(ADDRESS, value);

        assertThat(result).containsExactly(value);
    }

    @Test
    void adaOnly_aboveThreshold_subdividedByPercentages() {
        List<Value> result = planner.plan(ADDRESS, Value.fromCoin(adaToLovelace(1000)));

        assertThat(result).extracting(Value::getCoin).containsExactly(
                adaToLovelace(500), adaToLovelace(150), adaToLovelace(100), adaToLovelace(100),
                adaToLovelace(50), adaToLovelace(50), adaToLovelace(50));
    }

    @Test
    void adaOnly_lastSliceGetsRemainder() {
        BigInteger lovelace = BigInteger.valueOf(123_456_789L);

        List<Value> result = planner.plan(ADDRESS, Value.fromCoin(lovelace));

        assertThat(result).hasSize(7);
        assertThat(result.get(0).getCoin()).isEqualTo(BigInteger.valueOf(61_728_394L));
        assertConserved(Value.fromCoin(lovelace), result);
        assertAllMeetMinAda(result);
    }

    @Test
    void adaOnly_subdivisionUnaffordable_singleOutput() {
        UnfrackPlanner customPlanner = new UnfrackPlanner(UnfrackConfig.builder()
                .subdivideThreshold(adaToLovelace(10))
                .subdividePercentages(List.of(95, 5))
                .build(), protocolParams);
        Value value = Value.fromCoin(adaToLovelace(10)); // 5% = 0.5 ADA < min ada

        assertThat(customPlanner.plan(ADDRESS, value)).containsExactly(value);
    }

    @Test
    void tokens_smallAda_spreadAcrossPolicyBundles() {
        Value value = new Value(adaToLovelace(10), List.of(
                multiAsset(POLICY_1, 3, BigInteger.ONE),
                multiAsset(POLICY_2, 2, BigInteger.valueOf(1000))));

        List<Value> result = planner.plan(ADDRESS, value);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMultiAssets()).singleElement()
                .satisfies(ma -> assertThat(ma.getPolicyId()).isEqualTo(POLICY_1));
        assertThat(result.get(1).getMultiAssets()).singleElement()
                .satisfies(ma -> assertThat(ma.getPolicyId()).isEqualTo(POLICY_2));
        assertConserved(value, result);
        assertAllMeetMinAda(result);
    }

    @Test
    void tokens_policyLargerThanBundleSize_splitIntoChunks() {
        Value value = new Value(adaToLovelace(20), List.of(multiAsset(POLICY_1, 25, BigInteger.ONE)));

        List<Value> result = planner.plan(ADDRESS, value);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(v -> v.getMultiAssets().get(0).getAssets().size()).containsExactly(10, 10, 5);
        assertConserved(value, result);
        assertAllMeetMinAda(result);
    }

    @Test
    void tokens_largeAda_bundlesWithMinAdaPlusSubdividedAda() {
        Value value = new Value(adaToLovelace(1000), List.of(
                multiAsset(POLICY_1, 3, BigInteger.ONE),
                multiAsset(POLICY_2, 1, BigInteger.valueOf(1000))));

        List<Value> result = planner.plan(ADDRESS, value);

        assertThat(result).hasSize(2 + 7);
        for (int i = 0; i < 2; i++) {
            Value bundle = result.get(i);
            assertThat(bundle.getCoin()).isEqualTo(minAda(bundle));
        }
        assertThat(result.subList(2, 9)).allSatisfy(v -> assertThat(v.getMultiAssets()).isNullOrEmpty());
        assertConserved(value, result);
        assertAllMeetMinAda(result);
    }

    @Test
    void tokens_bundlesUnaffordable_singleOutput() {
        Value value = new Value(adaToLovelace(2), List.of(
                multiAsset(POLICY_1, 3, BigInteger.ONE),
                multiAsset(POLICY_2, 3, BigInteger.ONE)));

        assertThat(planner.plan(ADDRESS, value)).containsExactly(value);
    }

    @Test
    void negativeOrZeroCoin_untouched() {
        Value value = Value.fromCoin(BigInteger.valueOf(-5));

        assertThat(planner.plan(ADDRESS, value)).containsExactly(value);
    }

    @Test
    void invalidConfig_rejected() {
        UnfrackConfig badPercentages = UnfrackConfig.builder().subdividePercentages(List.of(50, 40)).build();
        UnfrackConfig badBundleSize = UnfrackConfig.builder().bundleSize(0).build();

        assertThatThrownBy(() -> new UnfrackPlanner(badPercentages, protocolParams))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnfrackPlanner(badBundleSize, protocolParams))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static MultiAsset multiAsset(String policyId, int count, BigInteger qty) {
        List<Asset> assets = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> assets.add(new Asset("token" + i, qty)));
        return new MultiAsset(policyId, assets);
    }

    private BigInteger minAda(Value value) {
        return minAdaCalculator.calculateMinAda(new TransactionOutput(ADDRESS, value));
    }

    private void assertAllMeetMinAda(List<Value> values) {
        assertThat(values).isNotEmpty()
                .allSatisfy(v -> assertThat(v.getCoin()).isGreaterThanOrEqualTo(minAda(v)));
    }

    static void assertConserved(Value original, List<Value> pieces) {
        Value sum = pieces.stream().reduce(Value.fromCoin(BigInteger.ZERO), Value::add);
        assertThat(sum.getCoin()).isEqualTo(original.getCoin());
        if (original.getMultiAssets() != null) {
            for (MultiAsset ma : original.getMultiAssets())
                for (Asset asset : ma.getAssets())
                    assertThat(sum.amountOf(ma.getPolicyId(), asset.getName())).isEqualTo(asset.getValue());
        }
        long originalAssets = original.getMultiAssets() == null ? 0 :
                original.getMultiAssets().stream().mapToLong(ma -> ma.getAssets().size()).sum();
        long sumAssets = sum.getMultiAssets() == null ? 0 :
                sum.getMultiAssets().stream().mapToLong(ma -> ma.getAssets().size()).sum();
        assertThat(sumAssets).isEqualTo(originalAssets);
    }
}

package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvolutionStrategyTest {
    EvolutionStrategy strategy = new EvolutionStrategy();

    @Nested
    class AdaOnly {

        @Test
        void belowThreshold_singleOutput() {
            Value value = Value.fromCoin(adaToLovelace(99));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }

        @Test
        void justBelowThreshold_singleOutput() {
            Value value = Value.fromCoin(adaToLovelace(100).subtract(BigInteger.ONE));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }

        @Test
        void exactlyAtThreshold_subdivided() {
            Value value = Value.fromCoin(adaToLovelace(100));

            List<Value> result = strategy.split(request(value));

            assertThat(result).extracting(Value::getCoin).containsExactly(
                    adaToLovelace(50), adaToLovelace(15), adaToLovelace(10), adaToLovelace(10),
                    adaToLovelace(5), adaToLovelace(5), adaToLovelace(5));
        }

        @Test
        void aboveThreshold_subdividedByDefaultPercentages() {
            List<Value> result = strategy.split(request(Value.fromCoin(adaToLovelace(1000))));

            assertThat(result).extracting(Value::getCoin).containsExactly(
                    adaToLovelace(500), adaToLovelace(150), adaToLovelace(100), adaToLovelace(100),
                    adaToLovelace(50), adaToLovelace(50), adaToLovelace(50));
        }

        @Test
        void lastSliceGetsRoundingRemainder() {
            Value value = Value.fromCoin(BigInteger.valueOf(123_456_789L));

            List<Value> result = strategy.split(request(value));

            assertThat(result).hasSize(7);
            assertThat(result.get(0).getCoin()).isEqualTo(BigInteger.valueOf(61_728_394L));
            assertThat(result.get(6).getCoin()).isEqualTo(BigInteger.valueOf(123_456_789L)
                    .subtract(result.subList(0, 6).stream().map(Value::getCoin).reduce(BigInteger.ZERO, BigInteger::add)));
            assertValid(value, result);
        }

        @Test
        void customPercentages() {
            EvolutionStrategy custom = EvolutionStrategy.builder()
                    .subdivideThreshold(adaToLovelace(10))
                    .subdividePercentages(List.of(50, 30, 20))
                    .build();

            List<Value> result = custom.split(request(Value.fromCoin(adaToLovelace(100))));

            assertThat(result).extracting(Value::getCoin)
                    .containsExactly(adaToLovelace(50), adaToLovelace(30), adaToLovelace(20));
        }

        @Test
        void smallestSliceBelowMinAda_singleOutput() {
            EvolutionStrategy custom = EvolutionStrategy.builder()
                    .subdivideThreshold(adaToLovelace(10))
                    .subdividePercentages(List.of(95, 5))
                    .build();
            Value value = Value.fromCoin(adaToLovelace(10)); // 5% = 0.5 ADA < min-ada

            assertThat(custom.split(request(value))).containsExactly(value);
        }

        @Test
        void zeroThreshold_smallAmountStillNeedsAffordableSlices() {
            EvolutionStrategy custom = EvolutionStrategy.builder().subdivideThreshold(BigInteger.ZERO).build();
            Value value = Value.fromCoin(adaToLovelace(5)); // 5% = 0.25 ADA

            assertThat(custom.split(request(value))).containsExactly(value);
        }
    }

    @Nested
    class WithTokens {

        @Test
        void smallAda_spreadAcrossPolicyBundles_noAdaOnlyOutput() {
            Value value = new Value(adaToLovelace(10), List.of(
                    multiAsset(POLICY_1, 3, BigInteger.ONE),
                    multiAsset(POLICY_2, 2, BigInteger.valueOf(1000))));

            List<Value> result = strategy.split(request(value));

            assertThat(result).hasSize(2);
            assertThat(adaOnly(result)).isEmpty();
            assertThat(result.get(0).getMultiAssets()).singleElement()
                    .satisfies(ma -> assertThat(ma.getPolicyId()).isEqualTo(POLICY_1));
            assertThat(result.get(1).getMultiAssets()).singleElement()
                    .satisfies(ma -> assertThat(ma.getPolicyId()).isEqualTo(POLICY_2));
            assertValid(value, result);
        }

        @Test
        void spread_evenlyWithRemainderOnLastBundle() {
            Value value = new Value(adaToLovelace(10).add(BigInteger.ONE), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE),
                    multiAsset(POLICY_2, 1, BigInteger.ONE),
                    multiAsset(POLICY_3, 1, BigInteger.ONE)));

            List<Value> result = strategy.split(request(value));

            BigInteger bundleMin = minAda(new Value(adaToLovelace(1), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            BigInteger remaining = adaToLovelace(10).add(BigInteger.ONE).subtract(bundleMin.multiply(BigInteger.valueOf(3)));
            BigInteger perBundle = remaining.divide(BigInteger.valueOf(3));
            assertThat(result).extracting(Value::getCoin).containsExactly(
                    bundleMin.add(perBundle), bundleMin.add(perBundle),
                    bundleMin.add(perBundle).add(remaining.mod(BigInteger.valueOf(3))));
            assertValid(value, result);
        }

        @Test
        void singlePolicy_smallAda_singleOutput() {
            Value value = new Value(adaToLovelace(10), List.of(multiAsset(POLICY_1, 3, BigInteger.ONE)));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }

        @Test
        void policyLargerThanBundleSize_splitIntoChunks() {
            Value value = new Value(adaToLovelace(20), List.of(multiAsset(POLICY_1, 25, BigInteger.ONE)));

            List<Value> result = strategy.split(request(value));

            assertThat(result).extracting(v -> v.getMultiAssets().get(0).getAssets().size()).containsExactly(10, 10, 5);
            assertValid(value, result);
        }

        @Test
        void customBundleSize() {
            EvolutionStrategy custom = EvolutionStrategy.builder().bundleSize(2).build();
            Value value = new Value(adaToLovelace(20), List.of(multiAsset(POLICY_1, 5, BigInteger.ONE)));

            List<Value> result = custom.split(request(value));

            assertThat(result).extracting(v -> v.getMultiAssets().get(0).getAssets().size()).containsExactly(2, 2, 1);
            assertValid(value, result);
        }

        @Test
        void policiesAreNeverMixed() {
            Value value = new Value(adaToLovelace(20), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE),
                    multiAsset(POLICY_2, 1, BigInteger.ONE),
                    multiAsset(POLICY_3, 1, BigInteger.ONE)));

            List<Value> result = strategy.split(request(value));

            assertThat(result).hasSize(3).allSatisfy(v -> assertThat(v.getMultiAssets()).hasSize(1));
        }

        @Test
        void largeAda_bundlesAtMinAdaPlusSubdividedAda() {
            Value value = new Value(adaToLovelace(1000), List.of(
                    multiAsset(POLICY_1, 3, BigInteger.ONE),
                    multiAsset(POLICY_2, 1, BigInteger.valueOf(1000))));

            List<Value> result = strategy.split(request(value));

            assertThat(result).hasSize(2 + 7);
            assertThat(result.subList(0, 2)).allSatisfy(bundle -> assertThat(bundle.getCoin()).isEqualTo(minAda(bundle)));
            assertThat(result.subList(2, 9)).allSatisfy(v -> assertThat(v.getMultiAssets()).isNullOrEmpty());
            assertValid(value, result);
        }

        @Test
        void remainingAboveThreshold_subdivisionUnaffordable_bundlesPlusOneAdaOutput() {
            EvolutionStrategy custom = EvolutionStrategy.builder()
                    .subdivideThreshold(adaToLovelace(10))
                    .subdividePercentages(List.of(99, 1))
                    .build();
            Value value = new Value(adaToLovelace(20), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)));

            List<Value> result = custom.split(request(value));

            assertThat(withTokens(result)).hasSize(1);
            assertThat(adaOnly(result)).hasSize(1);
            assertValid(value, result);
        }

        @Test
        void bundlesUnaffordable_singleOutput() {
            Value value = new Value(adaToLovelace(2), List.of(
                    multiAsset(POLICY_1, 3, BigInteger.ONE),
                    multiAsset(POLICY_2, 3, BigInteger.ONE)));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }

        @Test
        void zeroQuantityAssetsIgnored() {
            MultiAsset withZero = new MultiAsset(POLICY_1, List.of(
                    new Asset("a", BigInteger.ONE), new Asset("b", BigInteger.ZERO)));
            Value value = new Value(adaToLovelace(1000), List.of(withZero, multiAsset(POLICY_2, 1, BigInteger.ONE)));

            List<Value> result = strategy.split(request(value));

            assertThat(withTokens(result)).flatExtracting(Value::getMultiAssets)
                    .flatExtracting(MultiAsset::getAssets)
                    .extracting(Asset::getName)
                    .doesNotContain("b");
            assertValid(value, result);
        }
    }

    @Nested
    class Untouched {

        @Test
        void negativeCoin() {
            Value value = Value.fromCoin(BigInteger.valueOf(-5));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }

        @Test
        void zeroCoin() {
            Value value = Value.fromCoin(BigInteger.ZERO);

            assertThat(strategy.split(request(value))).containsExactly(value);
        }

        @Test
        void negativeAsset() {
            Value value = new Value(adaToLovelace(1000), List.of(multiAsset(POLICY_1, 1, BigInteger.valueOf(-1))));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }
    }

    @Nested
    class Configuration {

        @Test
        void defaults_matchEvolutionSdk() {
            assertThat(strategy.getSubdivideThreshold()).isEqualTo(adaToLovelace(100));
            assertThat(strategy.getSubdividePercentages()).containsExactly(50, 15, 10, 10, 5, 5, 5);
            assertThat(strategy.getBundleSize()).isEqualTo(10);
        }

        @Test
        void percentagesNotSummingTo100_rejected() {
            assertThatThrownBy(() -> EvolutionStrategy.builder().subdividePercentages(List.of(50, 40)).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void emptyPercentages_rejected() {
            assertThatThrownBy(() -> EvolutionStrategy.builder().subdividePercentages(List.of()).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nonPositivePercentage_rejected() {
            assertThatThrownBy(() -> EvolutionStrategy.builder().subdividePercentages(List.of(110, -10)).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void bundleSizeBelowOne_rejected() {
            assertThatThrownBy(() -> EvolutionStrategy.builder().bundleSize(0).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void negativeThreshold_rejected() {
            assertThatThrownBy(() -> EvolutionStrategy.builder().subdivideThreshold(BigInteger.valueOf(-1)).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

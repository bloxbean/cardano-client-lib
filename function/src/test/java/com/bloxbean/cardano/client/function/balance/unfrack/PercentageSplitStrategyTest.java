package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PercentageSplitStrategyTest {
    PercentageSplitStrategy strategy = new PercentageSplitStrategy();

    @Nested
    class AdaOnly {

        @Test
        void belowThreshold_singleOutput() {
            Value value = Value.fromCoin(adaToLovelace(99));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }

        @Test
        void atThreshold_subdivided() {
            List<Value> result = strategy.split(request(Value.fromCoin(adaToLovelace(100))));

            assertThat(result).hasSize(7);
        }

        @Test
        void aboveThreshold_sameSlicesAsEvolution() {
            Value value = Value.fromCoin(BigInteger.valueOf(987_654_321L));

            List<Value> result = strategy.split(request(value));

            assertThat(result).isEqualTo(new EvolutionStrategy().split(request(value)));
            assertValid(value, result);
        }

        @Test
        void belowAdaOnlyMinAda_singleOutput() {
            Value value = Value.fromCoin(BigInteger.valueOf(500_000));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }

        @Test
        void smallestSliceBelowMinAda_singleOutput() {
            PercentageSplitStrategy custom = PercentageSplitStrategy.builder()
                    .subdivideThreshold(adaToLovelace(10))
                    .subdividePercentages(List.of(95, 5))
                    .build();
            Value value = Value.fromCoin(adaToLovelace(10));

            assertThat(custom.split(request(value))).containsExactly(value);
        }
    }

    @Nested
    class WithTokens {

        @Test
        void smallAda_keptAsSeparateAdaOutput_notSpreadAcrossBundles() {
            Value value = new Value(adaToLovelace(60), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE),
                    multiAsset(POLICY_2, 1, BigInteger.ONE),
                    multiAsset(POLICY_3, 1, BigInteger.ONE)));

            List<Value> result = strategy.split(request(value));

            // Evolution would spread ~57 ADA over the token bundles; here the bundles only hold their min-ada
            assertThat(adaOnly(result)).singleElement()
                    .satisfies(v -> assertThat(v.getCoin()).isGreaterThan(adaToLovelace(55)));
            assertThat(withTokens(result)).allSatisfy(b -> assertThat(b.getCoin()).isEqualTo(minAda(b)));
            assertValid(value, result);
        }

        @Test
        void smallPoliciesShareOneBundle_byByteBudget() {
            Value value = new Value(adaToLovelace(60), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE),
                    multiAsset(POLICY_2, 1, BigInteger.ONE),
                    multiAsset(POLICY_3, 1, BigInteger.ONE)));

            List<Value> result = strategy.split(request(value));

            assertThat(withTokens(result)).singleElement()
                    .satisfies(v -> assertThat(v.getMultiAssets()).hasSize(3));
        }

        @Test
        void largeAda_bundlesPlusSubdividedAda() {
            Value value = new Value(adaToLovelace(1000), List.of(multiAsset(POLICY_1, 3, BigInteger.ONE)));

            List<Value> result = strategy.split(request(value));

            assertThat(withTokens(result)).hasSize(1);
            assertThat(adaOnly(result)).hasSize(7);
            assertValid(value, result);
        }

        @Test
        void remainingBelowAdaOnlyMinAda_addedToLastBundle() {
            BigInteger bundleMin = minAda(new Value(adaToLovelace(1), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            Value value = new Value(bundleMin.add(BigInteger.valueOf(300_000)),
                    List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }

        @Test
        void remainingBelowAdaOnlyMinAda_twoBundles_addedToLastBundle() {
            PercentageSplitStrategy perPolicy = PercentageSplitStrategy.builder().tokenBundling(new PolicyBundling()).build();
            BigInteger bundleMin = minAda(new Value(adaToLovelace(1), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            Value value = new Value(bundleMin.multiply(BigInteger.TWO).add(BigInteger.valueOf(300_000)), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE), multiAsset(POLICY_2, 1, BigInteger.ONE)));

            List<Value> result = perPolicy.split(request(value));

            assertThat(result).hasSize(2);
            assertThat(adaOnly(result)).isEmpty();
            assertThat(result.get(1).getCoin()).isEqualTo(bundleMin.add(BigInteger.valueOf(300_000)));
            assertValid(value, result);
        }

        @Test
        void remainingExactlyZero_onlyBundles() {
            PercentageSplitStrategy perPolicy = PercentageSplitStrategy.builder().tokenBundling(new PolicyBundling()).build();
            BigInteger bundleMin = minAda(new Value(adaToLovelace(1), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            Value value = new Value(bundleMin.multiply(BigInteger.TWO), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE), multiAsset(POLICY_2, 1, BigInteger.ONE)));

            List<Value> result = perPolicy.split(request(value));

            assertThat(result).hasSize(2).allSatisfy(b -> assertThat(b.getCoin()).isEqualTo(bundleMin));
            assertValid(value, result);
        }

        @Test
        void bundlesUnaffordable_singleOutput() {
            Value value = new Value(adaToLovelace(1), List.of(multiAsset(POLICY_1, 60, BigInteger.ONE)));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }

        @Test
        void customTokenBundlingIsUsed() {
            PercentageSplitStrategy perPolicy = PercentageSplitStrategy.builder()
                    .tokenBundling(new PolicyBundling(1))
                    .build();
            Value value = new Value(adaToLovelace(60), List.of(multiAsset(POLICY_1, 3, BigInteger.ONE)));

            List<Value> result = perPolicy.split(request(value));

            assertThat(withTokens(result)).hasSize(3);
            assertValid(value, result);
        }
    }

    @Nested
    class Configuration {

        @Test
        void defaults() {
            assertThat(strategy.getSubdivideThreshold()).isEqualTo(adaToLovelace(100));
            assertThat(strategy.getSubdividePercentages()).containsExactly(50, 15, 10, 10, 5, 5, 5);
            assertThat(strategy.getTokenBundling()).isInstanceOf(ByteBudgetBundling.class);
        }

        @Test
        void invalidPercentages_rejected() {
            assertThatThrownBy(() -> PercentageSplitStrategy.builder().subdividePercentages(List.of(60, 60)).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void negativeThreshold_rejected() {
            assertThatThrownBy(() -> PercentageSplitStrategy.builder().subdivideThreshold(BigInteger.valueOf(-1)).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

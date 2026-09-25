package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EqualLanesStrategyTest {
    EqualLanesStrategy strategy = new EqualLanesStrategy();

    @Nested
    class AdaOnly {

        @Test
        void enoughAda_defaultFiveEqualLanes() {
            List<Value> result = strategy.split(request(Value.fromCoin(adaToLovelace(500))));

            assertThat(result).extracting(Value::getCoin).containsExactly(
                    adaToLovelace(100), adaToLovelace(100), adaToLovelace(100), adaToLovelace(100), adaToLovelace(100));
        }

        @Test
        void lastLaneGetsRoundingRemainder() {
            Value value = Value.fromCoin(adaToLovelace(500).add(BigInteger.valueOf(4)));

            List<Value> result = strategy.split(request(value));

            assertThat(result).hasSize(5);
            assertThat(result.subList(0, 4)).allSatisfy(v -> assertThat(v.getCoin()).isEqualTo(adaToLovelace(100)));
            assertThat(result.get(4).getCoin()).isEqualTo(adaToLovelace(100).add(BigInteger.valueOf(4)));
            assertValid(value, result);
        }

        @ParameterizedTest(name = "{0} ADA with 10 ADA lanes -> {1} lanes")
        @CsvSource({"9, 1", "10, 1", "19, 1", "20, 2", "35, 3", "49, 4", "50, 5", "5000, 5"})
        void numberOfLanesLimitedByMinLaneAmount(int ada, int expectedLanes) {
            Value value = Value.fromCoin(adaToLovelace(ada));

            List<Value> result = strategy.split(request(value));

            assertThat(result).hasSize(expectedLanes);
            assertValid(value, result);
            if (expectedLanes > 1)
                assertThat(result).allSatisfy(v -> assertThat(v.getCoin()).isGreaterThanOrEqualTo(adaToLovelace(10)));
        }

        @Test
        void minLaneAmountBelowMinAda_raisedToMinAda() {
            EqualLanesStrategy tiny = EqualLanesStrategy.builder().lanes(10).minLaneAmount(BigInteger.ONE).build();
            Value value = Value.fromCoin(adaToLovelace(3));

            List<Value> result = tiny.split(request(value));

            assertThat(result).hasSize(adaToLovelace(3).divide(adaOnlyMinAda()).intValueExact());
            assertValid(value, result);
        }

        @Test
        void singleLaneConfigured_neverSplits() {
            EqualLanesStrategy one = EqualLanesStrategy.builder().lanes(1).build();
            Value value = Value.fromCoin(adaToLovelace(10_000));

            assertThat(one.split(request(value))).containsExactly(value);
        }

        @Test
        void manyLanes() {
            EqualLanesStrategy many = EqualLanesStrategy.builder().lanes(20).minLaneAmount(adaToLovelace(5)).build();
            Value value = Value.fromCoin(adaToLovelace(1000));

            List<Value> result = many.split(request(value));

            assertThat(result).hasSize(20).allSatisfy(v -> assertThat(v.getCoin()).isEqualTo(adaToLovelace(50)));
        }

        @Test
        void belowAdaOnlyMinAda_singleOutput() {
            Value value = Value.fromCoin(BigInteger.valueOf(500_000));

            assertThat(strategy.split(request(value))).containsExactly(value);
        }
    }

    @Nested
    class WithTokens {

        @Test
        void tokensBundled_remainingAdaSplitIntoLanes() {
            Value value = new Value(adaToLovelace(100), List.of(
                    multiAsset(POLICY_1, 2, BigInteger.ONE), multiAsset(POLICY_2, 1, BigInteger.TEN)));

            List<Value> result = strategy.split(request(value));

            assertThat(withTokens(result)).singleElement().satisfies(b -> assertThat(b.getCoin()).isEqualTo(minAda(b)));
            assertThat(adaOnly(result)).hasSize(5);
            assertValid(value, result);
        }

        @Test
        void tokensWithLittleAda_bundlePlusOneAdaOutput() {
            Value value = new Value(adaToLovelace(15), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)));

            List<Value> result = strategy.split(request(value));

            assertThat(withTokens(result)).hasSize(1);
            assertThat(adaOnly(result)).hasSize(1);
            assertValid(value, result);
        }
    }

    @Nested
    class Configuration {

        @Test
        void defaults() {
            assertThat(strategy.getLanes()).isEqualTo(5);
            assertThat(strategy.getMinLaneAmount()).isEqualTo(adaToLovelace(10));
            assertThat(strategy.getTokenBundling()).isInstanceOf(ByteBudgetBundling.class);
        }

        @Test
        void lanesBelowOne_rejected() {
            assertThatThrownBy(() -> EqualLanesStrategy.builder().lanes(0).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void negativeMinLaneAmount_rejected() {
            assertThatThrownBy(() -> EqualLanesStrategy.builder().minLaneAmount(BigInteger.valueOf(-1)).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

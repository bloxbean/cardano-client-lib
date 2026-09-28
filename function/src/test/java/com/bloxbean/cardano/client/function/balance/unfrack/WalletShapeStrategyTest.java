package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.function.BiFunction;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class WalletShapeStrategyTest {
    UtxoSupplier emptyWallet = mock(UtxoSupplier.class);

    {
        given(emptyWallet.getAll(ADDRESS)).willReturn(List.of());
    }

    @Nested
    class Tokens {
        WalletShapeStrategy single = strategy(WalletShape.builder().ada(AdaShape.single()).build());

        @Test
        void adaKeptSeparate_notSpreadAcrossBundles() {
            Value change = new Value(adaToLovelace(60), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE),
                    multiAsset(POLICY_2, 1, BigInteger.ONE),
                    multiAsset(POLICY_3, 1, BigInteger.ONE)));

            List<Value> result = single.split(request(change));

            assertThat(adaOnly(result)).singleElement()
                    .satisfies(v -> assertThat(v.getCoin()).isGreaterThan(adaToLovelace(55)));
            assertThat(withTokens(result)).allSatisfy(b -> assertThat(b.getCoin()).isEqualTo(minAda(b)));
            assertValid(change, result);
        }

        @Test
        void byteBudget_smallPoliciesShareOneBundle() {
            Value change = new Value(adaToLovelace(60), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE),
                    multiAsset(POLICY_2, 1, BigInteger.ONE),
                    multiAsset(POLICY_3, 1, BigInteger.ONE)));

            List<Value> result = single.split(request(change));

            assertThat(withTokens(result)).singleElement().satisfies(v -> assertThat(v.getMultiAssets()).hasSize(3));
        }

        @Test
        void policyBundling_onePolicyPerOutput() {
            WalletShapeStrategy perPolicy = strategy(WalletShape.builder()
                    .ada(AdaShape.single()).tokens(new PolicyBundling()).build());
            Value change = new Value(adaToLovelace(60), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE),
                    multiAsset(POLICY_2, 1, BigInteger.ONE),
                    multiAsset(POLICY_3, 1, BigInteger.ONE)));

            List<Value> result = perPolicy.split(request(change));

            assertThat(withTokens(result)).hasSize(3).allSatisfy(v -> assertThat(v.getMultiAssets()).hasSize(1));
            assertValid(change, result);
        }

        @Test
        void remainingBelowAdaOnlyMinAda_addedToLastBundle() {
            WalletShapeStrategy perPolicy = strategy(WalletShape.builder()
                    .ada(AdaShape.single()).tokens(new PolicyBundling()).build());
            BigInteger bundleMin = minAda(new Value(adaToLovelace(1), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            Value change = new Value(bundleMin.multiply(BigInteger.TWO).add(BigInteger.valueOf(300_000)), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE), multiAsset(POLICY_2, 1, BigInteger.ONE)));

            List<Value> result = perPolicy.split(request(change));

            assertThat(result).hasSize(2);
            assertThat(adaOnly(result)).isEmpty();
            assertThat(result.get(1).getCoin()).isEqualTo(bundleMin.add(BigInteger.valueOf(300_000)));
            assertValid(change, result);
        }

        @Test
        void remainingExactlyZero_onlyBundles() {
            WalletShapeStrategy perPolicy = strategy(WalletShape.builder()
                    .ada(AdaShape.single()).tokens(new PolicyBundling()).build());
            BigInteger bundleMin = minAda(new Value(adaToLovelace(1), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            Value change = new Value(bundleMin.multiply(BigInteger.TWO), List.of(
                    multiAsset(POLICY_1, 1, BigInteger.ONE), multiAsset(POLICY_2, 1, BigInteger.ONE)));

            List<Value> result = perPolicy.split(request(change));

            assertThat(result).hasSize(2).allSatisfy(b -> assertThat(b.getCoin()).isEqualTo(bundleMin));
        }

        @Test
        void singleBundle_smallRemainder_untouched() {
            BigInteger bundleMin = minAda(new Value(adaToLovelace(1), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            Value change = new Value(bundleMin.add(BigInteger.valueOf(300_000)), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)));

            assertThat(single.split(request(change))).containsExactly(change);
        }

        @Test
        void bundlesUnaffordable_untouched() {
            Value change = new Value(adaToLovelace(1), List.of(multiAsset(POLICY_1, 60, BigInteger.ONE)));

            assertThat(single.split(request(change))).containsExactly(change);
        }
    }

    @Nested
    class AdaShapes {

        @Test
        void lanes_withTokens_bundlePlusMissingLanes() {
            WalletShapeStrategy lanes = strategy(WalletShape.throughput(3, Amount.ada(10)));
            Value change = new Value(adaToLovelace(100), List.of(multiAsset(POLICY_1, 2, BigInteger.ONE)));

            List<Value> result = lanes.split(request(change, tx(), emptyWallet));

            assertThat(withTokens(result)).hasSize(1);
            assertThat(adaOnly(result)).extracting(Value::getCoin).startsWith(adaToLovelace(10), adaToLovelace(10)).hasSize(3);
            assertValid(change, result);
        }

        @Test
        void percentages_withTokens_bundlePlusSlices() {
            WalletShapeStrategy offline = strategy(WalletShape.offline());
            Value change = new Value(adaToLovelace(1000), List.of(multiAsset(POLICY_1, 3, BigInteger.ONE)));

            List<Value> result = offline.split(request(change));

            assertThat(withTokens(result)).hasSize(1);
            assertThat(adaOnly(result)).hasSize(7);
            assertValid(change, result);
        }

        @Test
        void percentages_adaOnly_sameSlicesAsEvolution() {
            Value change = Value.fromCoin(BigInteger.valueOf(987_654_321L));

            assertThat(strategy(WalletShape.offline()).split(request(change)))
                    .isEqualTo(new EvolutionStrategy().split(request(change)));
        }

        @Test
        void single_adaOnly_untouched() {
            Value change = Value.fromCoin(adaToLovelace(10_000));

            assertThat(strategy(WalletShape.builder().ada(AdaShape.single()).build()).split(request(change)))
                    .containsExactly(change);
        }

        @Test
        void belowAdaOnlyMinAda_untouched() {
            Value change = Value.fromCoin(BigInteger.valueOf(500_000));

            assertThat(strategy(WalletShape.offline()).split(request(change))).containsExactly(change);
        }
    }

    @Nested
    class Profiles {
        Value change = new Value(adaToLovelace(1000), List.of(
                multiAsset(POLICY_1, 1, BigInteger.ONE), multiAsset(POLICY_2, 1, BigInteger.ONE)));

        @Test
        void hygiene_oneBundleAndThreeLanes() {
            List<Value> result = strategy(WalletShape.hygiene()).split(request(change, tx(), emptyWallet));

            assertThat(withTokens(result)).hasSize(1);
            assertThat(adaOnly(result)).extracting(Value::getCoin).startsWith(adaToLovelace(50), adaToLovelace(50)).hasSize(3);
            assertValid(change, result);
        }

        @Test
        void throughput_oneBundleAndConfiguredLanes() {
            List<Value> result = strategy(WalletShape.throughput(10, Amount.ada(60))).split(request(change, tx(), emptyWallet));

            assertThat(adaOnly(result)).hasSize(10);
            assertThat(adaOnly(result).subList(0, 9)).allSatisfy(v -> assertThat(v.getCoin()).isEqualTo(adaToLovelace(60)));
        }

        @Test
        void collector_oneBundleAndTwoLanes() {
            List<Value> result = strategy(WalletShape.collector()).split(request(change, tx(), emptyWallet));

            assertThat(withTokens(result)).hasSize(1);
            assertThat(adaOnly(result)).extracting(Value::getCoin).startsWith(adaToLovelace(20)).hasSize(2);
        }

        @Test
        void dex_bundlePerPolicy() {
            List<Value> result = strategy(WalletShape.dex()).split(request(change, tx(), emptyWallet));

            assertThat(withTokens(result)).hasSize(2).allSatisfy(v -> assertThat(v.getMultiAssets()).hasSize(1));
            assertThat(adaOnly(result)).hasSize(3);
        }

        @Test
        void offline_percentageSlices_withoutWallet() {
            List<Value> result = strategy(WalletShape.offline()).split(request(change));

            assertThat(adaOnly(result)).hasSize(7);
        }
    }

    @Nested
    class BaseClassGuards {

        @Test
        void splitAdaReturningNull_rejected() {
            assertThatThrownBy(() -> stub((l, r) -> null).split(request(Value.fromCoin(adaToLovelace(100)))))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void splitAdaReturningEmpty_rejected() {
            assertThatThrownBy(() -> stub((l, r) -> List.of()).split(request(Value.fromCoin(adaToLovelace(100)))))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void splitAdaNotSummingUp_rejected() {
            assertThatThrownBy(() -> stub((l, r) -> List.of(adaToLovelace(10), adaToLovelace(10)))
                    .split(request(Value.fromCoin(adaToLovelace(100)))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("split");
        }

        @Test
        void nullTokenBundling_defaultsToByteBudget() {
            assertThat(stub((l, r) -> List.of(l)).getTokenBundling()).isInstanceOf(ByteBudgetBundling.class);
        }

        private AbstractChangeSplitStrategy stub(BiFunction<BigInteger, ChangeSplitRequest, List<BigInteger>> splitAda) {
            return new AbstractChangeSplitStrategy(null) {
                @Override
                protected List<BigInteger> splitAda(BigInteger lovelace, ChangeSplitRequest request) {
                    return splitAda.apply(lovelace, request);
                }
            };
        }
    }

    @Test
    void nullShape_rejected() {
        assertThatThrownBy(() -> new WalletShapeStrategy(null)).isInstanceOf(NullPointerException.class);
    }

    private static WalletShapeStrategy strategy(WalletShape shape) {
        return new WalletShapeStrategy(shape);
    }
}

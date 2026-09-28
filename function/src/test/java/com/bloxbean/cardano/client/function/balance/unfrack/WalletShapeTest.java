package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.model.Amount;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletShapeTest {

    @Nested
    class Profiles {

        @Test
        void builderDefaults() {
            WalletShape shape = WalletShape.builder().build();

            assertThat(shape.getAda()).isEqualTo(new AdaShape.Lanes(3, adaToLovelace(50)));
            assertThat(shape.getTokens()).isInstanceOf(ByteBudgetBundling.class);
            assertThat(shape.getConsolidation()).isEqualTo(Consolidation.none());
        }

        @Test
        void hygiene_lanesBundlesAndOpportunisticConsolidation() {
            WalletShape shape = WalletShape.hygiene();

            assertThat(shape.getAda()).isEqualTo(new AdaShape.Lanes(3, adaToLovelace(50)));
            assertThat(shape.getTokens()).isInstanceOf(ByteBudgetBundling.class);
            assertThat(shape.getConsolidation()).isEqualTo(new Consolidation(3, adaToLovelace(5)));
        }

        @Test
        void throughput_configuredLanes_noConsolidation() {
            WalletShape shape = WalletShape.throughput(10, Amount.ada(60));

            assertThat(shape.getAda()).isEqualTo(new AdaShape.Lanes(10, adaToLovelace(60)));
            assertThat(shape.getTokens()).isInstanceOf(ByteBudgetBundling.class);
            assertThat(shape.getConsolidation().isEnabled()).isFalse();
        }

        @Test
        void collector_fewLanes_aggressiveConsolidation() {
            WalletShape shape = WalletShape.collector();

            assertThat(shape.getAda()).isEqualTo(new AdaShape.Lanes(2, adaToLovelace(20)));
            assertThat(shape.getConsolidation()).isEqualTo(new Consolidation(20, adaToLovelace(10)));
        }

        @Test
        void dex_onePolicyPerUtxo() {
            WalletShape shape = WalletShape.dex();

            assertThat(shape.getTokens()).isInstanceOf(PolicyBundling.class);
            assertThat(shape.getConsolidation().isEnabled()).isFalse();
        }

        @Test
        void offline_statelessPercentages() {
            WalletShape shape = WalletShape.offline();

            assertThat(shape.getAda()).isEqualTo(
                    new AdaShape.Percentages(adaToLovelace(100), List.of(50, 15, 10, 10, 5, 5, 5)));
            assertThat(shape.getConsolidation().isEnabled()).isFalse();
        }

        @Test
        void toBuilder_changesOneDimension() {
            WalletShape shape = WalletShape.hygiene().toBuilder().tokens(new PolicyBundling(5)).build();

            assertThat(shape.getAda()).isEqualTo(WalletShape.hygiene().getAda());
            assertThat(shape.getConsolidation()).isEqualTo(WalletShape.hygiene().getConsolidation());
            assertThat(((PolicyBundling) shape.getTokens()).getBundleSize()).isEqualTo(5);
        }

        @Test
        void nullDimension_rejected() {
            assertThatThrownBy(() -> WalletShape.builder().ada(null).build()).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> WalletShape.builder().tokens(null).build()).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> WalletShape.builder().consolidation(null).build()).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ConsolidationConfig {

        @Test
        void none_isDisabled() {
            assertThat(Consolidation.none().isEnabled()).isFalse();
        }

        @Test
        void opportunistic_defaultMaxUtxoOf5Ada() {
            assertThat(Consolidation.opportunistic(4)).isEqualTo(new Consolidation(4, adaToLovelace(5)));
            assertThat(Consolidation.opportunistic(4).isEnabled()).isTrue();
        }

        @Test
        void opportunistic_customMaxUtxo() {
            assertThat(Consolidation.opportunistic(2, Amount.ada(7))).isEqualTo(new Consolidation(2, adaToLovelace(7)));
        }

        @Test
        void invalid_rejected() {
            assertThatThrownBy(() -> new Consolidation(-1, BigInteger.ONE)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Consolidation(1, BigInteger.valueOf(-1))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Consolidation.opportunistic(1, Amount.asset("ab", 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

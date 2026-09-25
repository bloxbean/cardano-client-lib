package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyBundlingTest {

    @Test
    void onePolicy_belowBundleSize_oneBundle() {
        List<List<MultiAsset>> bundles = new PolicyBundling().bundle(List.of(multiAsset(POLICY_1, 3, BigInteger.ONE)));

        assertThat(bundles).singleElement().satisfies(b -> assertThat(b).singleElement()
                .satisfies(ma -> assertThat(ma.getAssets()).hasSize(3)));
    }

    @Test
    void onePolicy_exactlyBundleSize_oneBundle() {
        List<List<MultiAsset>> bundles = new PolicyBundling().bundle(List.of(multiAsset(POLICY_1, 10, BigInteger.ONE)));

        assertThat(bundles).hasSize(1);
    }

    @Test
    void onePolicy_aboveBundleSize_chunkedInOrder() {
        List<List<MultiAsset>> bundles = new PolicyBundling().bundle(List.of(multiAsset(POLICY_1, 25, BigInteger.ONE)));

        assertThat(bundles).extracting(b -> b.get(0).getAssets().size()).containsExactly(10, 10, 5);
        assertThat(bundles.get(1).get(0).getAssets().get(0).getName()).isEqualTo("token10");
    }

    @Test
    void severalPolicies_neverMixed_inputOrderKept() {
        List<List<MultiAsset>> bundles = new PolicyBundling().bundle(List.of(
                multiAsset(POLICY_2, 1, BigInteger.ONE),
                multiAsset(POLICY_1, 1, BigInteger.ONE),
                multiAsset(POLICY_3, 1, BigInteger.ONE)));

        assertThat(bundles).allSatisfy(b -> assertThat(b).hasSize(1));
        assertThat(bundles).extracting(b -> b.get(0).getPolicyId()).containsExactly(POLICY_2, POLICY_1, POLICY_3);
    }

    @Test
    void manySmallPolicies_oneBundleEach() {
        List<MultiAsset> tokens = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++)
            tokens.add(multiAsset(policy(i), 1, BigInteger.ONE));

        assertThat(new PolicyBundling().bundle(tokens)).hasSize(50);
    }

    @Test
    void bundleSizeOne_oneAssetPerBundle() {
        List<List<MultiAsset>> bundles = new PolicyBundling(1).bundle(List.of(multiAsset(POLICY_1, 4, BigInteger.ONE)));

        assertThat(bundles).hasSize(4).allSatisfy(b -> assertThat(b.get(0).getAssets()).hasSize(1));
    }

    @Test
    void quantitiesPreserved() {
        List<List<MultiAsset>> bundles = new PolicyBundling(2).bundle(List.of(multiAsset(POLICY_1, 3, BigInteger.valueOf(777))));

        assertThat(bundles).flatExtracting(b -> b.get(0).getAssets())
                .allSatisfy(a -> assertThat(a.getValue()).isEqualTo(BigInteger.valueOf(777)))
                .hasSize(3);
    }

    @Test
    void invalidBundleSize_rejected() {
        assertThatThrownBy(() -> new PolicyBundling(0)).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByteBudgetBundlingTest {
    ByteBudgetBundling bundling = new ByteBudgetBundling();

    @Test
    void smallPoliciesShareOneBundle() {
        List<List<MultiAsset>> bundles = bundling.bundle(List.of(
                multiAsset(POLICY_1, 1, BigInteger.ONE),
                multiAsset(POLICY_2, 2, BigInteger.ONE),
                multiAsset(POLICY_3, 1, BigInteger.ONE)));

        assertThat(bundles).singleElement().satisfies(b -> assertThat(b).hasSize(3));
    }

    @Test
    void manySmallPolicies_packedIntoFewBundlesWithinBudget() {
        List<MultiAsset> tokens = new ArrayList<>();
        for (int i = 0; i < 150; i++)
            tokens.add(multiAsset(policy(i), 1, BigInteger.ONE));

        List<List<MultiAsset>> bundles = bundling.bundle(tokens);

        assertThat(bundles).hasSizeLessThan(15);
        assertThat(bundles).allSatisfy(b -> assertThat(ChangeValues.serializedSize(b)).isLessThanOrEqualTo(1000));
        assertEveryAssetOnce(tokens, bundles);
    }

    @Test
    void policyLargerThanBudget_chunkedWithinBudget_keepingAssetOrder() {
        MultiAsset large = multiAsset(POLICY_1, 200, BigInteger.ONE);

        List<List<MultiAsset>> bundles = bundling.bundle(List.of(large));

        assertThat(bundles).hasSizeGreaterThan(1);
        assertThat(bundles).allSatisfy(b -> {
            assertThat(b).singleElement().satisfies(ma -> assertThat(ma.getPolicyId()).isEqualTo(POLICY_1));
            assertThat(ChangeValues.serializedSize(b)).isLessThanOrEqualTo(1000);
        });
        List<String> names = bundles.stream().flatMap(b -> b.get(0).getAssets().stream()).map(Asset::getName).toList();
        assertThat(names).isEqualTo(large.getAssets().stream().map(Asset::getName).toList());
    }

    @Test
    void policyThatFits_neverSplit() {
        List<List<MultiAsset>> bundles = bundling.bundle(List.of(
                multiAsset(POLICY_1, 20, BigInteger.ONE),
                multiAsset(POLICY_2, 20, BigInteger.ONE)));

        long policy1Bundles = bundles.stream()
                .filter(b -> b.stream().anyMatch(ma -> ma.getPolicyId().equals(POLICY_1)))
                .count();
        assertThat(policy1Bundles).isEqualTo(1);
    }

    @Test
    void noBundleRepeatsAPolicy() {
        List<MultiAsset> tokens = new ArrayList<>();
        tokens.add(multiAsset(POLICY_1, 150, BigInteger.ONE));
        for (int i = 0; i < 40; i++)
            tokens.add(multiAsset(policy(i), 3, BigInteger.valueOf(1_000_000)));

        List<List<MultiAsset>> bundles = bundling.bundle(tokens);

        assertThat(bundles).allSatisfy(b ->
                assertThat(b.stream().map(MultiAsset::getPolicyId).distinct().count()).isEqualTo(b.size()));
        assertEveryAssetOnce(tokens, bundles);
    }

    @Test
    void assetLargerThanBudget_getsOwnBundle() {
        ByteBudgetBundling tiny = new ByteBudgetBundling(10);

        List<List<MultiAsset>> bundles = tiny.bundle(List.of(multiAsset(POLICY_1, 3, BigInteger.ONE)));

        assertThat(bundles).hasSize(3).allSatisfy(b -> assertThat(b.get(0).getAssets()).hasSize(1));
    }

    @Test
    void smallerBudget_moreBundles() {
        List<MultiAsset> tokens = new ArrayList<>();
        for (int i = 0; i < 30; i++)
            tokens.add(multiAsset(policy(i), 1, BigInteger.ONE));

        int large = new ByteBudgetBundling(2000).bundle(tokens).size();
        int small = new ByteBudgetBundling(200).bundle(tokens).size();

        assertThat(small).isGreaterThan(large);
    }

    @Test
    void deterministic() {
        List<MultiAsset> tokens = new ArrayList<>();
        for (int i = 0; i < 60; i++)
            tokens.add(multiAsset(policy(i), 1 + i % 7, BigInteger.valueOf(i + 1)));

        assertThat(bundling.bundle(tokens)).usingRecursiveComparison().isEqualTo(bundling.bundle(tokens));
    }

    @Test
    void inputNotModified() {
        MultiAsset policy = multiAsset(POLICY_1, 200, BigInteger.ONE);

        bundling.bundle(List.of(policy));

        assertThat(policy.getAssets()).hasSize(200);
    }

    @Test
    void invalidBudget_rejected() {
        assertThatThrownBy(() -> new ByteBudgetBundling(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertEveryAssetOnce(List<MultiAsset> input, List<List<MultiAsset>> bundles) {
        Map<String, BigInteger> expected = new HashMap<>();
        input.forEach(ma -> ma.getAssets().forEach(a -> expected.merge(ma.getPolicyId() + a.getName(), a.getValue(), BigInteger::add)));
        Map<String, BigInteger> actual = new HashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        bundles.forEach(b -> b.forEach(ma -> ma.getAssets().forEach(a -> {
            actual.merge(ma.getPolicyId() + a.getName(), a.getValue(), BigInteger::add);
            occurrences.merge(ma.getPolicyId() + a.getName(), 1, Integer::sum);
        })));
        assertThat(actual).isEqualTo(expected);
        assertThat(occurrences.values()).allSatisfy(n -> assertThat(n).isEqualTo(1));
    }
}

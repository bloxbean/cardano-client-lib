package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiAssetSpecTest {

    @Test
    void addSameAsset() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset moreAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(300L)).build();
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, asset2)).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, moreAsset2)).build();

        MultiAsset expectedMultiAsset = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(
                asset1.add(asset1),
                asset2.add(moreAsset2)
        )).build();

        assertThat(multiAsset1.add(multiAsset2), equalTo(expectedMultiAsset));
        assertEquals(multiAsset1.add(multiAsset2).hashCode(), expectedMultiAsset.hashCode());
    }

    @Test
    void addSameAsset2() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset moreAsset2 = Asset.builder().name("0x"+HexUtil.encodeHexString("asset2".getBytes(StandardCharsets.UTF_8))).value(BigInteger.valueOf(300L)).build();
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, asset2)).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, moreAsset2)).build();

        MultiAsset expectedMultiAsset = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(
                asset1.add(asset1),
                asset2.add(moreAsset2)
        )).build();

        assertThat(multiAsset1.add(multiAsset2), equalTo(expectedMultiAsset));
    }

    @Test
    void addMultiAssetWithDifferentPolicyThrowsError() {
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy1").assets(Arrays.asList()).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy2").assets(Arrays.asList()).build();

        assertThrows(IllegalArgumentException.class, () -> multiAsset1.add(multiAsset2));
    }

    @Test
    void subtractSameAsset() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset moreAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(50L)).build();
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, asset2)).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, moreAsset2)).build();

        MultiAsset expectedMultiAsset = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(
                asset1.subtract(asset1),
                asset2.subtract(moreAsset2)
        )).build();

        assertThat(multiAsset1.subtract(multiAsset2), equalTo(expectedMultiAsset));
    }

    @Test
    void subtractSameAssetWhenOneAssetNameIsHex() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset moreAsset2 = Asset.builder().name("0x617373657432").value(BigInteger.valueOf(50L)).build(); //asset2
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, asset2)).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, moreAsset2)).build();

        MultiAsset expectedMultiAsset = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(
                asset1.subtract(asset1),
                asset2.subtract(moreAsset2)
        )).build();

        assertThat(multiAsset1.subtract(multiAsset2), equalTo(expectedMultiAsset));
    }

    @Test
    void subtractSameAssetButWhenAdditionalAssetsInFirstMultiAsset() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset asset3 = Asset.builder().name("asset3").value(BigInteger.valueOf(500L)).build();
        Asset moreAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(50L)).build();
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, asset2, asset3)).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, moreAsset2)).build();

        MultiAsset expectedMultiAsset = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(
                asset1.subtract(asset1),
                asset2.subtract(moreAsset2),
                asset3
        )).build();

        assertThat(multiAsset1.subtract(multiAsset2), equalTo(expectedMultiAsset));
    }

    @Test
    void subtractMultiAssetWithDifferentPolicyThrowsError() {
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy1").assets(Arrays.asList()).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy2").assets(Arrays.asList()).build();

        assertThrows(IllegalArgumentException.class, () -> multiAsset1.subtract(multiAsset2));
    }

    @Test
    void subtractMultiAssetLists() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset asset3 = Asset.builder().name("asset3").value(BigInteger.valueOf(55L)).build(); //asset2
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, asset2)).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(asset3)).build();

        List<MultiAsset> list1 = Arrays.asList(multiAsset1, multiAsset2);

        Asset asset4 = Asset.builder().name("asset1").value(BigInteger.valueOf(50L)).build();
        Asset asset5 = Asset.builder().name("asset2").value(BigInteger.valueOf(20L)).build();
        Asset asset6 = Asset.builder().name("asset3").value(BigInteger.valueOf(50L)).build(); //asset2
        MultiAsset multiAsset3 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset4, asset5)).build();
        MultiAsset multiAsset4 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(asset6)).build();

        List<MultiAsset> list2 = Arrays.asList(multiAsset3, multiAsset4);

        List<MultiAsset> result = MultiAsset.subtractMultiAssetLists(list1, list2);

        Asset exAsset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(50L)).build();
        Asset exAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(180L)).build();
        Asset exAsset3 = Asset.builder().name("asset3").value(BigInteger.valueOf(5L)).build(); //asset2
        MultiAsset exMultiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(exAsset1, exAsset2)).build();
        MultiAsset exMultiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(exAsset3)).build();
        List<MultiAsset> exList = Arrays.asList(exMultiAsset1, exMultiAsset2);

        assertThat(result, equalTo(exList));
    }

    @Test
    void subtractMultiAssetListsWhenAdditionalMultiAssetInFirstList() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset asset3 = Asset.builder().name("asset3").value(BigInteger.valueOf(55L)).build(); //asset2
        Asset asset4 = Asset.builder().name("asset4").value(BigInteger.valueOf(60L)).build();
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, asset2)).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(asset3)).build();
        MultiAsset multiAsset3 = MultiAsset.builder().policyId("policy_id3").assets(Arrays.asList(asset4)).build();

        List<MultiAsset> list1 = Arrays.asList(multiAsset1, multiAsset2, multiAsset3);

        Asset otherAsset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(50L)).build();
        Asset otherAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(20L)).build();
        Asset otherAsset3 = Asset.builder().name("asset3").value(BigInteger.valueOf(50L)).build(); //asset2
        MultiAsset otherMultiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(otherAsset1, otherAsset2)).build();
        MultiAsset otherMultiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(otherAsset3)).build();

        List<MultiAsset> list2 = Arrays.asList(otherMultiAsset1, otherMultiAsset2);

        List<MultiAsset> result = MultiAsset.subtractMultiAssetLists(list1, list2);

        Asset exAsset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(50L)).build();
        Asset exAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(180L)).build();
        Asset exAsset3 = Asset.builder().name("asset3").value(BigInteger.valueOf(5L)).build();
        Asset exAsset4 = Asset.builder().name("asset4").value(BigInteger.valueOf(60L)).build();
        MultiAsset exMultiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(exAsset1, exAsset2)).build();
        MultiAsset exMultiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(exAsset3)).build();
        MultiAsset exMultiAsset3 = MultiAsset.builder().policyId("policy_id3").assets(Arrays.asList(exAsset4)).build();
        List<MultiAsset> exList = Arrays.asList(exMultiAsset1, exMultiAsset2, exMultiAsset3);

        assertThat(result, equalTo(exList));
    }
}

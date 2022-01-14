package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class ValueSpecTest {

    @Test
    void addTwoLovelacesValues() {
        Value lovelaceValue1 = new Value();
        lovelaceValue1.setCoin(BigInteger.valueOf(1000000L));

        Value lovelaceValue2 = new Value();
        lovelaceValue2.setCoin(BigInteger.valueOf(1500000L));

        Value actualValue = lovelaceValue1.plus(lovelaceValue2);
        Value expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(2500000L));
        expectedValue.setMultiAssets(Arrays.asList());

        assertThat(actualValue, equalTo(expectedValue));
    }

    @Test
    void addMultiAssetToLovelacesValues() {
        Value lovelaceValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(Arrays.asList()).build();

        List<MultiAsset> testMultiAssets = Arrays.asList(MultiAsset.builder().policyId("policy_ud").assets(Arrays.asList(Asset.builder().name("asset_name").value(BigInteger.valueOf(123456L)).build())).build());

        Value multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets).build();

        Value actualValue = lovelaceValue.plus(multiAssetValue);
        Value expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));
        expectedValue.setMultiAssets(testMultiAssets);

        assertThat(actualValue, equalTo(expectedValue));
    }

    @Test
    void addDifferentMultiAssetsToLovelacesValues() {
        List<MultiAsset> testMultiAssets1 = Arrays.asList(MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(Asset.builder().name("asset_name1").value(BigInteger.valueOf(1000000L)).build())).build());
        List<MultiAsset> testMultiAssets2 = Arrays.asList(MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(Asset.builder().name("asset_name2").value(BigInteger.valueOf(2000000L)).build())).build());

        Value lovelaceAndMultiAssetValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(testMultiAssets1).build();

        Value multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets2).build();

        Value actualValue = lovelaceAndMultiAssetValue.plus(multiAssetValue);
        Value expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));

        expectedValue.setMultiAssets(MultiAsset.mergeMultiAssetLists(testMultiAssets1, testMultiAssets2));

        assertThat(actualValue, equalTo(expectedValue));
    }


    @Test
    void addSamePolicyMultiAssetsToLovelacesValues() {
        List<MultiAsset> testMultiAssets1 = Arrays.asList(MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(Asset.builder().name("asset_name1").value(BigInteger.valueOf(1000000L)).build())).build());
        List<MultiAsset> testMultiAssets2 = Arrays.asList(MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(Asset.builder().name("asset_name2").value(BigInteger.valueOf(2000000L)).build())).build());

        Value lovelaceAndMultiAssetValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(testMultiAssets1).build();

        Value multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets2).build();

        Value actualValue = lovelaceAndMultiAssetValue.plus(multiAssetValue);
        Value expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));

        List<MultiAsset> testMultiAssets = MultiAsset.mergeMultiAssetLists(testMultiAssets1, testMultiAssets2);
        expectedValue.setMultiAssets(testMultiAssets);

        assertThat(actualValue, equalTo(expectedValue));
    }

    @Test
    void addSameMultiAssetsToLovelacesValues() {
        List<MultiAsset> testMultiAssets1 = Arrays.asList(MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(Asset.builder().name("asset_name1").value(BigInteger.valueOf(1000000L)).build())).build());
        List<MultiAsset> testMultiAssets2 = Arrays.asList(MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(Asset.builder().name("asset_name1").value(BigInteger.valueOf(2000000L)).build())).build());

        Value lovelaceAndMultiAssetValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(testMultiAssets1).build();

        Value multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets2).build();

        Value actualValue = lovelaceAndMultiAssetValue.plus(multiAssetValue);
        Value expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));
        expectedValue.setMultiAssets(MultiAsset.mergeMultiAssetLists(testMultiAssets1, testMultiAssets2));

        assertThat(actualValue, equalTo(expectedValue));
    }


    @Test
    void SubtractValuesWithMultiAssetsList() {
        Asset l1asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset l1asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset l1moreAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(100L)).build();
        MultiAsset l1multiAsset1 = MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(l1asset1, l1asset2)).build();
        MultiAsset l1multiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(l1asset1, l1moreAsset2)).build();
        List<MultiAsset> multiAssetList1 = Arrays.asList(l1multiAsset1,l1multiAsset2);

        Value value1 = Value.builder().coin(BigInteger.valueOf(3000000L)).multiAssets(multiAssetList1).build();

        Asset l2asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset l2asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset l2moreAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(50L)).build();
        MultiAsset l2multiAsset1 = MultiAsset.builder().policyId("policy_id1").assets(Arrays.asList(l2asset1, l2asset2)).build();
        MultiAsset l2multiAsset2 = MultiAsset.builder().policyId("policy_id2").assets(Arrays.asList(l2asset1, l2moreAsset2)).build();
        List<MultiAsset> multiAssetList2 = Arrays.asList(l2multiAsset1,l2multiAsset2);

        Value value2 = Value.builder().coin(BigInteger.valueOf(2000000L)).multiAssets(multiAssetList2).build();

        List<MultiAsset> expectedMultiAssetList = Arrays.asList(l1multiAsset1.minus(l2multiAsset1),l1multiAsset2.minus(l2multiAsset2));
        Value expectedValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(expectedMultiAssetList).build();

        assertThat(MultiAsset.subtractMultiAssetLists(multiAssetList1,multiAssetList2), equalTo(expectedMultiAssetList));
        assertThat(value1.minus(value2), equalTo(expectedValue));
    }

}

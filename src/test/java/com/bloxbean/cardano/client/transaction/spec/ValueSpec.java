package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ValueSpec {

    @Test
    public void addTwoLovelacesValues() {
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
    public void addMultiAssetToLovelacesValues() {

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
    public void addDifferentMultiAssetsToLovelacesValues() {

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
    public void addSamePolicyMultiAssetsToLovelacesValues() {

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
    public void addSameMultiAssetsToLovelacesValues() {

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


}

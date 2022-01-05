package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ValueSpec {

    @Test
    public void addTwoLovelacesValues() {
        var lovelaceValue1 = new Value();
        lovelaceValue1.setCoin(BigInteger.valueOf(1000000L));

        var lovelaceValue2 = new Value();
        lovelaceValue2.setCoin(BigInteger.valueOf(1500000L));

        var actualValue = lovelaceValue1.plus(lovelaceValue2);
        var expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(2500000L));
        expectedValue.setMultiAssets(List.of());

        assertThat(actualValue, equalTo(expectedValue));

    }

    @Test
    public void addMultiAssetToLovelacesValues() {

        var lovelaceValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(List.of()).build();

        var testMultiAssets = List.of(MultiAsset.builder().policyId("policy_ud").assets(List.of(Asset.builder().name("asset_name").value(BigInteger.valueOf(123456L)).build())).build());

        var multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets).build();

        var actualValue = lovelaceValue.plus(multiAssetValue);
        var expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));
        expectedValue.setMultiAssets(testMultiAssets);

        assertThat(actualValue, equalTo(expectedValue));

    }

    @Test
    public void addDifferentMultiAssetsToLovelacesValues() {

        var testMultiAssets1 = List.of(MultiAsset.builder().policyId("policy_id1").assets(List.of(Asset.builder().name("asset_name1").value(BigInteger.valueOf(1000000L)).build())).build());
        var testMultiAssets2 = List.of(MultiAsset.builder().policyId("policy_id2").assets(List.of(Asset.builder().name("asset_name2").value(BigInteger.valueOf(2000000L)).build())).build());

        var lovelaceAndMultiAssetValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(testMultiAssets1).build();

        var multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets2).build();

        var actualValue = lovelaceAndMultiAssetValue.plus(multiAssetValue);
        var expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));

        expectedValue.setMultiAssets(MultiAsset.mergeMultiAssetLists(testMultiAssets1, testMultiAssets2));

        assertThat(actualValue, equalTo(expectedValue));

    }


    @Test
    public void addSamePolicyMultiAssetsToLovelacesValues() {

        var testMultiAssets1 = List.of(MultiAsset.builder().policyId("policy_id1").assets(List.of(Asset.builder().name("asset_name1").value(BigInteger.valueOf(1000000L)).build())).build());
        var testMultiAssets2 = List.of(MultiAsset.builder().policyId("policy_id1").assets(List.of(Asset.builder().name("asset_name2").value(BigInteger.valueOf(2000000L)).build())).build());

        var lovelaceAndMultiAssetValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(testMultiAssets1).build();

        var multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets2).build();

        var actualValue = lovelaceAndMultiAssetValue.plus(multiAssetValue);
        var expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));

        var testMultiAssets = MultiAsset.mergeMultiAssetLists(testMultiAssets1, testMultiAssets2);
        expectedValue.setMultiAssets(testMultiAssets);

        System.out.println(actualValue);
        System.out.println(expectedValue);
        assertThat(actualValue, equalTo(expectedValue));

    }

    @Test
    public void addSameMultiAssetsToLovelacesValues() {

        var testMultiAssets1 = List.of(MultiAsset.builder().policyId("policy_id1").assets(List.of(Asset.builder().name("asset_name1").value(BigInteger.valueOf(1000000L)).build())).build());
        var testMultiAssets2 = List.of(MultiAsset.builder().policyId("policy_id1").assets(List.of(Asset.builder().name("asset_name1").value(BigInteger.valueOf(2000000L)).build())).build());

        var lovelaceAndMultiAssetValue = Value.builder().coin(BigInteger.valueOf(1000000L)).multiAssets(testMultiAssets1).build();

        var multiAssetValue = Value.builder().coin(BigInteger.ZERO).multiAssets(testMultiAssets2).build();

        var actualValue = lovelaceAndMultiAssetValue.plus(multiAssetValue);
        var expectedValue = new Value();
        expectedValue.setCoin(BigInteger.valueOf(1000000L));
        expectedValue.setMultiAssets(MultiAsset.mergeMultiAssetLists(testMultiAssets1, testMultiAssets2));

        assertThat(actualValue, equalTo(expectedValue));


    }


}

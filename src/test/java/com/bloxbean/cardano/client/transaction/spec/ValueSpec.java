package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(actualValue).isEqualTo(expectedValue);

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

        assertThat(actualValue).isEqualTo(expectedValue);

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

        assertThat(actualValue).isEqualTo(expectedValue);

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

        assertThat(actualValue).isEqualTo(expectedValue);

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

        assertThat(actualValue).isEqualTo(expectedValue);


    }

    @Test
    public void toMap() {
        List<MultiAsset> testMultiAssetst = Arrays.asList(
                MultiAsset.builder().policyId("policy_id1").assets(
                        Arrays.asList(
                                Asset.builder().name("asset_name1").value(BigInteger.valueOf(5000000L)).build(),
                                Asset.builder().name("asset_name2").value(BigInteger.valueOf(2000000L)).build(),
                                Asset.builder().name("asset_name3").value(BigInteger.valueOf(3000000L)).build()
                        )
                ).build(),
                MultiAsset.builder().policyId("policy_id2").assets(
                        Arrays.asList(
                                Asset.builder().name("my_asset_name1").value(BigInteger.valueOf(111)).build(),
                                Asset.builder().name("asset_name2").value(BigInteger.valueOf(333)).build()
                        )
                ).build(),
                MultiAsset.builder().policyId("policy_id3").assets(
                        Arrays.asList(
                                Asset.builder().name("abc").value(BigInteger.valueOf(555)).build()
                        )
                ).build()
        );

        Value value = new Value(BigInteger.valueOf(1000), testMultiAssetst);

        Map<String, HashMap<String, BigInteger>> map = value.toMap();

        //Expected map
        Map<String, HashMap<String, BigInteger>> expected = new HashMap<>();
        expected.put("policy_id1", new HashMap<String, BigInteger>() {{
            put("asset_name1", BigInteger.valueOf(5000000L));
            put("asset_name2", BigInteger.valueOf(2000000L));
            put("asset_name3", BigInteger.valueOf(3000000L));
        }});
        expected.put("policy_id2", new HashMap<String, BigInteger>() {{
            put("my_asset_name1", BigInteger.valueOf(111));
            put("asset_name2", BigInteger.valueOf(333));
        }});
        expected.put("policy_id3", new HashMap<String, BigInteger>() {{
            put("abc", BigInteger.valueOf(555));
        }});

        assertThat(map).isEqualTo(expected);
    }

}

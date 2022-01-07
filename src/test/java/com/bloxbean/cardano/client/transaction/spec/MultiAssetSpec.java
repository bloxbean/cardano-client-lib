package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MultiAssetSpec {

    @Test
    public void addSameAsset() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        Asset moreAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(300L)).build();
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, asset2)).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(asset1, moreAsset2)).build();

        MultiAsset expectedMultiAsset = MultiAsset.builder().policyId("policy_id").assets(Arrays.asList(
                asset1.plus(asset1),
                asset2.plus(moreAsset2)
        )).build();

        assertThat(multiAsset1.plus(multiAsset2), equalTo(expectedMultiAsset));
    }

    @Test
    public void addMultiAssetWithDifferentPolicyThrowsError() {
        MultiAsset multiAsset1 = MultiAsset.builder().policyId("policy1").assets(Arrays.asList()).build();
        MultiAsset multiAsset2 = MultiAsset.builder().policyId("policy2").assets(Arrays.asList()).build();

        assertThrows(IllegalArgumentException.class, () -> multiAsset1.plus(multiAsset2));
    }

}

package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MultiAssetSpec {

    @Test
    public void addSameAsset() {
        var asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        var asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();
        var moreAsset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(300L)).build();
        var multiAsset1 = MultiAsset.builder().policyId("policy_id").assets(List.of(asset1, asset2)).build();
        var multiAsset2 = MultiAsset.builder().policyId("policy_id").assets(List.of(asset1, moreAsset2)).build();

        var expectedMultiAsset = MultiAsset.builder().policyId("policy_id").assets(List.of(
                asset1.plus(asset1),
                asset2.plus(moreAsset2)
        )).build();

        assertThat(multiAsset1.plus(multiAsset2), equalTo(expectedMultiAsset));
    }

    @Test
    public void addMultiAssetWithDifferentPolicyThrowsError() {
        var multiAsset1 = MultiAsset.builder().policyId("policy1").assets(List.of()).build();
        var multiAsset2 = MultiAsset.builder().policyId("policy2").assets(List.of()).build();

        assertThrows(IllegalArgumentException.class, () -> multiAsset1.plus(multiAsset2));
    }

}

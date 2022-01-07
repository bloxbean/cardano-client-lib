package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AssetSpec {

    @Test
    public void addSameAsset() {
        Asset asset1 = Asset.builder().name("asset").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset").value(BigInteger.valueOf(200L)).build();

        assertThat(asset1.plus(asset2), equalTo(Asset.builder().name("asset").value(BigInteger.valueOf(300L)).build()));
    }

    @Test
    public void addDifferentAssetThrowsError() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();

        assertThrows(IllegalArgumentException.class, () -> asset1.plus(asset2));
    }

}

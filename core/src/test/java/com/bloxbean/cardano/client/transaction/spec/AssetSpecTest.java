package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AssetSpecTest {

    @Test
    public void addSameAsset() {
        Asset asset1 = Asset.builder().name("asset").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset").value(BigInteger.valueOf(200L)).build();

        assertThat(asset1.plus(asset2)).isEqualTo(Asset.builder().name("asset").value(BigInteger.valueOf(300L)).build());
    }

    @Test
    public void addDifferentAssetThrowsError() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();

        assertThrows(IllegalArgumentException.class, () -> asset1.plus(asset2));
    }

    @Test
    public void minusSameAsset() {
        Asset asset1 = Asset.builder().name("asset").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("asset").value(BigInteger.valueOf(200L)).build();

        assertThat(asset1.minus(asset2)).isEqualTo((Asset.builder().name("asset").value(BigInteger.valueOf(500L)).build()));
    }

    @Test
    public void minusDifferentAssetThrowsError() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();

        assertThrows(IllegalArgumentException.class, () -> asset1.minus(asset2));
    }

    @Test
    public void equals() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();

        assertThat(asset1).isEqualTo(asset2);
    }

    @Test
    public void equalsWhenOneWithHexName() {
        Asset asset1 = Asset.builder().name("TestAss1").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("0x5465737441737331").value(BigInteger.valueOf(700L)).build();

        assertThat(asset1).isEqualTo(asset2);
    }

    @Test
    public void notEqualsWhenOneWithHexName() {
        Asset asset1 = Asset.builder().name("TestAss2").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("0x5465737441737331").value(BigInteger.valueOf(700L)).build();

        assertThat(asset1).isNotEqualTo(asset2);
    }

    @Test
    public void notEqualsWhenDifferentValue() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("asset1").value(BigInteger.valueOf(500L)).build();

        assertThat(asset1).isNotEqualTo(asset2);
    }
}

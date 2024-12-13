package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class AssetSpecTest {

    @Test
    void addSameAsset() {
        Asset asset1 = Asset.builder().name("asset").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset").value(BigInteger.valueOf(200L)).build();

        assertThat(asset1.add(asset2)).isEqualTo(Asset.builder().name("asset").value(BigInteger.valueOf(300L)).build());
    }

    @Test
    void addDifferentAssetThrowsError() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(100L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();

        assertThrows(IllegalArgumentException.class, () -> asset1.add(asset2));
    }

    @Test
    void subtractSameAsset() {
        Asset asset1 = Asset.builder().name("asset").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("asset").value(BigInteger.valueOf(200L)).build();

        assertThat(asset1.subtract(asset2)).isEqualTo((Asset.builder().name("asset").value(BigInteger.valueOf(500L)).build()));
    }

    @Test
    void subtractDifferentAssetThrowsError() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("asset2").value(BigInteger.valueOf(200L)).build();

        assertThrows(IllegalArgumentException.class, () -> asset1.subtract(asset2));
    }

    @Test
    void equals() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();

        assertThat(asset1).isEqualTo(asset2);
        assertEquals(asset1.hashCode(), asset2.hashCode());
    }

    @Test
    void equalsWhenOneWithHexName() {
        Asset asset1 = Asset.builder().name("TestAss1").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("0x5465737441737331").value(BigInteger.valueOf(700L)).build();

        assertThat(asset1).isEqualTo(asset2);
        assertEquals(asset1.hashCode(), asset2.hashCode());
    }

    @Test
    void notEqualsWhenOneWithHexName() {
        Asset asset1 = Asset.builder().name("TestAss2").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("0x5465737441737331").value(BigInteger.valueOf(700L)).build();

        assertThat(asset1).isNotEqualTo(asset2);
    }

    @Test
    void notEqualsWhenDifferentValue() {
        Asset asset1 = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();
        Asset asset2 = Asset.builder().name("asset1").value(BigInteger.valueOf(500L)).build();

        assertThat(asset1).isNotEqualTo(asset2);
    }

    @Test
    void invalidHexName() {
        Asset asset = Asset.builder().name("0xtest").value(BigInteger.valueOf(700L)).build();

        byte[] expectedBytes = "0xtest".getBytes(StandardCharsets.UTF_8);
        assertThat(asset.getNameAsBytes()).isEqualTo(expectedBytes);
    }

    @Test
    void hasName() {
        Asset asset = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();

        var same = asset.hasName("asset1");
        assertTrue(same);
    }

    @Test
    void hasNameReturnsFalse() {
        Asset asset = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();

        var same = asset.hasName("asset2");
        assertFalse(same);
    }

    @Test
    void hasNameWhenHex() {
        Asset asset = Asset.builder().name("asset1").value(BigInteger.valueOf(700L)).build();

        var same = asset.hasName("0x617373657431");
        assertTrue(same);
    }
}

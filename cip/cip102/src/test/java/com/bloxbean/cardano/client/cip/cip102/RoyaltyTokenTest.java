package com.bloxbean.cardano.client.cip.cip102;

import com.bloxbean.cardano.client.cip.cip67.CIP67AssetNameUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoyaltyTokenTest {

    // CIP-67 prefix for label 500 = 001f4d70 (as per the CIP-102 spec)
    private static final String LABEL_500_PREFIX_HEX = "001f4d70";
    private static final String ROYALTY_NAME_HEX = HexUtil.encodeHexString(
            "Royalty".getBytes(StandardCharsets.UTF_8));

    @Test
    void testCreate_noPostfix_assetName() {
        RoyaltyToken token = RoyaltyToken.create();
        // asset name = CIP67 prefix for 500 + "Royalty" in UTF-8
        String expected = "0x" + LABEL_500_PREFIX_HEX + ROYALTY_NAME_HEX;
        assertThat(token.getAssetNameAsHex()).isEqualTo(expected);
    }

    @Test
    void testCreate_noPostfix_friendlyName() {
        RoyaltyToken token = RoyaltyToken.create();
        assertThat(token.getFriendlyName()).isEqualTo("(500)Royalty");
    }

    @Test
    void testCreate_withPostfix_assetName() {
        RoyaltyToken token = RoyaltyToken.create(2);
        String royalty2NameHex = HexUtil.encodeHexString("Royalty2".getBytes(StandardCharsets.UTF_8));
        String expected = "0x" + LABEL_500_PREFIX_HEX + royalty2NameHex;
        assertThat(token.getAssetNameAsHex()).isEqualTo(expected);
    }

    @Test
    void testCreate_withPostfix_friendlyName() {
        assertThat(RoyaltyToken.create(1).getFriendlyName()).isEqualTo("(500)Royalty1");
        assertThat(RoyaltyToken.create(99).getFriendlyName()).isEqualTo("(500)Royalty99");
    }

    @Test
    void testCreate_postfixZero_throws() {
        assertThatThrownBy(() -> RoyaltyToken.create(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCreate_postfixNegative_throws() {
        assertThatThrownBy(() -> RoyaltyToken.create(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetAsset_quantityIsOne() {
        RoyaltyToken token = RoyaltyToken.create();
        assertThat(token.getAsset().getValue()).isEqualByComparingTo(java.math.BigInteger.ONE);
    }

    @Test
    void testLabelPrefix_matchesCip67() {
        // The CIP-67 prefix for label 500 must match what CIP67AssetNameUtil computes
        byte[] expected = CIP67AssetNameUtil.labelToPrefix(500);
        byte[] tokenPrefix = new byte[4];
        System.arraycopy(RoyaltyToken.create().getAssetNameAsBytes(), 0, tokenPrefix, 0, 4);
        assertThat(tokenPrefix).isEqualTo(expected);
    }

    @Test
    void testAssetNameBytes_length() {
        // 4 bytes CIP67 prefix + 7 bytes "Royalty" = 11 bytes
        assertThat(RoyaltyToken.create().getAssetNameAsBytes()).hasSize(11);
        // 4 + 8 ("Royalty1") = 12 bytes
        assertThat(RoyaltyToken.create(1).getAssetNameAsBytes()).hasSize(12);
    }
}

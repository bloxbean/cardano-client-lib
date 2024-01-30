package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CIP68AssetNameTest {

    @Test
    public void assetNamePrefixTest() {
        CIP68NFT testNFT = CIP68NFT.create()
                .name("TestNFT");

        assertTrue(testNFT.getAssetNameAsHex().startsWith("0x000de140"));

        CIP68ReferenceToken referenceToken = testNFT.getReferenceToken();
        assertTrue(referenceToken.getAssetNameAsHex().startsWith("0x000643b0"));
    }

    @Test
    public void assetNameBytesTest() {
        CIP68NFT testNFT = CIP68NFT.create()
                .name("TestNFT");

        byte[] assetNameAsBytes = testNFT.getAssetNameAsBytes();
        String assetNameBytesAsHex = HexUtil.encodeHexString(assetNameAsBytes);

        assertTrue(assetNameBytesAsHex.equals("000de140546573744e4654"));
    }


    @Test
    public void createAssetTest() {
        CIP68NFT testNFT = CIP68NFT.create()
                .name("TestNFT")
                .description("Testing NFT asset creation");
        Asset asset = testNFT.getAsset(BigInteger.valueOf(1));
        String expectedName = "0x000de140546573744e4654";
        assertEquals(expectedName, asset.getName());
    }
}

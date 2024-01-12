package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CIP68MetadataTest {

    @Test
    public void assetNamePrefixTest() {
        CIP68NFT testNFT = CIP68NFT.create()
                .assetName("TestNFT")
                .name("TestNFT");

        assertTrue(testNFT.getHexAssetFullName().startsWith("0x000de140"));

        CIP68ReferenceToken referenceToken = testNFT.getReferenceToken();
        assertTrue(referenceToken.getHexAssetFullName().startsWith("0x000643b0"));
    }

    @Test
    public void assetMetaDataTest() {
        CIP68NFT testNFT = CIP68NFT.create()
                .assetName("TestNFT")
                .name("TestNFT")
                .description("Testing NFT metadata");
        CIP68Metadata metadata = testNFT.getMetadata();

        String metadataHash = HexUtil.encodeHexString(metadata.getMetadataHash());

        String expectedHash = HexUtil.encodeHexString(new byte[]{94, -80, 13, 102, -76, 29, -128, -71, -119, 75, 17, -40, 76, -123, 40, -35, 101, -72, 80, -106, 10, -61, 63, -20, -31, 70, 63, -25, -33, 12, -83, -39});
        assertEquals(expectedHash, metadataHash);
    }

    @Test
    public void createAssetTest() {
        CIP68NFT testNFT = CIP68NFT.create()
                .assetName("TestNFT")
                .name("TestNFT")
                .description("Testing NFT asset creation");
        Asset asset = testNFT.getAsset(BigInteger.valueOf(1));
        String expectedName = "0x000de140546573744e4654";
        assertEquals(expectedName, asset.getName());
    }
}

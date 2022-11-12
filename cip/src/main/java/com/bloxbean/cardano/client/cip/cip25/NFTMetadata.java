package com.bloxbean.cardano.client.cip.cip25;

import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;

import java.math.BigInteger;

//Implementation for https://cips.cardano.org/cips/cip25/
//NFT Metadata Standard
public class NFTMetadata extends CBORMetadata {

    private final static BigInteger LABEL = BigInteger.valueOf(721);
    public static final String VERSION_KEY = "version";
    private String version = "1.0";

    private CBORMetadataMap policyMap;

    private NFTMetadata() {
        policyMap = new CBORMetadataMap();
        policyMap.put(VERSION_KEY, version);
        put(LABEL, policyMap);
    }

    private NFTMetadata(Map map) {
        super(map);
    }

    /**
     * Create an instance of NFTMetadata
     * @return
     */
    public static NFTMetadata create() {
        return new NFTMetadata();
    }

    /**
     * Create an instance of NFTMetadata from cbor bytes
     * @param cborBytes
     * @return
     */
    public static NFTMetadata create(byte[] cborBytes) {
        Map map = NFTMetadata.deserialize(cborBytes).getData();
        return new NFTMetadata(map);
    }

    /**
     * Add an NFT
     * @param policyId
     * @param nft
     * @return
     */
    public NFTMetadata addNFT(String policyId, NFT nft) {
        CBORMetadataMap nftListMap = (CBORMetadataMap) policyMap.get(policyId);
        if (nftListMap == null) {
            nftListMap = new CBORMetadataMap();
            policyMap.put(policyId, nftListMap);
        }

        nftListMap.put(nft.getAssetName(), nft);

        return this;
    }

    /**
     * Get an NFT by policy id and asset name
     * @param policyId
     * @param assetName
     * @return
     */
    public NFT getNFT(String policyId, String assetName) {
        CBORMetadataMap nftListMap = (CBORMetadataMap) policyMap.get(policyId);
        if (nftListMap == null)
            return null;

        CBORMetadataMap nftMap = (CBORMetadataMap) nftListMap.get(assetName);
        if (nftMap != null) {
            NFT nft = NFT.create(nftMap.getMap());
            nft.assetName(assetName);
            return nft;
        } else {
            return null;
        }
    }

    /**
     * Remove nft by policy id and asset name
     * @param policyId
     * @param assetName
     * @return
     */
    public NFTMetadata removeNFT(String policyId, String assetName) {
        CBORMetadataMap nftListMap = (CBORMetadataMap) policyMap.get(policyId);

        if (nftListMap != null) {
            nftListMap.remove(assetName);
        }

        return this;
    }

    /**
     * Set version
     * Default version : 1.0
     * @param version
     * @return
     */
    public NFTMetadata version(String version) {
        this.version = version;
        policyMap.put(VERSION_KEY, version);
        return this;
    }

    /**
     * Get version
     * @return
     */
    public String getVersion() {
        return version;
    }

    public String toString() {
        try {
            return toJson();
        } catch (Exception e) {
            return super.toString();
        }
    }
}

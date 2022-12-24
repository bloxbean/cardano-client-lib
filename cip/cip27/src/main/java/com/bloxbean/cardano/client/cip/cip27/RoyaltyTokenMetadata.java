package com.bloxbean.cardano.client.cip.cip27;

import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;

import java.math.BigInteger;

/**
 * Implementation for https://cips.cardano.org/cips/cip27/
 * CNFT Community Royalties Standard
 */
public class RoyaltyTokenMetadata extends CBORMetadata {

    private static final BigInteger LABEL = BigInteger.valueOf(777);

    private RoyaltyTokenMetadata() {}

    private RoyaltyTokenMetadata(Map map) {
        super(map);
    }

    /**
     * Create an instance of NFTMetadata
     *
     * @return {@link RoyaltyTokenMetadata}
     */
    public static RoyaltyTokenMetadata create() {
        return new RoyaltyTokenMetadata();
    }

    /**
     * Create an instance of NFTMetadata from cbor bytes
     *
     * @param cborBytes cborBytes
     * @return {@link RoyaltyTokenMetadata}
     */
    public static RoyaltyTokenMetadata create(byte[] cborBytes) {
        Map map = CBORMetadata.deserialize(cborBytes).getData();
        return new RoyaltyTokenMetadata(map);
    }

    /**
     * Get Royalty Token
     *
     * @return {@link RoyaltyToken}
     */
    public RoyaltyToken getRoyaltyToken() {
        CBORMetadataMap cborMetadataMap = (CBORMetadataMap) get(LABEL);
        if (cborMetadataMap == null) {
            return null;
        }
        return RoyaltyToken.create(cborMetadataMap.getMap());
    }

    /**
     * Set RoyaltyToken
     *
     * @param royaltyToken royaltyToken
     * @return {@link RoyaltyTokenMetadata}
     */
    public RoyaltyTokenMetadata royaltyToken(RoyaltyToken royaltyToken) {
        if (royaltyToken == null)
            return null;
        put(LABEL, royaltyToken);
        return this;
    }

    public String toString() {
        try {
            return toJson();
        } catch (Exception e) {
            return super.toString();
        }
    }
}

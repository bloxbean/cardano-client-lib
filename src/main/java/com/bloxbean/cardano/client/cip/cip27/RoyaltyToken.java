package com.bloxbean.cardano.client.cip.cip27;

import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.cip.cip25.NFTProperties;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;

public class RoyaltyToken extends NFTProperties {

    public static final String ADDRESS_KEY = "addr";
    public static final String RATE_KEY = "rate";

    private RoyaltyToken() {}

    private RoyaltyToken(Map map) {
        super(map);
    }

    /**
     * Create an instance of RoyaltyToken
     * @return {@link RoyaltyToken}
     */
    public static RoyaltyToken create() {
        return new RoyaltyToken();
    }

    static RoyaltyToken create(Map map) {
        return new RoyaltyToken(map);
    }

    /**
     * Set Royalty Address
     * @param address bech32 payment address
     * @return {@link RoyaltyToken}
     */
    public RoyaltyToken address(String address) {
        put(ADDRESS_KEY, address);
        return this;
    }

    /**
     * Set Royalty Rate
     * @param rate rate
     * @return {@link RoyaltyToken}
     */
    public RoyaltyToken rate(Double rate) {
        put(RATE_KEY, rate.toString());
        return this;
    }

    /**
     * Get Royalty Address
     * @return Bech32 Payment Address
     */
    public String getAddress() {
        if (get(ADDRESS_KEY) instanceof CBORMetadataList) {
            StringBuilder stringBuilder = new StringBuilder();
            for (DataItem dataItem : ((CBORMetadataList) get(ADDRESS_KEY)).getArray().getDataItems()) {
                stringBuilder.append(dataItem.toString());
            }
            return stringBuilder.toString();
        } else {
            return (String) get(ADDRESS_KEY);
        }
    }

    /**
     * Get Royalty Rate
     * @return Rate
     */
    public Double getRate() {
        return Double.valueOf((String) get(RATE_KEY));
    }
}

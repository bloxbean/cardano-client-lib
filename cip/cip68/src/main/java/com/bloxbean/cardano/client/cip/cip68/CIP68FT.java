package com.bloxbean.cardano.client.cip.cip68;

import co.nstant.in.cbor.model.Map;

import java.math.BigInteger;

/**
 * Implementation of CIP-68 fungible token.
 * According to specification: https://developers.cardano.org/docs/governance/cardano-improvement-proposals/cip-0068/#333-ft-standard
 */
public class CIP68FT extends CIP68TokenTemplate<CIP68FT> {

    private static final int ASSET_NAME_LABEL = 333;

    private static final String TICKER_KEY = "ticker";
    private static final String URL_KEY = "url";
    private static final String DECIMALS_KEY = "decimals";
    private static final String LOGO_KEY = "logo";

    private CIP68FT() {
        super(ASSET_NAME_LABEL);
    }

    private CIP68FT(Map map) {
        super(map, ASSET_NAME_LABEL);
    }

    /**
     * Create instance of CIP68FT
     * @return
     */
    public static CIP68FT create() {
        return new CIP68FT();
    }

    /**
     * Set ticker
     * @param ticker
     * @return adjusted CIP68FT object
     */
    public CIP68FT ticker(String ticker) {
        put(TICKER_KEY, ticker);
        return this;
    }

    /**
     * get Ticker
     * @return current Object
     */
    public String getTicker() {
        return (String) get(TICKER_KEY);
    }

    /**
     * set url
     * @param url
     * @return adjusted CIP68FT object
     */
    public CIP68FT url(String url) {
        put(URL_KEY, url);
        return this;
    }

    /**
     * Get url
     * @return
     */
    public String getURL() {
        return (String) get(URL_KEY);
    }

    /**
     * Set decimals
     * @param decimals
     * @return adjusted CIP68FT object
     */
    public CIP68FT decimals(int decimals) {
        put(DECIMALS_KEY, BigInteger.valueOf(decimals));
        return this;
    }

    /**
     * get decimals
     * @return
     */
    public int getDecimals() {
        return (int) get(DECIMALS_KEY);
    }

    /**
     * set logo
     * @param logo
     * @return adjusted CIP68FT object
     */
    public CIP68FT logo(String logo) {
        put(LOGO_KEY, logo);
        return this;
    }

    /**
     * get logo
     * @return
     */
    public String getLogo() {
        return (String) get(LOGO_KEY);
    }
}

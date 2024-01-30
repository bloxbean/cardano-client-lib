package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.cip.cip68.common.CIP68Datum;
import com.bloxbean.cardano.client.cip.cip68.common.CIP68TokenTemplate;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;

import static com.bloxbean.cardano.client.cip.cip68.common.CIP68Util.toByteString;

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

    private CIP68FT(CIP68Datum cip68Datum) {
        super(ASSET_NAME_LABEL, cip68Datum);
    }

    /**
     * Create instance of CIP68FT
     *
     * @return
     */
    public static CIP68FT create() {
        return new CIP68FT();
    }

    /**
     * Set ticker
     *
     * @param ticker
     * @return adjusted CIP68FT object
     */
    public CIP68FT ticker(String ticker) {
        property(TICKER_KEY, ticker);
        return this;
    }

    /**
     * get Ticker
     *
     * @return current Object
     */
    public String getTicker() {
        return getStringProperty(TICKER_KEY);
    }

    /**
     * set url
     *
     * @param url
     * @return adjusted CIP68FT object
     */
    public CIP68FT url(String url) {
        property(URL_KEY, url);
        return this;
    }

    /**
     * Get url
     *
     * @return
     */
    public String getURL() {
        return getStringProperty(URL_KEY);
    }

    /**
     * Set decimals
     *
     * @param decimals
     * @return adjusted CIP68FT object
     */
    public CIP68FT decimals(int decimals) {
        property(toByteString(DECIMALS_KEY), BigIntPlutusData.of(decimals));
        return this;
    }

    /**
     * get decimals
     *
     * @return
     */
    public int getDecimals() {
        return ((BigIntPlutusData) getProperty(toByteString(DECIMALS_KEY))).getValue().intValue();
    }

    /**
     * set logo
     *
     * @param logo
     * @return adjusted CIP68FT object
     */
    public CIP68FT logo(String logo) {
        property(LOGO_KEY, logo);
        return this;
    }

    /**
     * get logo
     *
     * @return
     */
    public String getLogo() {
        return (String) getStringProperty(LOGO_KEY);
    }

    public static CIP68FT fromDatum(byte[] datumBytes) {
        CIP68FT token = CIP68FT.create();
        return token.populateFromDatumBytes(datumBytes);
    }
}

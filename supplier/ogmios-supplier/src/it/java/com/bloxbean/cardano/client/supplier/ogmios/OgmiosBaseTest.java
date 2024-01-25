package com.bloxbean.cardano.client.supplier.ogmios;

import com.bloxbean.cardano.client.supplier.kupo.KupoUtxoSupplier;

public class OgmiosBaseTest {
    protected OgmiosProtocolParamSupplier ogmiosProtocolParamSupplier;
    protected OgmiosTransactionProcessor ogmiosTransactionProcessor;
    protected KupoUtxoSupplier kupoUtxoSupplier;

    private final static String OGMIOS_URL = "http://localhost:1337/";
    private final static String KUPO_URL = "http://localhost:1442/";

    public OgmiosBaseTest() {
        this.ogmiosProtocolParamSupplier = new OgmiosProtocolParamSupplier(OGMIOS_URL);
        this.ogmiosTransactionProcessor = new OgmiosTransactionProcessor(OGMIOS_URL);
        this.kupoUtxoSupplier = new KupoUtxoSupplier(KUPO_URL);
    }
}

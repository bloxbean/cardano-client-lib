package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.backend.kupo.KupoUtxoService;

public class OgmiosBaseTest {
    protected OgmiosBackendService ogmiosBackendService;
    protected KupoUtxoService kupoUtxoService;

    public OgmiosBaseTest() {
        this.ogmiosBackendService = new OgmiosBackendService(Constants.OGMIOS_DANDELION_TESTNET_URL);
        this.kupoUtxoService = new KupoUtxoService("http://192.168.0.228:1442");
    }
}

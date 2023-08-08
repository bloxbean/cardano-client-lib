package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.backend.kupo.KupoUtxoService;

public class OgmiosBaseTest {
    protected OgmiosBackendService ogmiosBackendService;
    protected KupoUtxoService kupoUtxoService;

    public OgmiosBaseTest() {
        this.ogmiosBackendService = new OgmiosBackendService("ws://ogmios-preprod:1337/");
        this.kupoUtxoService = new KupoUtxoService("http://ogmios-preprod:1442");
    }
}
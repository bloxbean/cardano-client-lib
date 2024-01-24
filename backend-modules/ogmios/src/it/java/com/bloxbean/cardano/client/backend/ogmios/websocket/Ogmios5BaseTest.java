package com.bloxbean.cardano.client.backend.ogmios.websocket;

import com.bloxbean.cardano.client.backend.kupo.KupoUtxoService;
import com.bloxbean.cardano.client.backend.ogmios.KupmiosBackendService;

public class Ogmios5BaseTest {
    protected Ogmios5BackendService ogmios5BackendService;
    protected KupoUtxoService kupoUtxoService;
    protected KupmiosBackendService kupmiosBackendService;

    public Ogmios5BaseTest() {
        this.ogmios5BackendService = new Ogmios5BackendService("ws://ogmios-preprod:1337/");
        this.kupoUtxoService = new KupoUtxoService("http://ogmios-preprod:1442");
    }
}

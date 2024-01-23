package com.bloxbean.cardano.client.backend.ogmios.websocket;

import com.bloxbean.cardano.client.backend.kupo.KupoUtxoService;
import com.bloxbean.cardano.client.backend.ogmios.websocket.Kupmios5BackendService;
import com.bloxbean.cardano.client.backend.ogmios.websocket.Ogmios5BackendService;

public class Ogmios5BaseTest {
    protected Ogmios5BackendService ogmios5BackendService;
    protected KupoUtxoService kupoUtxoService;
    protected Kupmios5BackendService kupmiosBackendService;

    public Ogmios5BaseTest() {
        this.ogmios5BackendService = new Ogmios5BackendService("ws://ogmios-preprod:1337/");
        this.kupoUtxoService = new KupoUtxoService("http://ogmios-preprod:1442");

        this.kupmiosBackendService = new Kupmios5BackendService("ws://ogmios-preprod:1337/", "http://ogmios-preprod:1442");
    }
}

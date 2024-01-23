package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.backend.kupo.KupoUtxoService;
import com.bloxbean.cardano.client.backend.ogmios.http.OgmiosBackendService;
import com.bloxbean.cardano.client.backend.ogmios.websocket.Kupmios5BackendService;

public class OgmiosBaseTest {
    protected OgmiosBackendService ogmiosBackendService;
    protected KupoUtxoService kupoUtxoService;
    protected Kupmios5BackendService kupmiosBackendService;

    public OgmiosBaseTest() {
        this.ogmiosBackendService = new OgmiosBackendService("http://localhost:1337/");
        this.kupoUtxoService = new KupoUtxoService("http://ogmios-preprod:1442");

        this.kupmiosBackendService = new Kupmios5BackendService("ws://ogmios-preprod:1337/", "http://ogmios-preprod:1442");
    }
}

package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.backend.KupmiosBackendService;
import com.bloxbean.cardano.client.backend.kupo.KupoUtxoService;
import com.bloxbean.cardano.client.backend.ogmios.http.OgmiosBackendService;

public class OgmiosBaseTest {
    protected OgmiosBackendService ogmiosBackendService;
    protected KupoUtxoService kupoUtxoService;
    protected KupmiosBackendService kupmiosBackendService;

    private String OGMIOS_HTTP_URL = "http://localhost:1337/";
    private String KUPO_HTTP_URL = "http://localhost:1442/";

    public OgmiosBaseTest() {
        this.ogmiosBackendService = new OgmiosBackendService(OGMIOS_HTTP_URL);
        this.kupoUtxoService = new KupoUtxoService(KUPO_HTTP_URL);

        this.kupmiosBackendService = new KupmiosBackendService(OGMIOS_HTTP_URL, KUPO_HTTP_URL);
    }
}

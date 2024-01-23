package com.bloxbean.cardano.client.backend.ogmios.websocket;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.kupo.KupoUtxoService;

/**
 * KupmiosBackendService is a combination of Kupo and Ogmios backend services.
 * It uses Kupo for UtxoService and Ogmios for other services.
 */
public class Kupmios5BackendService extends Ogmios5BackendService {
    private UtxoService kupoUtxoService;

    public Kupmios5BackendService(String ogmiosUrl, String kupoUrl) {
        super(ogmiosUrl);
        kupoUtxoService = new KupoUtxoService(kupoUrl);
    }

    @Override
    public UtxoService getUtxoService() {
        return kupoUtxoService;
    }

}

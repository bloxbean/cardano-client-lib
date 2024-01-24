package com.bloxbean.cardano.client.backend;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.kupo.KupoUtxoService;
import com.bloxbean.cardano.client.backend.ogmios.http.OgmiosBackendService;

/**
 * KupmiosBackendService is a combination of Kupo and Ogmios backend services.
 * It uses Kupo for UtxoService and Ogmios for other services.
 */
public class KupmiosBackendService extends OgmiosBackendService {
    private UtxoService kupoUtxoService;

    public KupmiosBackendService(String ogmiosUrl, String kupoUrl) {
        super(ogmiosUrl);
        kupoUtxoService = new KupoUtxoService(kupoUrl);
    }

    @Override
    public UtxoService getUtxoService() {
        return kupoUtxoService;
    }

}

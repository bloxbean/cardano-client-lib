package com.bloxbean.cardano.client.backend.factory;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFBackendService;

public class BackendFactory {

    /**
     * Get BackendService for Blockfrost
     * @param blockfrostUrl
     * @param projectId
     * @return
     */
    public static BackendService getBlockfrostBackendService(String blockfrostUrl, String projectId) {
        return new BFBackendService(blockfrostUrl, projectId);
    }

}

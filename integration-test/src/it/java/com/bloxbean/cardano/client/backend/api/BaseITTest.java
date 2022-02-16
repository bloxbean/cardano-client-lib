package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFBackendService;

public class BaseITTest {

    public String bfProjectId;

    public BaseITTest() {
        bfProjectId = System.getProperty("BF_PROJECT_ID");
        if (bfProjectId == null || bfProjectId.isEmpty()) {
            bfProjectId = System.getenv("BF_PROJECT_ID");
        }

    }

    public BackendService getBackendService() {
        if (bfProjectId != null && !bfProjectId.isEmpty())
            return BackendFactory.getBlockfrostBackendService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        else
            return null;
    }
}

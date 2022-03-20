package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;

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
            return new BFBackendService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        else
            return null;
    }
}

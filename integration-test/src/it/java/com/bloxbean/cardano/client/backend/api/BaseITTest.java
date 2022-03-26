package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;

public class BaseITTest {

    protected String backendType = "blockfrost";
    public String bfProjectId;

    public BaseITTest() {
        bfProjectId = System.getProperty("BF_PROJECT_ID");
        if (bfProjectId == null || bfProjectId.isEmpty()) {
            bfProjectId = System.getenv("BF_PROJECT_ID");
        }
    }

    public BackendService getBackendService() {
        if ("blockfrost".equals(backendType) && bfProjectId != null && !bfProjectId.isEmpty())
            return new BFBackendService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        else if ("koios".equals(backendType)) {
            return new KoiosBackendService(com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_TESTNET_URL);
        } else
            return null;
    }
}

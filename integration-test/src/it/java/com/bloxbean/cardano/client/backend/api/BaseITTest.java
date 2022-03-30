package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.gql.GqlBackendService;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;

public class BaseITTest {
    protected String BLOCKFROST = "blockfrost";
    protected String KOIOS = "koios";
    protected String CARDANO_GQL = "cardano-gql";
    protected String OGMIOS = "OGMIOS";

    protected String backendType = BLOCKFROST;
    public String bfProjectId;

    public BaseITTest() {
        bfProjectId = System.getProperty("BF_PROJECT_ID");
        if (bfProjectId == null || bfProjectId.isEmpty()) {
            bfProjectId = System.getenv("BF_PROJECT_ID");
        }
    }

    public BackendService getBackendService() {
        if (BLOCKFROST.equals(backendType) && bfProjectId != null && !bfProjectId.isEmpty()) {
            return new BFBackendService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        } else if (KOIOS.equals(backendType)) {
            return new KoiosBackendService(com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_TESTNET_URL);
        } else if (CARDANO_GQL.equals(backendType)) {
            return new GqlBackendService(com.bloxbean.cardano.client.backend.gql.Constants.DANDELION_TESTNET_GQL_URL);
        } else
            return null;
    }
}

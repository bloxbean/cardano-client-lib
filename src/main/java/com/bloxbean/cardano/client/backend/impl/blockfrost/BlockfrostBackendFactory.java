package com.bloxbean.cardano.client.backend.impl.blockfrost;

import com.bloxbean.cardano.client.backend.api.BackendFactory;
import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.impl.blockfrost.excpetion.BlockfrostConfigurationException;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;

import static com.bloxbean.cardano.client.backend.common.BackendConstants.MAINNET;
import static com.bloxbean.cardano.client.backend.common.BackendConstants.TESTNET;

public class BlockfrostBackendFactory implements BackendFactory {
    public static final String BLOCKFROST_NETWORK = "BLOCKFROST_NETWORK";
    public static final String BLOCKFROST_PROJECT_ID = "BLOCKFROST_PROJECT_ID";
    public static final String BLOCKFROST_URL = "BLOCKFROST_URL";
    private String baseUrl;
    private String projectId;

    public BlockfrostBackendFactory(Network network, String projectId) {
        if(network == null)
            throw new BlockfrostConfigurationException("Network cannot be null");
        if(projectId != null && projectId.isEmpty())
            throw  new BlockfrostConfigurationException("Blockfrost project id cannot be null");

        if(Networks.mainnet().equals(network)) {
            this.baseUrl = Constants.BLOCKFROST_MAINNET_URL;
        } else if(Networks.testnet().equals(network)) {
            this.baseUrl = Constants.BLOCKFROST_TESTNET_URL;
        } else {
            throw new BlockfrostConfigurationException(String.format("Invalid network for Blockfrost : %s", network.toString()));
        }

        this.projectId = projectId;
    }

    public BlockfrostBackendFactory(String baseUrl, String projectId) {
        if(baseUrl == null)
            throw new BlockfrostConfigurationException("Base url cannot be null");
        if(projectId != null && projectId.isEmpty())
            throw  new BlockfrostConfigurationException("Blockfrost project id cannot be null");

        this.baseUrl = baseUrl;
        this.projectId = projectId;
    }

    public BlockfrostBackendFactory() {
        String blockfrostNetwork = System.getProperty(BLOCKFROST_NETWORK);
        String projectId = System.getProperty(BLOCKFROST_PROJECT_ID);
        String blockfrostUrl = System.getProperty(BLOCKFROST_URL);

        if(blockfrostUrl == null && blockfrostUrl.isEmpty()) {
            if (blockfrostNetwork == null || blockfrostNetwork.isEmpty())
                throw new BlockfrostConfigurationException("BLOCKFROST_NETWORK is not defined");

            if(blockfrostNetwork.equals(MAINNET))
                this.baseUrl = Constants.BLOCKFROST_MAINNET_URL;
            else if(blockfrostNetwork.equals(TESTNET))
                this.baseUrl = Constants.BLOCKFROST_TESTNET_URL;
            else
                throw new BlockfrostConfigurationException("Invalid Blockforst network : " + blockfrostNetwork);

        } else {
            this.baseUrl = blockfrostUrl;
        }

        if(projectId != null && projectId.isEmpty())
            throw  new BlockfrostConfigurationException("BLOCKFROST_PROJECT_ID is not defined");
        this.projectId = projectId;
    }

    @Override
    public NetworkInfoService getNetworkInfoService() {
        return null;
    }

    @Override
    public TransactionService getTransactionService() {
        return null;
    }
}

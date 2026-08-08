package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.factory.BackendServiceFactory;
import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.backend.api.*;

/**
 * Nexus Backend Service
 */
public class NexusBackendService implements BackendService {

    private final adlabs.nexus.client.backend.factory.BackendService sdkBackendService;
    private final Network network;

    /**
     * NexusBackendService Constructor
     *
     * @param baseUrl baseUrl
     * @param apiKey  Nexus API key
     * @param network Cardano network
     */
    public NexusBackendService(String baseUrl, String apiKey, Network network) {
        this.sdkBackendService = BackendServiceFactory.getNexusBackendService(baseUrl, apiKey);
        this.network = network;
    }

    /**
     * NexusBackendService Constructor using the default Nexus URL, no API key.
     *
     * @param network Cardano network
     */
    public NexusBackendService(Network network) {
        this(Constants.DEFAULT_URL, null, network);
    }

    @Override
    public AssetService getAssetService() {
        return new NexusAssetService(sdkBackendService.getAssetService(), network);
    }

    @Override
    public BlockService getBlockService() {
        return new NexusBlockService(sdkBackendService.getBlockService(), network);
    }

    @Override
    public NetworkInfoService getNetworkInfoService() {
        return new NexusNetworkService(sdkBackendService.getNetworkService(), network);
    }

    @Override
    public PoolService getPoolService() {
        return new NexusPoolService(sdkBackendService.getPoolService(), network);
    }

    @Override
    public TransactionService getTransactionService() {
        return new NexusTransactionService(sdkBackendService.getTransactionService(), network);
    }

    @Override
    public UtxoService getUtxoService() {
        return new NexusUtxoService(sdkBackendService.getAddressService(),
                new NexusTransactionService(sdkBackendService.getTransactionService(), network), network);
    }

    @Override
    public AddressService getAddressService() {
        return new NexusAddressService(sdkBackendService.getAddressService(), network);
    }

    @Override
    public AccountService getAccountService() {
        return new NexusAccountService(sdkBackendService.getAccountService(), network);
    }

    @Override
    public EpochService getEpochService() {
        return new NexusEpochService(sdkBackendService.getEpochService(), network);
    }

    @Override
    public MetadataService getMetadataService() {
        return new NexusMetadataService(sdkBackendService.getMetadataService(), network);
    }

    @Override
    public ScriptService getScriptService() {
        return new NexusScriptService(sdkBackendService.getScriptService(), network);
    }
}

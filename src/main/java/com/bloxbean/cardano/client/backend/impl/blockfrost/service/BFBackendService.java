package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.api.*;

public class BFBackendService extends BFBaseService implements BackendService {

    public BFBackendService(String baseUrl, String projectId) {
        super(baseUrl, projectId);
    }

    @Override
    public AssetService getAssetService() {
        return new BFAssetService(getBaseUrl(), getProjectId());
    }

    @Override
    public BlockService getBlockService() {
        return new BFBlockService(getBaseUrl(), getProjectId());
    }

    @Override
    public NetworkInfoService getNetworkInfoService() {
        return new BFNetworkService(getBaseUrl(), getProjectId());
    }

    @Override
    public TransactionService getTransactionService() {
        return new BFTransactionService(getBaseUrl(), getProjectId());
    }

    @Override
    public UtxoService getUtxoService() {
        return new BFUtxoService(getBaseUrl(), getProjectId());
    }

    @Override
    public AddressService getAddressService() {
        return new BFAddressService(getBaseUrl(), getProjectId());
    }

    @Override
    public EpochService getEpochService() {
        return new BFEpochService(getBaseUrl(), getProjectId());
    }

    @Override
    public MetadataService getMetadataService() {
        return new BFMetadataService(getBaseUrl(), getProjectId());
    }
}

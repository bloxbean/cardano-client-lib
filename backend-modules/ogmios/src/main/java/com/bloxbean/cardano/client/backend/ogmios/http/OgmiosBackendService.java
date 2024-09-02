package com.bloxbean.cardano.client.backend.ogmios.http;

import com.bloxbean.cardano.client.backend.api.*;
import lombok.Getter;

@Getter
public class OgmiosBackendService implements BackendService {
    private String baseUrl;

    public OgmiosBackendService(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public AssetService getAssetService() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public BlockService getBlockService() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public NetworkInfoService getNetworkInfoService() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public PoolService getPoolService() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public TransactionService getTransactionService() {
        return new OgmiosTransactionService(getBaseUrl());
    }

    @Override
    public EpochService getEpochService() {
        return new OgmiosEpochService(getBaseUrl());
    }

    @Override
    public UtxoService getUtxoService() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public AddressService getAddressService() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public AccountService getAccountService() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public MetadataService getMetadataService() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public ScriptService getScriptService() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public GovernanceService getGovernanceService() {
        throw new UnsupportedOperationException("Not supported yet");
    }
}

package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.backend.api.*;
import rest.koios.client.backend.factory.impl.BackendServiceImpl;

public class KoiosBackendService implements BackendService {

    private final BackendServiceImpl backendServiceImpl;

    public KoiosBackendService(String baseUrl) {
        backendServiceImpl = new BackendServiceImpl(baseUrl);
    }

    @Override
    public AssetService getAssetService() {
        return new KoiosAssetService(backendServiceImpl.getAssetService());
    }

    @Override
    public BlockService getBlockService() {
        return new KoiosBlockService(backendServiceImpl.getBlockService());
    }

    @Override
    public NetworkInfoService getNetworkInfoService() {
        return new KoiosNetworkService(backendServiceImpl.getNetworkService());
    }

    @Override
    public TransactionService getTransactionService() {
        return new KoiosTransactionService(backendServiceImpl.getTransactionsService());
    }

    @Override
    public UtxoService getUtxoService() {
        return new KoiosUtxoService(backendServiceImpl.getAddressService());
    }

    @Override
    public AddressService getAddressService() {
        return new KoiosAddressService(backendServiceImpl.getAddressService());
    }

    @Override
    public EpochService getEpochService() {
        return new KoiosEpochService(backendServiceImpl.getEpochService());
    }

    @Override
    public MetadataService getMetadataService() {
        return new KoiosMetadataService(backendServiceImpl.getTransactionsService());
    }
}

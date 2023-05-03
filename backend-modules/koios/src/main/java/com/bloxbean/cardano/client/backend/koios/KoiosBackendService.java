package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.backend.api.*;
import rest.koios.client.backend.factory.impl.BackendServiceImpl;

/**
 * Koios Backend Service
 */
public class KoiosBackendService implements BackendService {

    /**
     * Koios Backend Service Implementation
     */
    private final BackendServiceImpl backendServiceImpl;

    /**
     * KoiosBackendService Constructor
     *
     * @param baseUrl baseUrl
     */
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
        return new KoiosUtxoService(backendServiceImpl.getAddressService(), getTransactionService());
    }

    @Override
    public AddressService getAddressService() {
        return new KoiosAddressService(backendServiceImpl.getAddressService());
    }

    @Override
    public AccountService getAccountService() {
        return new KoiosAccountService(backendServiceImpl.getAccountService());
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

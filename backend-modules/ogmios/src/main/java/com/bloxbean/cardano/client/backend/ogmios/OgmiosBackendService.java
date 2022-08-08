package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.backend.api.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

public class OgmiosBackendService implements BackendService {
    String url;
    private OgmiosWSClient wsClient;

    public OgmiosBackendService(String url) {
        this.url = url;
        try {
            wsClient = new OgmiosWSClient(new URI(url));
            wsClient.connectBlocking(20, TimeUnit.SECONDS);
        } catch (URISyntaxException e) {
            throw new ApiRuntimeException("Invalid Ogmios url : ", e);
        } catch (InterruptedException e) {
            throw new ApiRuntimeException(e);
        }
    }

    @Override
    public TransactionService getTransactionService() {
        return new OgmiosTransactionService(wsClient);
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
    public EpochService getEpochService() {
        return new OgmiosEpochService(wsClient);
    }

    @Override
    public MetadataService getMetadataService() {
        throw new UnsupportedOperationException("Not supported yet");
    }
}

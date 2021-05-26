package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.backend.api.helper.UtxoTransactionBuilder;

public interface BackendService {

    public AssetService getAssetService();

    public BlockService getBlockService();

    public NetworkInfoService getNetworkInfoService();

    public TransactionService getTransactionService();

    public UtxoService getUtxoService();

    public AddressService getAddressService();

    default public TransactionHelperService getTransactionHelperService() {
        TransactionHelperService transactionHelperService = new TransactionHelperService(getUtxoService(), getTransactionService());
        return transactionHelperService;
    }

    default public UtxoTransactionBuilder getUtxoTransactionBuilder() {
        UtxoTransactionBuilder utxoTransactionBuilder = new UtxoTransactionBuilder(getUtxoService(), getTransactionService());
        return utxoTransactionBuilder;
    }
}

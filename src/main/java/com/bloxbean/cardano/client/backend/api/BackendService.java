package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.backend.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.backend.api.helper.UtxoTransactionBuilder;
import com.bloxbean.cardano.client.backend.api.helper.impl.FeeCalculationServiceImpl;
import com.bloxbean.cardano.client.backend.api.helper.impl.UtxoTransactionBuilderImpl;

public interface BackendService {

    /**
     * Get AssetService
     * @return
     */
    public AssetService getAssetService();

    /**
     * Get BlockService
     * @return
     */
    public BlockService getBlockService();

    /**
     * Get NetworkInfoService
     * @return
     */
    public NetworkInfoService getNetworkInfoService();

    /**
     * Get Transaction service
     * @return
     */
    public TransactionService getTransactionService();

    /**
     * Get UtxoService
     * @return
     */
    public UtxoService getUtxoService();

    /**
     * Get AddressService
     * @return
     */
    public AddressService getAddressService();

    /**
     * Get EpochService
     * @return
     */
    public EpochService getEpochService();

    /**
     * Get MetadataService
     * @return
     */
    public MetadataService getMetadataService();

    /**
     * Get TransactionHelperService
     * @return
     */
    default public TransactionHelperService getTransactionHelperService() {
        TransactionHelperService transactionHelperService = new TransactionHelperService(getTransactionService(),
                getEpochService(), getUtxoService());
        return transactionHelperService;
    }

    /**
     * Get UtxoTransactionBuilder
     * @return
     */
    default public UtxoTransactionBuilder getUtxoTransactionBuilder() {
        UtxoTransactionBuilder utxoTransactionBuilder = new UtxoTransactionBuilderImpl(getUtxoService());
        return utxoTransactionBuilder;
    }

    /**
     * Get FeeCalculationService
     * @return
     */
    default public FeeCalculationService getFeeCalculationService() {
        return new FeeCalculationServiceImpl(getTransactionHelperService(), getEpochService());
    }

    /**
     * Get FeeCalculationService
     * @param transactionHelperService TransactionHelperService instance
     * @return
     */
    default public FeeCalculationService getFeeCalculationService(TransactionHelperService transactionHelperService) {
        return new FeeCalculationServiceImpl(transactionHelperService, getEpochService());
    }
}

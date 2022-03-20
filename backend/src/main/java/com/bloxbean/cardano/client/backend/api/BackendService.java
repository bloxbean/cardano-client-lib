package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.api.helper.TransactionBuilder;
import com.bloxbean.cardano.client.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.api.helper.UtxoTransactionBuilder;
import com.bloxbean.cardano.client.api.helper.impl.FeeCalculationServiceImpl;
import com.bloxbean.cardano.client.api.helper.impl.UtxoTransactionBuilderImpl;

public interface BackendService {

    /**
     * Get AssetService
     *
     * @return {@link AssetService}
     */
    AssetService getAssetService();

    /**
     * Get BlockService
     *
     * @return {@link BlockService}
     */
    BlockService getBlockService();

    /**
     * Get NetworkInfoService
     *
     * @return {@link NetworkInfoService}
     */
    NetworkInfoService getNetworkInfoService();

    /**
     * Get Transaction service
     *
     * @return {@link TransactionService}
     */
    TransactionService getTransactionService();

    /**
     * Get UtxoService
     *
     * @return {@link UtxoService}
     */
    UtxoService getUtxoService();

    /**
     * Get AddressService
     *
     * @return {@link AddressService}
     */
    AddressService getAddressService();

    /**
     * Get EpochService
     *
     * @return {@link EpochService}
     */
    EpochService getEpochService();

    /**
     * Get MetadataService
     *
     * @return {@link MetadataService}
     */
    MetadataService getMetadataService();

    /**
     * Get TransactionHelperService
     *
     * @return {@link TransactionHelperService}
     */
    default TransactionHelperService getTransactionHelperService() {
        TransactionHelperService transactionHelperService = new TransactionHelperService(
                new TransactionBuilder(new DefaultUtxoSupplier(getUtxoService()), new DefaultProtocolParamsSupplier(getEpochService())),
                new DefaultTransactionProcessor(getTransactionService()));
        return transactionHelperService;
    }

    /**
     * Get UtxoTransactionBuilder
     *
     * @return {@link UtxoTransactionBuilder}
     */
    default UtxoTransactionBuilder getUtxoTransactionBuilder() {
        UtxoTransactionBuilder utxoTransactionBuilder = new UtxoTransactionBuilderImpl(new DefaultUtxoSupplier(getUtxoService()));
        return utxoTransactionBuilder;
    }

    /**
     * Get FeeCalculationService
     *
     * @return {@link FeeCalculationService}
     */
    default FeeCalculationService getFeeCalculationService() {
        return new FeeCalculationServiceImpl(getTransactionHelperService().getTransactionBuilder());
    }

    /**
     * Get FeeCalculationService
     *
     * @param transactionHelperService TransactionHelperService instance
     * @return {@link FeeCalculationService}
     */
    default FeeCalculationService getFeeCalculationService(TransactionHelperService transactionHelperService) {
        return new FeeCalculationServiceImpl(transactionHelperService.getTransactionBuilder());
    }
}

package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.math.BigInteger;
import java.util.List;

/**
 * Interface to build transaction from higher level transaction request apis.
 * It uses {@link UtxoSelectionStrategy} to get appropriate Utxos
 */
public interface UtxoTransactionBuilder {
    /**
     * Set utxo selection strategy
     * @param utxoSelectionStrategy
     */
    void setUtxoSelectionStrategy(UtxoSelectionStrategy utxoSelectionStrategy);

    /**
     * Build Transaction for list of Payment Transactions
     * @param transactions
     * @param detailsParams
     * @param metadata
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     */
    Transaction buildTransaction(List<PaymentTransaction> transactions, TransactionDetailsParams detailsParams, Metadata metadata, ProtocolParams protocolParams) throws ApiException,
            AddressExcepion;

    /**
     * Get required utxos by address, unit and amount by calling {@link UtxoSelectionStrategy}
     * @param address
     * @param unit
     * @param amount
     * @return
     * @throws ApiException
     */
    List<Utxo> getUtxos(String address, String unit, BigInteger amount) throws ApiException;

    /**
     * Build Transaction for token minting
     * @param mintTransaction
     * @param detailsParams
     * @param metadata
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     */
    Transaction buildMintTokenTransaction(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata, ProtocolParams protocolParams) throws ApiException, AddressExcepion;
}

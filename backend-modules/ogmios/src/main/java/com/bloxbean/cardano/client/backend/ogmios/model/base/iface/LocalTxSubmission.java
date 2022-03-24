package com.bloxbean.cardano.client.backend.ogmios.model.base.iface;

import com.bloxbean.cardano.client.backend.ogmios.model.tx.response.EvaluateTxResponse;
import com.bloxbean.cardano.client.backend.ogmios.model.tx.response.SubmitTxResponse;

import java.security.InvalidParameterException;

public interface LocalTxSubmission {

    /**
     * Submit a signed and serialized Utxo transaction to the network.
     * @param cborData CBOR-serialized signed transaction, in base16/base64
     * @return {@link SubmitTxResponse}
     * @throws InvalidParameterException
     */
    SubmitTxResponse submitTx(byte[] cborData) throws InvalidParameterException;

    /**
     * Evaluate script execution costs for a transaction
     * @param cborData  CBOR-serialized signed transaction, in base16/base64
     * @return {@link EvaluateTxResponse}
     * @throws InvalidParameterException
     */
    EvaluateTxResponse evaluateTx(byte[] cborData) throws InvalidParameterException;
}

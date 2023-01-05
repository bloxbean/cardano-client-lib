package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.util.HexUtil;

public class TransactionUtil {

    /**
     * Create a copy of transaction object
     * @param transaction
     * @return
     */
    public static Transaction createCopy(Transaction transaction) {
        try {
            Transaction cloneTxn = Transaction.deserialize(transaction.serialize());
            return cloneTxn;
        } catch (CborDeserializationException e) {
            throw new CborRuntimeException(e);
        } catch (CborSerializationException e) {
            throw new CborRuntimeException(e);
        }
    }

    /**
     * Get transaction hash from Transaction
     * @param transaction
     * @return transaction hash
     */
    public static String getTxHash(Transaction transaction) {
        try {
            transaction.serialize(); //Just to trigger fill body.setAuxiliaryDataHash(), might be removed later.
            return safeGetTxHash(transaction.getBody());
        } catch (Exception ex) {
            throw new RuntimeException("Get transaction hash failed. ", ex);
        }
    }

    /**
     * Get transaction hash from transaction cbor bytes
     * Use this method to get txhash for already executed transaction
     * @param transactionBytes
     * @return transaction hash
     */
    public static String getTxHash(byte[] transactionBytes) {
        try {
            Array array = (Array) CborSerializationUtil.deserialize(transactionBytes);
            DataItem txBodyDI = array.getDataItems().get(0);
            return safeGetTxHash(CborSerializationUtil.serialize(txBodyDI, false));
        } catch (Exception ex) {
            throw new RuntimeException("Get transaction hash failed. ", ex);
        }
    }

    private static String safeGetTxHash(byte[] txBodyBytes) throws Exception {
        return HexUtil.encodeHexString(Blake2bUtil.blake2bHash256(txBodyBytes));
    }

    private static String safeGetTxHash(TransactionBody safeTransactionBody) throws Exception {
        return safeGetTxHash(CborSerializationUtil.serialize(safeTransactionBody.serialize()));
    }
}

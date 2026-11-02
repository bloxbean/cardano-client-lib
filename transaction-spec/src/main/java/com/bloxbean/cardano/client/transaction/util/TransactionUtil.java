package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
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
            cloneTxn.setEra(transaction.getEra());
            return cloneTxn;
        } catch (CborDeserializationException e) {
            throw new CborRuntimeException(e);
        } catch (CborSerializationException e) {
            throw new CborRuntimeException(e);
        }
    }

    /**
     * Get transaction hash from Transaction. This hashes the serialized model, which is right for a transaction built
     * with CCL. For a transaction received as bytes, use {@link #getTxHash(byte[])} or
     * {@link com.bloxbean.cardano.client.transaction.raw.RawTx#txId()}, which hash the body as it was encoded.
     * @param transaction
     * @return transaction hash
     */
    public static String getTxHash(Transaction transaction) {
        try {
            byte[] txBytes = transaction.serialize(); //Just to trigger fill body.setAuxiliaryDataHash(), might be removed later.
            return getTxHash(txBytes);
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
            byte[] txBodyBytes = extractTransactionBodyFromTx(transactionBytes);
            return safeGetTxHash(txBodyBytes);
        } catch (Exception ex) {
            throw new RuntimeException("Get transaction hash failed. ", ex);
        }
    }

    /**
     * Extract transaction body bytes from transaction bytes.
     * The transaction array is walked with {@link CborSpan} (iteratively, so any nesting depth is supported) and the body
     * is returned exactly as encoded. The array header may have any width, including an indefinite-length array.
     * Bytes after the transaction array are ignored.
     * @param txBytes transaction bytes
     * @return transaction body bytes
     * @throws CborRuntimeException if the transaction is not well-formed CBOR or not an array
     */
    public static byte[] extractTransactionBodyFromTx(byte[] txBytes) {
        if (txBytes == null || txBytes.length == 0)
            throw new IllegalArgumentException("Transaction bytes can't be null or empty");

        return CborSpan.at(txBytes, 0).get(0).bytes();
    }

    private static String safeGetTxHash(byte[] txBodyBytes) {
        return HexUtil.encodeHexString(Blake2bUtil.blake2bHash256(txBodyBytes));
    }

}

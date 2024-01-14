package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;

import java.io.ByteArrayInputStream;

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
     * @param txBytes transaction bytes
     * @return transaction body bytes
     */
    public static byte[] extractTransactionBodyFromTx(byte[] txBytes) {
        if (txBytes == null || txBytes.length == 0)
            throw new IllegalArgumentException("Transaction bytes can't be null or empty");

        ByteArrayInputStream bais = new ByteArrayInputStream(txBytes);
        CborDecoder decoder = new CborDecoder(bais);

        //Extract transaction body
        bais.read(); //Skip the first byte as it is a tag
        try {
            decoder.decodeNext();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        int available = bais.available();
        byte[] txBodyRaw = new byte[txBytes.length - available -1]; // -1 for the first byte

        //Copy tx body bytes to txBodyRaw
        System.arraycopy(txBytes,1,txBodyRaw,0,txBodyRaw.length);

        return txBodyRaw;
    }

    private static String safeGetTxHash(byte[] txBodyBytes) {
        return HexUtil.encodeHexString(Blake2bUtil.blake2bHash256(txBodyBytes));
    }

}

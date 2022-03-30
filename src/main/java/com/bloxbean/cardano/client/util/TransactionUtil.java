package com.bloxbean.cardano.client.util;

import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;

public class TransactionUtil {

    public static String getTxHash(Transaction transaction) {
        try {
            transaction.serialize(); //Just to trigger fill body.setAuxiliaryDataHash(), might be removed later.
            return safeGetTxHash(transaction.getBody());
        } catch (Exception ex) {
            throw new RuntimeException("Get transaction hash failed. ", ex);
        }
    }

    public static String getTxHash(byte[] transactionBytes) {
        try {
            Transaction transaction = Transaction.deserialize(transactionBytes);
            return safeGetTxHash(transaction.getBody());
        } catch (Exception ex) {
            throw new RuntimeException("Get transaction hash failed. ", ex);
        }
    }

    private static String safeGetTxHash(TransactionBody safeTransactionBody) throws Exception {
        return HexUtil.encodeHexString(
                KeyGenUtil.blake2bHash256(CborSerializationUtil.serialize(safeTransactionBody.serialize()))
        );
    }
}

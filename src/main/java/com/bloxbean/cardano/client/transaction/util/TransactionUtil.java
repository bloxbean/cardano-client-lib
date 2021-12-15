package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

public class TransactionUtil {

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
}

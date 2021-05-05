package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;

public class TransactionInput {
    private byte[] transaction_id;
    private int index;

    public byte[] getTransactionId() {
        return transaction_id;
    }

    public void setTransactionId(byte[] transactionId) {
        this.transaction_id = transactionId;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void serialize(ArrayBuilder builder) throws CborException {
        builder.add(transaction_id)
                .add(index)
                .end();
    }
}

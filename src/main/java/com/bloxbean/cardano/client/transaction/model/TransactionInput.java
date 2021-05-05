package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;

import java.io.ByteArrayInputStream;
import java.util.List;

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
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        new CborEncoder(baos).encode(new CborBuilder()
//                .addArray()
////                .add()
////                .add(2)
//                .add(transaction_id)
//                .add(index)
//                .end()
//
//                .build());
//        byte[] encodedBytes = baos.toByteArray();
//        return encodedBytes;
    }

    public static TransactionInput deserialize(byte[] bytes) throws CborException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        List<DataItem> dataItems = new CborDecoder(bais).decode();
        System.out.println(dataItems.size());
        for(DataItem dataItem : dataItems) {
            // process data item
            if(dataItem instanceof Array) {
                Array array = (Array) dataItem;
                List<DataItem> items = array.getDataItems();

            }
        }
        return null;
    }
}

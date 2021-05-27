package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionInput {
    private String transactionId;
    private int index;

    public Array serialize() throws CborSerializationException {
        Array inputArray = new Array();
        byte[] transactionIdBytes = HexUtil.decodeHexString(transactionId);
        inputArray.add(new ByteString(transactionIdBytes));
        inputArray.add(new UnsignedInteger(index));

        return inputArray;
    }

    public static TransactionInput deserialize(Array inputItem) throws CborDeserializationException {
        List<DataItem> items = inputItem.getDataItems();

        if(items == null || items.size() != 2) {
            throw new CborDeserializationException("TransactionInput deserialization failed. Invalid no of DataItems");
        }

        TransactionInput transactionInput = new TransactionInput();

        ByteString txnIdBytes = (ByteString) items.get(0);
        if(txnIdBytes != null)
            transactionInput.setTransactionId(HexUtil.encodeHexString(txnIdBytes.getBytes()));

        UnsignedInteger indexUI = (UnsignedInteger) items.get(1);
        if(indexUI != null)
            transactionInput.setIndex(indexUI.getValue().intValue());

        return transactionInput;
    }

    @Override
    public String toString() {
        try {
            return "TransactionInput{" +
                    "transactionId=" + transactionId +
                    ", index=" + index +
                    '}';
        } catch (Exception e) {
            return "TransactionInput { Error : " + e.getMessage() + " }";
        }
    }
}

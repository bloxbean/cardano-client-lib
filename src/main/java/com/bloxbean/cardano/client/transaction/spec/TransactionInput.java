package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionInput {
    private String transactionId;
    private int index;

    public Array serialize() throws CborException {
        Array inputArray = new Array();
        byte[] transactionIdBytes = HexUtil.decodeHexString(transactionId);
        inputArray.add(new ByteString(transactionIdBytes));
        inputArray.add(new UnsignedInteger(index));

        return inputArray;
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

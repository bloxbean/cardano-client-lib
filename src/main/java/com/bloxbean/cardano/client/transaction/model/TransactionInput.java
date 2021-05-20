package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionInput {
    private String transactionId;
    private int index;

    public void serialize(ArrayBuilder builder) throws CborException {
        byte[] transactionIdBytes = HexUtil.decodeHexString(transactionId);
        builder.add(transactionIdBytes)
                .add(index)
                .end();
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

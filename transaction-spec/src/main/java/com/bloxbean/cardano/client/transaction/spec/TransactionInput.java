package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.raw.RawInput;
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

    public Array serialize() throws CborSerializationException {
        Array inputArray = new Array();
        byte[] transactionIdBytes = HexUtil.decodeHexString(transactionId);
        inputArray.add(new ByteString(transactionIdBytes));
        inputArray.add(new UnsignedInteger(index));

        return inputArray;
    }

    public static TransactionInput deserialize(Array inputItem) throws CborDeserializationException {
        try {
            return from(RawInput.of(CborSerializationUtil.span(inputItem)));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("TransactionInput deserialization failed", e);
        }
    }

    static TransactionInput from(RawInput input) {
        return new TransactionInput(HexUtil.encodeHexString(input.transactionId()), input.index());
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

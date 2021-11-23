package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BigIntPlutusData implements PlutusData {
    private BigInteger value;

    @Override
    public DataItem serialize() throws CborSerializationException {
        DataItem di = null;
        if (value != null) {
            if (value.compareTo(BigInteger.ZERO) == 0 || value.compareTo(BigInteger.ZERO) == 1) {
                di = new UnsignedInteger(value);
            } else {
                di = new NegativeInteger(value);
            }
        }

        return di;
    }

    public static BigIntPlutusData deserialize(Number numberDI) throws CborDeserializationException {
        if (numberDI == null)
            return null;

        return new BigIntPlutusData(numberDI.getValue());
    }

}

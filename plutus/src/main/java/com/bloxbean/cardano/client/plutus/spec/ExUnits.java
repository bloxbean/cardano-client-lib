package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.raw.RawExUnits;
import com.bloxbean.cardano.client.transaction.raw.RawItems;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExUnits {
    private BigInteger mem;
    private BigInteger steps;

    public Array serialize() {
        Array array = new Array();
        array.add(new UnsignedInteger(mem));
        array.add(new UnsignedInteger(steps));

        return array;
    }

    public static ExUnits deserialize(Array exUnitDI) throws CborDeserializationException {
        try {
            return from(RawExUnits.of(CborSerializationUtil.span(exUnitDI)));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("ExUnits deserialization failed", e);
        }
    }

    /**
     * @param raw execution units as received
     * @return the model
     * @throws CborDeserializationException if they have an indefinite length, which this model rejects: the ledger
     * decodes                                      them with {@code RecD}
     */
    public static ExUnits from(RawExUnits raw) throws CborDeserializationException {
        if (RawItems.indefiniteWhenDecoded(raw.span()))
            throw new CborDeserializationException("Invalid ex_units: expected a definite-length array");
        return new ExUnits(raw.mem(), raw.steps());
    }

}

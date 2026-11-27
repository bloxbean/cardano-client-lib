package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
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
        return from(raw, null);
    }

    /**
     * Reads execution units as {@link #from(RawExUnits)} does, in an era: from Dijkstra they may be indefinite
     * ({@link RawItems#indefiniteRecordsAllowed(Era)}).
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param raw execution units as received
     * @param era the era they were read in, or null
     * @return the model
     * @throws CborDeserializationException if they have an indefinite length and the era does not allow it
     */
    public static ExUnits from(RawExUnits raw, Era era) throws CborDeserializationException {
        if (!RawItems.indefiniteRecordsAllowed(era) && RawItems.indefiniteWhenDecoded(raw.span()))
            throw new CborDeserializationException("Invalid ex_units: expected a definite-length array");
        return new ExUnits(raw.mem(), raw.steps());
    }

}

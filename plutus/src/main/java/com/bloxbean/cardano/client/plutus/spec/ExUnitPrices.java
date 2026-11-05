package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.RationalNumber;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.raw.RawExUnitPrices;
import lombok.*;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.bigIntegerToDataItem;


@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class ExUnitPrices {
    private UnitInterval memPrice;
    private UnitInterval stepPrice;

    public DataItem serialize() throws CborSerializationException {
        Array array = new Array();

        try {
            RationalNumber memPriceRN = new RationalNumber(bigIntegerToDataItem(memPrice.getNumerator()),
                    bigIntegerToDataItem(memPrice.getDenominator()));
            RationalNumber stepPriceRN = new RationalNumber(bigIntegerToDataItem(stepPrice.getNumerator()),
                    bigIntegerToDataItem(stepPrice.getDenominator()));

            array.add(memPriceRN);
            array.add(stepPriceRN);
        } catch (Exception e) {
            throw new CborSerializationException("ExUnitPrices serialization error", e);
        }

        return array;
    }

    public static ExUnitPrices deserialize(DataItem di) throws CborDeserializationException {
        try {
            return from(RawExUnitPrices.lenient(CborSerializationUtil.span(di)));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("ExUnitPrices deserialization failed", e);
        }
    }

    /**
     * @param raw execution unit prices as received
     * @return the model
     */
    public static ExUnitPrices from(RawExUnitPrices raw) {
        return new ExUnitPrices(raw.memory(), raw.steps());
    }
}

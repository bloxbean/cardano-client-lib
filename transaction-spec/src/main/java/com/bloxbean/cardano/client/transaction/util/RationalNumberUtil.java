package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.RationalNumber;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Rational;
import com.bloxbean.cardano.client.spec.UnitInterval;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.bigIntegerToDataItem;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.getBigInteger;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.withoutBreak;

public class RationalNumberUtil {

    /**
     * Convert a RationalNumber to {@link Rational}
     *
     * @param rn
     * @return
     */
    public static Rational toRational(RationalNumber rn) {
        return new Rational(getBigInteger(rn.getNumerator()), getBigInteger(rn.getDenominator()));
    }

    /**
     * Convert a RationalNumber to {@link UnitInterval}
     *
     * @param rn
     * @return
     */
    public static UnitInterval toUnitInterval(RationalNumber rn) {
        return new UnitInterval(getBigInteger(rn.getNumerator()), getBigInteger(rn.getDenominator()));
    }

    /**
     * @param intervals the items, in order
     * @return a definite-length array of tag 30 rationals
     * @throws CborSerializationException if an item cannot be encoded
     */
    public static Array toRationalArray(UnitInterval... intervals) throws CborSerializationException {
        Array array = new Array();
        for (UnitInterval interval : intervals) {
            array.add(toRationalNumber(interval));
        }
        return array;
    }

    /**
     * @param interval a unit interval or {@link Rational}
     * @return its tag 30 rational
     * @throws CborSerializationException if it cannot be encoded
     */
    public static RationalNumber toRationalNumber(UnitInterval interval) throws CborSerializationException {
        try {
            return new RationalNumber(bigIntegerToDataItem(interval.getNumerator()),
                    bigIntegerToDataItem(interval.getDenominator()));
        } catch (CborException e) {
            throw new CborSerializationException("Invalid rational", e);
        }
    }

    /**
     * @param array an array of tag 30 rationals, of either length form, as Haskell's {@code decodeRecordNamed} reads it
     * @param name  what the array is, for the error message
     * @param size  the required number of items
     * @return the items, in order
     */
    public static List<UnitInterval> toUnitIntervals(Array array, String name, int size) {
        List<DataItem> items = withoutBreak(array.getDataItems());
        if (items.size() != size)
            throw new CborRuntimeException("Invalid " + name + ": expected " + size + " items, found " + items.size());
        List<UnitInterval> intervals = new ArrayList<>(size);
        for (DataItem item : items) {
            intervals.add(toUnitInterval((RationalNumber) item));
        }
        return intervals;
    }
}

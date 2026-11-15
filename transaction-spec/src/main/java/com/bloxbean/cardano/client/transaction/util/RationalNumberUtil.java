package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.RationalNumber;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Rational;
import com.bloxbean.cardano.client.spec.UnitInterval;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.bigIntegerToDataItem;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.getBigInteger;

public class RationalNumberUtil {

    /**
     * Convert a RationalNumber to {@link Rational}
     *
     * @param rn
     * @return the rational, reduced as {@link #toUnitInterval(RationalNumber)} reduces it
     */
    public static Rational toRational(RationalNumber rn) {
        UnitInterval reduced = toUnitInterval(rn);
        return new Rational(reduced.getNumerator(), reduced.getDenominator());
    }

    /**
     * Convert a RationalNumber to {@link UnitInterval}
     *
     * @param rn
     * @return the rational reduced as the ledger reads it, {@link UnitInterval#reduced}
     */
    public static UnitInterval toUnitInterval(RationalNumber rn) {
        return UnitInterval.reduced(getBigInteger(rn.getNumerator()), getBigInteger(rn.getDenominator()));
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
}

package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.model.RationalNumber;
import com.bloxbean.cardano.client.transaction.spec.Rational;
import com.bloxbean.cardano.client.transaction.spec.UnitInterval;

import static com.bloxbean.cardano.client.transaction.util.CborSerializationUtil.getBigInteger;

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
}

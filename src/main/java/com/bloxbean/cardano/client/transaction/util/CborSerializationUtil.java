package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import co.nstant.in.cbor.model.Number;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.spec.Rational;
import com.bloxbean.cardano.client.transaction.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.util.cbor.CustomCborEncoder;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.NonNull;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

public class CborSerializationUtil {

    /**
     * Covert a CBOR DataItem to BigInteger
     *
     * @param valueItem
     * @return
     */
    public static BigInteger getBigInteger(DataItem valueItem) {
        BigInteger value = null;
        if (MajorType.UNSIGNED_INTEGER.equals(valueItem.getMajorType())
                || MajorType.NEGATIVE_INTEGER.equals(valueItem.getMajorType())) {
            value = ((Number) valueItem).getValue();
        } else if (MajorType.BYTE_STRING.equals(valueItem.getMajorType())) { //For BigNum. >  2 pow 64 Tag 2
            if (valueItem.getTag().getValue() == 2) { //positive
                value = new BigInteger(((ByteString) valueItem).getBytes());
            } else if (valueItem.getTag().getValue() == 3) { //Negative
                value = new BigInteger(((ByteString) valueItem).getBytes()).multiply(BigInteger.valueOf(-1));
            }
        }

        return value;
    }

    /**
     * Convert a BigInteger to {@link Number}
     * @param bi
     * @return
     */
    public static Number bigIntegerToDataItem(BigInteger bi) {
        if (bi.compareTo(BigInteger.ZERO) == -1) {
            return new NegativeInteger(bi);
        } else {
            return new UnsignedInteger(bi);
        }
    }

    /**
     * Convert a CBOR DataItem to long
     * @param valueItem
     * @return
     */
    public static long toLong(DataItem valueItem) {
        return getBigInteger(valueItem).longValue();
    }

    /**
     * Convert a CBOR DataItem to int
     * @param valueItem
     * @return
     */
    public static int toInt(DataItem valueItem) {
        return getBigInteger(valueItem).intValue();
    }

    /**
     * Convert a ByteString dataitem to Hex value
     * @param di
     * @return
     */
    public static String toHex(DataItem di) {
        return HexUtil.encodeHexString(((ByteString)di).getBytes());
    }

    /**
     * Convert a ByteString dataitem to byte[]
     * @param di
     * @return
     */
    public static byte[] toBytes(DataItem di) {
        return ((ByteString)di).getBytes();
    }

    /**
     * Convert a UnicodeString dataitem to String
     * @param di
     * @return
     */
    public static String toUnicodeString(DataItem di) {
        return ((UnicodeString)di).getString();
    }

    /**
     * Convert a RationalNumber to {@link Rational}
     * @param rn
     * @return
     */
    public static Rational toRational(RationalNumber rn) {
        return new Rational(getBigInteger(rn.getNumerator()), getBigInteger(rn.getDenominator()));
    }

    /**
     * Convert a RationalNumber to {@link UnitInterval}
     * @param rn
     * @return
     */
    public static UnitInterval toUnitInterval(RationalNumber rn) {
        return new UnitInterval(getBigInteger(rn.getNumerator()), getBigInteger(rn.getDenominator()));
    }

    /**
     * Serialize CBOR DataItem as byte[]
     *
     * @param value
     * @return
     * @throws CborException
     */
    public static byte[] serialize(DataItem value) throws CborException {
        return serialize(new DataItem[]{value}, true); //By default Canonical = true
    }

    /**
     * Serialize CBOR DataItem as byte[]
     *
     * @param value
     * @param canonical
     * @return
     * @throws CborException
     */
    public static byte[] serialize(DataItem value, boolean canonical) throws CborException {
        return serialize(new DataItem[]{value}, canonical);
    }

    /**
     * Serialize CBOR DataItems as byte[]
     *
     * @param values
     * @return
     */
    public static byte[] serialize(DataItem[] values) throws CborException {
        return serialize(values, true); //By default Canonical = true
    }

    /**
     * Serialize CBOR DataItems as byte[]
     *
     * @param values
     * @param canonical
     * @return
     * @throws CborException
     */
    public static byte[] serialize(DataItem[] values, boolean canonical) throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CborBuilder cborBuilder = new CborBuilder();

        for (DataItem value : values) {
            cborBuilder.add(value);
        }

        if (canonical) {
            new CustomCborEncoder(baos).encode(cborBuilder.build());
        } else {
            new CustomCborEncoder(baos).nonCanonical().encode(cborBuilder.build());
        }

        byte[] encodedBytes = baos.toByteArray();

        return encodedBytes;

    }

    /**
     * Deserialize bytes to DataItem
     * @param bytes
     * @return DataItem
     */
    public static DataItem deserialize(@NonNull byte[] bytes) {
        try {
            return CborDecoder.decode(bytes).get(0);
        } catch (CborException e) {
            throw new CborRuntimeException("Cbor de-serialization error", e);
        }
    }
}

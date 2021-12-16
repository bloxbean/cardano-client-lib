package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Number;

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
            new CborEncoder(baos).encode(cborBuilder.build());
        } else {
            new CborEncoder(baos).nonCanonical().encode(cborBuilder.build());
        }

        byte[] encodedBytes = baos.toByteArray();

        return encodedBytes;

    }
}

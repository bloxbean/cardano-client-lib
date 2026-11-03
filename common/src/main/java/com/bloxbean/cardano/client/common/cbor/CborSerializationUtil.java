package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.custom.CustomCborEncoder;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

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
                value = CborSpan.bignum(true, ((ByteString) valueItem).getBytes());
            } else if (valueItem.getTag().getValue() == 3) { //Negative
                value = CborSpan.bignum(false, ((ByteString) valueItem).getBytes());
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
     * Convert a CBOR DataItem to int, rejecting a value an int cannot hold instead of wrapping it as {@link #toInt}
     * does.
     * @param valueItem an integer or bignum
     * @return the value
     * @throws CborRuntimeException if the value does not fit in an int
     */
    public static int toIntExact(DataItem valueItem) {
        BigInteger value = getBigInteger(valueItem);
        if (value == null || value.bitLength() > 31)
            throw new CborRuntimeException("Expected a 32-bit integer, found : " + valueItem);
        return value.intValue();
    }

    /**
     * Convert a CBOR DataItem to a long holding an unsigned 64-bit value. A value at or above 2^63 reads as negative
     * through the long, and {@link #unsignedLongItem(long)} writes it back exactly.
     * @param valueItem an integer or bignum
     * @return the value's 64 bits
     * @throws CborRuntimeException if the value is negative or does not fit in 64 bits
     */
    public static long toUnsignedLong(DataItem valueItem) {
        BigInteger value = getBigInteger(valueItem);
        if (value == null || value.signum() < 0 || value.bitLength() > 64)
            throw new CborRuntimeException("Expected an unsigned 64-bit integer, found : " + valueItem);
        return value.longValue();
    }

    /**
     * @param value the 64 bits of an unsigned value, as {@link #toUnsignedLong(DataItem)} reads it
     * @return the unsigned integer
     */
    public static UnsignedInteger unsignedLongItem(long value) {
        return new UnsignedInteger(new BigInteger(Long.toUnsignedString(value)));
    }

    /**
     * The items of an array that must have a definite length. cardano-ledger accepts both lengths for most arrays, but
     * up to protocol version 11 (Conway) a record decoded with Coders' {@code RecD} reads the list header before its
     * fields ({@code decodeCount (RecD _)}), and a tuple with {@code decodeListLenOf}, so an indefinite-length one is
     * rejected.
     *
     * @param array the array
     * @param name  what the array is, for the error message
     * @return the items of the array
     * @throws CborRuntimeException if the array has an indefinite length
     */
    public static List<DataItem> definiteItems(Array array, String name) {
        if (array.isChunked())
            throw new CborRuntimeException("Invalid " + name + ": expected a definite-length array");
        return array.getDataItems();
    }

    /**
     * The items of an array, without the BREAK that cbor-java's decoder ends an indefinite-length one with.
     *
     * @param items the items of the array
     * @return the items, without a trailing BREAK
     */
    public static List<DataItem> withoutBreak(List<DataItem> items) {
        int size = items.size();
        return size > 0 && items.get(size - 1) == Special.BREAK ? items.subList(0, size - 1) : items;
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

//
//    /**
//     * Convert a RationalNumber to {@link Rational}
//     * @param rn
//     * @return
//     */
//    public static Rational toRational(RationalNumber rn) {
//        return new Rational(getBigInteger(rn.getNumerator()), getBigInteger(rn.getDenominator()));
//    }
//
//    /**
//     * Convert a RationalNumber to {@link UnitInterval}
//     * @param rn
//     * @return
//     */
//    public static UnitInterval toUnitInterval(RationalNumber rn) {
//        return new UnitInterval(getBigInteger(rn.getNumerator()), getBigInteger(rn.getDenominator()));
//    }

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
        EncodedBytes out = new EncodedBytes();
        CborBuilder cborBuilder = new CborBuilder();

        for (DataItem value : values) {
            cborBuilder.add(value);
        }

        if (canonical) {
            new CustomCborEncoder(out).encode(cborBuilder.build());
        } else {
            new CustomCborEncoder(out).nonCanonical().encode(cborBuilder.build());
        }

        return out.toByteArray();
    }

    // CustomCborEncoder writes each item with one write call; keeping that array saves a copy for the usual single
    // item.
    private static final class EncodedBytes extends OutputStream {
        private byte[] single;
        private ByteArrayOutputStream several;

        @Override
        public void write(int b) {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (single == null && several == null) {
                single = Arrays.copyOfRange(b, off, off + len);
                return;
            }
            if (several == null) {
                several = new ByteArrayOutputStream(single.length + len);
                several.write(single, 0, single.length);
            }
            several.write(b, off, len);
        }

        byte[] toByteArray() {
            if (several != null)
                return several.toByteArray();
            return single != null ? single : new byte[0];
        }
    }

    /**
     * Deserialize bytes to DataItem. Every item in the bytes is decoded and must be well-formed; the first is returned.
     * <p>
     * Decoding is iterative, so items of any nesting depth decode on any thread, and produces the same trees as
     * cbor-java's {@code CborDecoder}, except that maps are
     * {@link com.bloxbean.cardano.client.common.cbor.custom.EncodedKeyMap}s and an indefinite-length array is
     * {@code chunked} without a trailing {@link Special#BREAK} item, so its items are just its elements. Encode such a
     * tree with {@link #serialize(DataItem)}: cbor-java's own encoder writes an indefinite array's end only from that
     * item, so it would leave the array unterminated.
     * Input that is not well-formed CBOR (RFC 8949) is rejected.
     *
     * @param bytes CBOR bytes
     * @return DataItem
     * @throws CborRuntimeException if the bytes are empty or not well-formed CBOR
     */
    public static DataItem deserialize(@NonNull byte[] bytes) {
        return DataItemDecoder.decodeFirst(bytes);
    }

    /**
     * Deserialize every CBOR data item in the bytes, as {@link #deserialize(byte[])} does.
     *
     * @param bytes CBOR bytes
     * @return the data items, in order; empty for empty input
     * @throws CborRuntimeException if the bytes are not well-formed CBOR
     */
    public static List<DataItem> deserializeAll(@NonNull byte[] bytes) {
        return DataItemDecoder.decodeAll(bytes);
    }
}

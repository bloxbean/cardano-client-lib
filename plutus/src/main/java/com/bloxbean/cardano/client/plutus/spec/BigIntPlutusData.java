package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.*;
import co.nstant.in.cbor.model.Number;
import com.bloxbean.cardano.client.common.cbor.custom.ChunkedByteString;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.serializers.BigIntDataJsonDeserializer;
import com.bloxbean.cardano.client.plutus.spec.serializers.BigIntDataJsonSerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;

import java.math.BigInteger;
import java.util.Arrays;

import static com.bloxbean.cardano.client.plutus.util.Bytes.getChunks;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@JsonSerialize(using = BigIntDataJsonSerializer.class)
@JsonDeserialize(using = BigIntDataJsonDeserializer.class)
public class BigIntPlutusData implements PlutusData {
    private BigInteger value;

    public static BigIntPlutusData deserialize(Number numberDI) {
        if (numberDI == null)
            return null;

        return new BigIntPlutusData(numberDI.getValue());
    }

    public static BigIntPlutusData deserialize(ByteString byteString) {
        if (byteString == null)
            return null;

        var tag = byteString.getTag();
        if (tag != null) {
            switch ((int) tag.getValue()) {
                case BIG_UINT_TAG:
                    return BigIntPlutusData.of(new BigInteger(1, byteString.getBytes()));
                case BIG_NINT_TAG:
                    return BigIntPlutusData.of(MINUS_ONE.subtract(new BigInteger(1, byteString.getBytes())));
                default:
                    throw new IllegalArgumentException("Invalid tag for BigIntPlutusData");
            }
        } else {
            throw new IllegalArgumentException("Missing tag for BigIntPlutusData");
        }
    }


    public static BigIntPlutusData of(int i) {
        return new BigIntPlutusData(BigInteger.valueOf(i));
    }

    public static BigIntPlutusData of(long l) {
        return new BigIntPlutusData(BigInteger.valueOf(l));
    }

    public static BigIntPlutusData of(BigInteger b) {
        return new BigIntPlutusData(b);
    }

    /**
     * Serializes as plutus-core {@code encodeData} does (plutus 1.65.0.0 {@code PlutusCore/Data.hs}
     * {@code encodeInteger}, {@code encodeBs}): an integer from -2^64 to 2^64-1 as a CBOR integer; any other as tag 2 over
     * n, or tag 3 over -1 - n, in minimal big-endian bytes, which are one byte string up to 64 bytes and an indefinite
     * byte string of 64-byte chunks above.
     */
    @Override
    public DataItem serialize() throws CborSerializationException {
        DataItem di = null;
        if (value != null) {
            if (value.bitLength() <= BYTES_LIMIT) {
                if (value.signum() >= 0) {
                    di = new UnsignedInteger(value);
                } else {
                    di = new NegativeInteger(value);
                }
            } else {
                boolean negative = value.signum() < 0;
                byte[] bytes = magnitudeBytes(negative ? MINUS_ONE.subtract(value) : value);
                di = bytes.length <= BYTES_LIMIT
                        ? new ByteString(bytes) : new ChunkedByteString(getChunks(bytes, BYTES_LIMIT));
                di.setTag(negative ? BIG_NINT_TAG : BIG_UINT_TAG);
            }
        }

        return di;
    }

    // Big-endian bytes of a positive number without the sign byte BigInteger.toByteArray adds when the top bit is set.
    private static byte[] magnitudeBytes(BigInteger positive) {
        byte[] bytes = positive.toByteArray();
        return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

}

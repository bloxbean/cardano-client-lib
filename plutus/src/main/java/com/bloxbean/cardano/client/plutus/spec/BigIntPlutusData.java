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
                byte[] bytes = value.toByteArray();
                if (value.signum() < 0) {
                    bytes = negateBytes(bytes);
                    di = new ChunkedByteString(getChunks(bytes, BYTES_LIMIT));
                    di.setTag(BIG_NINT_TAG);
                } else {
                    di = new ChunkedByteString(getChunks(bytes, BYTES_LIMIT));
                    di.setTag(BIG_UINT_TAG);
                }
            }
        }

        return di;
    }

    private byte[] negateBytes(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ~bytes[i];
        }
        return bytes;
    }

}

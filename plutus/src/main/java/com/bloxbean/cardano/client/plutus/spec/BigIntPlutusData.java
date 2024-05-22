package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.*;
import co.nstant.in.cbor.model.Number;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.serializers.BigIntDataJsonDeserializer;
import com.bloxbean.cardano.client.plutus.spec.serializers.BigIntDataJsonSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;

import java.math.BigInteger;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@JsonSerialize(using = BigIntDataJsonSerializer.class)
@JsonDeserialize(using = BigIntDataJsonDeserializer.class)
public class BigIntPlutusData implements PlutusData {
    private BigInteger value;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    private boolean encodeAsByteString;

    public BigIntPlutusData(BigInteger value) {
        this.value = value;
    }

    public static BigIntPlutusData deserialize(Number numberDI) throws CborDeserializationException {
        if (numberDI == null)
            return null;

        return new BigIntPlutusData(numberDI.getValue());
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
            if (encodeAsByteString) {
                if (value.compareTo(BigInteger.ZERO) == 0 || value.compareTo(BigInteger.ZERO) == 1) {
                    byte[] bytes = value.toByteArray();
                    di = new ByteString(bytes);
                    di.setTag(new Tag(BIG_UINT_TAG));
                } else {
                    byte[] bytes = value.negate().toByteArray();
                    di = new ByteString(bytes);
                    di.setTag(new Tag(BIG_NINT_TAG));
                }
            } else {
                if (value.compareTo(BigInteger.ZERO) == 0 || value.compareTo(BigInteger.ZERO) == 1) {
                    di = new UnsignedInteger(value);
                } else {
                    di = new NegativeInteger(value);
                }
            }
        }

        return di;
    }

    public BigIntPlutusData encodeAsByteString(boolean flag) {
        this.encodeAsByteString = flag;
        return this;
    }
}

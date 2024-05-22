package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BigIntPlutusDataTest {

    @Test
    void deserialize_overLongNegative() throws Exception {
        NegativeInteger negativeInteger = new NegativeInteger(new BigInteger("-10000000000000000020000003000000004000000"));
        var bytes = CborSerializationUtil.serialize(negativeInteger);

        BigIntPlutusData bigIntPlutusData = BigIntPlutusData.deserialize((ByteString) CborSerializationUtil.deserialize(bytes));
        assertThat(bigIntPlutusData.getValue()).isEqualTo(new BigInteger("-10000000000000000020000003000000004000000"));
    }

    @Test
    void deserialize_longNegative() throws Exception {
        NegativeInteger negativeInteger = new NegativeInteger(BigInteger.valueOf(Long.MAX_VALUE).negate());
        var bytes = CborSerializationUtil.serialize(negativeInteger);

        BigIntPlutusData bigIntPlutusData = BigIntPlutusData.deserialize((Number) CborSerializationUtil.deserialize(bytes));
        assertThat(bigIntPlutusData.getValue()).isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).negate());
    }

    @Test
    void deserialize_overLong() throws Exception {
        UnsignedInteger integer = new UnsignedInteger(new BigInteger("10000000000000000020000003000000004000000"));
        var bytes = CborSerializationUtil.serialize(integer);

        BigIntPlutusData bigIntPlutusData = BigIntPlutusData.deserialize((ByteString) CborSerializationUtil.deserialize(bytes));
        assertThat(bigIntPlutusData.getValue()).isEqualTo(new BigInteger("10000000000000000020000003000000004000000"));
    }

    @Test
    void deserialize_long() throws Exception {
        UnsignedInteger integer = new UnsignedInteger(BigInteger.valueOf(Long.MAX_VALUE));
        var bytes = CborSerializationUtil.serialize(integer);

        BigIntPlutusData bigIntPlutusData = BigIntPlutusData.deserialize((Number) CborSerializationUtil.deserialize(bytes));
        assertThat(bigIntPlutusData.getValue()).isEqualTo(BigInteger.valueOf(Long.MAX_VALUE));
    }
}

package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.util.HexUtil;
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

    @Test
    void greaterThan64bytes_positiveNumber() {
        var twoTo520 = BigInteger.valueOf(2).pow(520);
        var biPDTwoTo520 = BigIntPlutusData.of(twoTo520);

        var serHex = biPDTwoTo520.serializeToHex();
        var expectedTwo =
                "c25f584001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000420000ff";

        assertThat(serHex).isEqualTo(expectedTwo);
    }

    @Test
    void greaterThan64bytes_positiveNumber_roundtrip() throws CborDeserializationException {
        var twoTo520 = BigInteger.valueOf(2).pow(520);
        var biPDTwoTo520 = BigIntPlutusData.of(twoTo520);

        var serHex = biPDTwoTo520.serializeToHex();
        var deTwoTo520 = ((BigIntPlutusData)PlutusData.deserialize(HexUtil.decodeHexString(serHex))).getValue();

        assertThat(deTwoTo520).isEqualTo(twoTo520);
    }

    @Test
    void greaterThan64bytes_negativeNumber() throws CborDeserializationException {
        var minusTwoTo520 = BigInteger.valueOf(2).pow(520).negate();
        var biPDMinusTwoTo520 = BigIntPlutusData.of(minusTwoTo520);

        var serHex = biPDMinusTwoTo520.serializeToHex();
        var expectedMinusTwo =
                "c35f584000ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff42ffffff";

        assertThat(serHex).isEqualTo(expectedMinusTwo);
    }

    @Test
    void greaterThan64bytes_negativeNumber_roundtrip() throws CborDeserializationException {
        var minusTwoTo520 = BigInteger.valueOf(2).pow(520).negate();
        var biPDMinusTwoTo520 = BigIntPlutusData.of(minusTwoTo520);

        var serHex = biPDMinusTwoTo520.serializeToHex();
        var deBint = ((BigIntPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(serHex))).getValue();

        assertThat(deBint).isEqualTo(minusTwoTo520);
    }

    @Test
    void greaterThan64bytes_negativeNumber_roundtrip_2() throws CborDeserializationException {
        var minusTwoTo520 = BigInteger.valueOf(9).pow(520).negate();

        var bint = BigIntPlutusData.of(minusTwoTo520);
        var serHex = bint.serializeToHex();

        var deBint = (BigIntPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(serHex));
        assertThat(bint.getValue()).isEqualTo(deBint.getValue());

    }
}

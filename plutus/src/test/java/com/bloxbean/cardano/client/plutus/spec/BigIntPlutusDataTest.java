package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        // tag 3 over 2^520 - 1: 65 bytes 0xff, without a sign byte, in chunks of 64 and 1 (plutus encodeData)
        var expectedMinusTwo = "c35f5840" + "ff".repeat(64) + "41ff" + "ff";

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

    /**
     * plutus-core 1.65.0.0 {@code encodeData} run on cborg 0.2.8.0 (the oracle golden.tsv of bloxbean/julc#236):
     * a CBOR integer from -2^64 to 2^64-1, else tag 2 over n or tag 3 over -1 - n in minimal big-endian bytes, one
     * byte string up to 64 bytes and 64-byte chunks above. Scalus 1.1.1 {@code Data.toCbor} writes the same bytes.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "0, 0, 00",
            "2^63, 9223372036854775808, 1b8000000000000000",
            "-2^63, -9223372036854775808, 3b7fffffffffffffff",
            "2^64, 18446744073709551616, c249010000000000000000",
            "-2^64, -18446744073709551616, 3bffffffffffffffff",
            "2^64-1, 18446744073709551615, 1bffffffffffffffff",
            "-(2^64-1), -18446744073709551615, 3bfffffffffffffffe",
            "-2^64-1, -18446744073709551617, c349010000000000000000",
            "2^511, 2^511, c2584080000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
            "2^512, 2^512, c25f5840010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004100ff",
            "2^528, 2^528, c25f58400100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043000000ff",
            "-2^528, -2^528, c35f5840ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff42ffffff",
    })
    void serializesAsPlutusEncodeData(String name, String value, String plutusHex) throws Exception {
        BigInteger n = value.contains("^")
                ? BigInteger.TWO.pow(Integer.parseInt(value.substring(value.indexOf('^') + 1)))
                        .multiply(value.startsWith("-") ? BigInteger.ONE.negate() : BigInteger.ONE)
                : new BigInteger(value);

        assertThat(BigIntPlutusData.of(n).serializeToHex()).isEqualTo(plutusHex);
        assertThat(((BigIntPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(plutusHex))).getValue())
                .isEqualTo(n);
    }

    @Test
    void stillDecodesChunkedAndSignBytePayloads() throws Exception {
        // what serialize wrote before: a chunked payload of 64 bytes or less, and a leading sign byte
        assertThat(((BigIntPlutusData) PlutusData.deserialize(HexUtil.decodeHexString("c25f4a01f66b56600810500000ff")))
                .getValue()).isEqualTo(new BigInteger("01f66b56600810500000", 16));
        assertThat(((BigIntPlutusData) PlutusData.deserialize(HexUtil.decodeHexString("c25f4a00ffffffffffffffffffff")))
                .getValue()).isEqualTo(BigInteger.TWO.pow(72).subtract(BigInteger.ONE));
        assertThat(((BigIntPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(
                "c35f584000" + "ff".repeat(63) + "42ffff" + "ff"))).getValue())
                .isEqualTo(BigInteger.TWO.pow(520).negate());
    }

    /**
     * Real datums with bignums re-encode to their original bytes, so the model's datum hash is the hash of the original
     * bytes: a definite 10-byte payload (preview tx fa0fa5d1bb61..., slot 833158), a negative one (preview tx
     * 0afb84debe54..., slot 1363295), a 63-byte payload with the top bit set and a chunked one (preprod tx
     * f5404dd76b9e..., slot 44215949).
     */
    @ParameterizedTest
    @CsvSource({
            "8423d873bc6fe11c3dafaaaf452effcbdff65ddda66ca63d9a298bc1e0debcc1, d87a9f581c83d6a70fc8175ef9b6c28e852325b084bd687a20eee6a91a074032b14469425443d8799fd8799f1b0de0b6b3a7640000ffd8799fc24a01f66b56600810500000ffd8799f00ff0000ffff",
            "2008689c8b436b530cbf4264f52721581f1e5fb582c42ead82419e98626cc250, d8799f4469555344d8799fd8799fc349073a5f283e60eba598ffd8799f3b0de0b6b3ab1dac9fffd8799f1b20efc636a6368abfff0001ffa2d8799f0000ffd8799f1b20efc636a6368abfffd8799f0001ffd8799f1b20efc636a6368abfffff",
            "6bae58901be8f775c34403fcecfd7cd1b5225794e2d9de2ff25dbbecca965c52, d87a9fd8799f5820712efdce64f1207f1789ab127f3270c195ee471bc6d79d696a9dc9763b38c66f1b0000000ba43b740100d8799fc2583f9e5c5496cda97fb176f542e977bd65e2eeef34a8b952637933ff3ffb8ea7487fbc5dc392575001cedf2f1e2b6d3714d075b17d9be37e6f4b35bffa56ab6786c2583c082edac5197178b7490f9d2ae181eda3af21560236df52ddf6d4cc9845f3de55e0dfa1b7d9ec43328aa7f601bc1215d20ddb2e7e4047544fbea62441ff1a0049a09a1b00000083387b1af5d87a80d8799f5820c1c84e497ff13957a79017ca6cdf9c5436636da62570e477b7bb0fac18c78910ffffff",
            "311845ed9a47766814f25745cfba5ed379de7b9edcb27733742b7d21a9a9eb41, d87a9fd8799f5820712efdce64f1207f1789ab127f3270c195ee471bc6d79d696a9dc9763b38c66f1b0000000ba43b740100d8799fc25f58405147c323e63b7d1616702255b287b54ff2bcd79b7279a45de551f263ce6dd3162ca06d110cf80820679b063269b0126336c86625b59e934134f2edd10ffdaec2445f6eecf4ffc25f58400431c82216d66434ee5ae416a7cc53251c9d7e8066e0fc28b3dfadb06a3eb0ff9665dc38ee5b5a3729d97fb0da92f518b659a39c855eb4f2e045dd48b2f7ea4c4135ffff1a0049a0c31b0000008338c4bb8fd87a80d8799f5820c1c84e497ff13957a79017ca6cdf9c5436636da62570e477b7bb0fac18c78910ffffff",
    })
    void realBignumDatumsReencodeToTheirBytes(String datumHash, String datumHex) throws Exception {
        PlutusData datum = PlutusData.deserialize(HexUtil.decodeHexString(datumHex));

        assertThat(datum.serializeToHex()).isEqualTo(datumHex);
        assertThat(datum.getDatumHash()).isEqualTo(datumHash);
    }
}

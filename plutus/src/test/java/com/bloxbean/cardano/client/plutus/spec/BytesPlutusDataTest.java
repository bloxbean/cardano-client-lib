package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BytesPlutusDataTest {

    @Test
    void serializeDeserialize() throws CborSerializationException, CborException {
        BytesPlutusData bytesPlutusData = BytesPlutusData.builder()
                .value("Hello World!".getBytes(StandardCharsets.UTF_8))
                .build();

        DataItem di = bytesPlutusData.serialize();
        byte[] bytes = CborSerializationUtil.serialize(di);

        assertThat(HexUtil.encodeHexString(bytes)).isEqualTo("4c48656c6c6f20576f726c6421");
    }

    @Test
    void unicodeDeserializeSerialize() throws CborDeserializationException, CborSerializationException {
        String testString = "Hello World!";

        UnicodeString unicodeString = new UnicodeString(testString);
        PlutusData deserialize = PlutusData.deserialize(unicodeString);

        BytesPlutusData bytesPlutusData = BytesPlutusData.of(testString);

        assertTrue(bytesPlutusData.equals(deserialize));
    }

    @Test
    void byteStringChunkLessThan64bytes() {
        var longBs = "1234567890";
        var expected = "4a31323334353637383930";

        var serHex = BytesPlutusData.of(longBs).serializeToHex();

        assertThat(serHex).isEqualTo(expected);
    }

    @Test
    void byteStringChunkLessThan64bytes_roundtrip() throws CborDeserializationException {
        var longBs = "1234567890";

        var serHex = BytesPlutusData.of(longBs).serializeToHex();
        var deBytesPlutusData = (BytesPlutusData)PlutusData.deserialize(HexUtil.decodeHexString(serHex));
        var deValue = deBytesPlutusData.getValue();
        var deStr = new String(deValue);

        assertThat(deStr).isEqualTo(longBs);
    }

    @Test
    void byteStringChunkMoreThan64bytes() {
        var longBs = "1234567890".repeat(7);
        var expected =
                "5f58403132333435363738393031323334353637383930313233343536373839303132333435363738393031323334353637383930313233343536373839303132333446353637383930ff";

        var serHex = BytesPlutusData.of(longBs).serializeToHex();

        assertThat(serHex).isEqualTo(expected);
    }


    @Test
    void byteStringChunkMoreThan64bytes_roundtrip() throws CborDeserializationException {
        var longBs = "1234567890".repeat(7);
        var expected =
                "5f58403132333435363738393031323334353637383930313233343536373839303132333435363738393031323334353637383930313233343536373839303132333446353637383930ff";

        var serHex = BytesPlutusData.of(longBs).serializeToHex();
        var deBytesPlutusData = (BytesPlutusData) PlutusData.deserialize(HexUtil.decodeHexString(serHex));
        var deValue = deBytesPlutusData.getValue();
        var deStr = new String(deValue);

        assertThat(serHex).isEqualTo(expected);
        assertThat(deStr).isEqualTo(longBs);
    }
}

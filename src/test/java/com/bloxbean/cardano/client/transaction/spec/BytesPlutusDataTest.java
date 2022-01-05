package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class BytesPlutusDataTest {

    @Test
    void serializeDeserialize() throws CborSerializationException, CborException, CborDeserializationException {
        BytesPlutusData bytesPlutusData = BytesPlutusData.builder()
                .value("Hello World!".getBytes(StandardCharsets.UTF_8))
                .build();

        DataItem di = bytesPlutusData.serialize();
        byte[] bytes = CborSerializationUtil.serialize(di);

        assertThat(HexUtil.encodeHexString(bytes)).isEqualTo("4c48656c6c6f20576f726c6421");
    }
}

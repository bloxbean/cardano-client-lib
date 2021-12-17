package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlutusScriptTest {

    @Test
    void serializeDeserializePlutusScript() throws CborSerializationException, CborDeserializationException {
        PlutusScript plutusScript = PlutusScript.builder()
                .type("PlutusScriptV1")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        byte[] bytes = plutusScript.serialize();
        System.out.println("Bytes: " + HexUtil.encodeHexString(bytes));

        ByteString bs = plutusScript.serializeAsDataItem();
        System.out.println("Bytes from Bytestring: " + HexUtil.encodeHexString(bs.getBytes()));

        PlutusScript deSerPlutusScript = PlutusScript.deserialize(bs);

        assertThat(deSerPlutusScript).isEqualTo(plutusScript);
    }
}

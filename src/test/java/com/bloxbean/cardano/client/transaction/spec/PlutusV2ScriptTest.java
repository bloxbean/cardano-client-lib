package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlutusV2ScriptTest {
    @Test
    void serializeDeserializePlutusV2Script() throws CborSerializationException, CborDeserializationException {
        PlutusV2Script plutusV2Script = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        byte[] bytes = plutusV2Script.serialize();
        System.out.println("Bytes: " + HexUtil.encodeHexString(bytes));

        ByteString bs = plutusV2Script.serializeAsDataItem();
        System.out.println("Bytes from Bytestring: " + HexUtil.encodeHexString(bs.getBytes()));

        PlutusV2Script deSerPlutusScript = PlutusV2Script.deserialize(bs);

        assertThat(deSerPlutusScript).isEqualTo(plutusV2Script);
    }

    @Test
    void printJson() throws JsonProcessingException {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .description("Test description")
                .cborHex("4e4d01000033222220051200120011")
                .build();

        String json = JsonUtil.getPrettyJson(plutusScript);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);

        assertThat(node.get("type").asText()).isEqualTo(plutusScript.getType());
        assertThat(node.get("description").asText()).isEqualTo(plutusScript.getDescription());
        assertThat(node.get("cborHex").asText()).isEqualTo(plutusScript.getCborHex());
    }
}

package com.bloxbean.cardano.client.cip.cip30;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.cip.cip8.COSEKey;
import com.bloxbean.cardano.client.cip.cip8.COSESign1;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.cip.cip30.CIP30Constant.CRV_Ed25519;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataSignatureTest {

    @Test
    void from() throws JsonProcessingException {
        String json = "{\n" +
                "  \"signature\" : \"845846a2012767616464726573735839003175d03902583e82037438cc86732f6e539f803f9a8b2d4ee164b9d0c77e617030631811f60a1f8a8be26d65a57ff71825b336cc6b76361da166686173686564f44b48656c6c6f20576f726c64584036c2151e1230364b0bf9e40cb65dbdca4c5decf4187e3c5511945d410ea59a1e733b5e68178c234979053ed75b0226ba826fb951c5a79fabf10bddcabda8dc05\",\n" +
                "  \"key\" : \"a4010103272006215820a5f73966e73d0bb9eadc75c5857eafd054a0202d716ac6dde00303ee9c0019e3\"\n" +
                "}";

        DataSignature ds = DataSignature.from(json);
        assertThat(ds.signature()).isEqualTo("845846a2012767616464726573735839003175d03902583e82037438cc86732f6e539f803f9a8b2d4ee164b9d0c77e617030631811f60a1f8a8be26d65a57ff71825b336cc6b76361da166686173686564f44b48656c6c6f20576f726c64584036c2151e1230364b0bf9e40cb65dbdca4c5decf4187e3c5511945d410ea59a1e733b5e68178c234979053ed75b0226ba826fb951c5a79fabf10bddcabda8dc05");
        assertThat(ds.key()).isEqualTo("a4010103272006215820a5f73966e73d0bb9eadc75c5857eafd054a0202d716ac6dde00303ee9c0019e3");
    }

    @Test
    void serializationDeserializationTest() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        DataSignature dataSignature = new DataSignature("845846a2012767616464726573735839003175d03902583e82037438cc86732f6e539f803f9a8b2d4ee164b9d0c77e617030631811f60a1f8a8be26d65a57ff71825b336cc6b76361da166686173686564f44b48656c6c6f20576f726c64584036c2151e1230364b0bf9e40cb65dbdca4c5decf4187e3c5511945d410ea59a1e733b5e68178c234979053ed75b0226ba826fb951c5a79fabf10bddcabda8dc05", "a4010103272006215820a5f73966e73d0bb9eadc75c5857eafd054a0202d716ac6dde00303ee9c0019e3");
        String jsonString = objectMapper.writeValueAsString(dataSignature);
        DataSignature dataSignature1 = objectMapper.readValue(jsonString, DataSignature.class);
        assertThat(dataSignature).isEqualTo(dataSignature1);
    }

    @Test
    void invalidDataSignature_throwsError() {
        assertThrows(CborRuntimeException.class, () -> {
            DataSignature dataSignature = new DataSignature("845846a2012767616464726573735839003175d03902583e82037438cc86732f6e539f803f9a8b2d4ee164b9d0c77e617030631811f60a1f8a8be26d65a57ff71825b336cc6b76361da166686173686564f44b48656c6c6f20576f726c64584036c2151e1230364b0bf9e40cb65dbdca4c5decf4187e3c5511945d410ea59a1e733b5e68178c234979053ed75b0226ba826fb951c5a79fab",
                    "103272006215820a5f73966e73d0bb9eadc75c5857eafd054a0202d716ac6dde00303ee9c0019e3");
        });
    }

    @Test
    void getCoseSignCoseKey_otherHeaders() {
        DataSignature dataSignature = new DataSignature("845846a2012767616464726573735839003175d03902583e82037438cc86732f6e539f803f9a8b2d4ee164b9d0c77e617030631811f60a1f8a8be26d65a57ff71825b336cc6b76361da166686173686564f44b48656c6c6f20576f726c64584036c2151e1230364b0bf9e40cb65dbdca4c5decf4187e3c5511945d410ea59a1e733b5e68178c234979053ed75b0226ba826fb951c5a79fabf10bddcabda8dc05", "a4010103272006215820a5f73966e73d0bb9eadc75c5857eafd054a0202d716ac6dde00303ee9c0019e3");
        COSESign1 coseSign1 = dataSignature.coseSign1();
        COSEKey coseKey = dataSignature.coseKey();

        assertThat(coseSign1).isNotNull();
        assertThat(coseKey).isNotNull();
        assertThat(new Address(dataSignature.address()).toBech32()).isEqualTo("addr_test1qqcht5peqfvraqsrwsuvepnn9ah988uq87dgkt2wu9jtn5x80eshqvrrrqglvzsl3297ymt954llwxp9kvmvc6mkxcwswtlz7k");
        assertThat(dataSignature.crv()).isEqualTo(CRV_Ed25519);
        assertThat(HexUtil.encodeHexString(dataSignature.x())).isEqualTo("a5f73966e73d0bb9eadc75c5857eafd054a0202d716ac6dde00303ee9c0019e3");
    }
}

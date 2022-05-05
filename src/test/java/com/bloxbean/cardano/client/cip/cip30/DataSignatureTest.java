package com.bloxbean.cardano.client.cip.cip30;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}

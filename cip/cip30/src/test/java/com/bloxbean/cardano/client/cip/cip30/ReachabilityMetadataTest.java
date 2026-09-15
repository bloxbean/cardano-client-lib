package com.bloxbean.cardano.client.cip.cip30;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GraalVM (Native Image / Web Image) reachability metadata shipped in this module's jar.
 * {@link DataSignature} is (de)serialized by Jackson through its private fields.
 */
class ReachabilityMetadataTest {

    private static final String METADATA =
            "/META-INF/native-image/com.bloxbean.cardano/cardano-client-cip30/reachability-metadata.json";

    @Test
    void registersDataSignatureFieldsForJackson() throws Exception {
        JsonNode metadata;
        try (InputStream in = getClass().getResourceAsStream(METADATA)) {
            assertThat(in).as(METADATA).isNotNull();
            metadata = new ObjectMapper().readTree(in);
        }

        assertThat(metadata.get("reflection"))
                .anySatisfy(entry -> {
                    assertThat(entry.get("type").asText()).isEqualTo(DataSignature.class.getName());
                    assertThat(entry.get("allDeclaredFields").asBoolean()).isTrue();
                    assertThat(entry.get("allDeclaredConstructors").asBoolean()).isTrue();
                });
    }
}

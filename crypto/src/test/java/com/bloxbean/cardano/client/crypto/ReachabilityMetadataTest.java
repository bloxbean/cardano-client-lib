package com.bloxbean.cardano.client.crypto;

import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GraalVM (Native Image / Web Image) reachability metadata shipped in this module's jar: the BIP39 word
 * list that {@link MnemonicCode} loads from the class path must be included as a resource.
 */
class ReachabilityMetadataTest {

    private static final String METADATA =
            "/META-INF/native-image/com.bloxbean.cardano/cardano-client-crypto/reachability-metadata.json";

    @Test
    void includesBip39WordListResource() throws Exception {
        JsonNode metadata;
        try (InputStream in = getClass().getResourceAsStream(METADATA)) {
            assertThat(in).as(METADATA).isNotNull();
            metadata = new ObjectMapper().readTree(in);
        }

        assertThat(metadata.get("resources")).anySatisfy(resource -> {
            String glob = resource.get("glob").asText();
            assertThat(getClass().getResource("/" + glob)).as(glob).isNotNull();
            assertThat(resource.path("condition").path("typeReached").asText()).isEqualTo(MnemonicCode.class.getName());
        });
    }
}

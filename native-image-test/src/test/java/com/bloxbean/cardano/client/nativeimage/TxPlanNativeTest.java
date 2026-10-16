package com.bloxbean.cardano.client.nativeimage;

import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.serialization.TransactionDocument;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TxPlan documents and their polymorphic intents ({@code @JsonSubTypes}) are bound by Jackson. The YAML is fixed
 * text written on the JVM, so reading it and writing it back must give the same text in a native image.
 */
class TxPlanNativeTest {

    @Test
    void txPlanYamlWithPaymentStakeRegistrationAndValidator() throws IOException {
        String yaml = resource("native/txplan.yaml");

        TxPlan plan = TxPlan.from(yaml);

        assertEquals(2, plan.getTxs().size());
        assertEquals(yaml.strip(), plan.toYaml().strip());
    }

    @Test
    void transactionDocumentWithExtensions() {
        String yaml = "version: '1.0'\n"
                + "extensions:\n"
                + "  pt:\n"
                + "    extension: programmable-token\n"
                + "    schema_version: '1'\n"
                + "    protocol: cip113\n"
                + "    contract_version: v2\n"
                + "    deployment:\n"
                + "      registry: abc\n"
                + "transaction: []\n";

        TransactionDocument doc = YamlSerializer.deserialize(yaml);

        ExtensionMetadata metadata = doc.getExtensions().get("pt");
        assertNotNull(metadata);
        assertEquals("programmable-token", metadata.getExtension());
        assertEquals("1", metadata.getSchemaVersion());
        assertEquals("cip113", metadata.getProtocol());
        assertEquals("v2", metadata.getContractVersion());
        assertEquals(Map.of("registry", "abc"), metadata.getDeployment());

        String written = YamlSerializer.serialize(doc);
        assertTrue(written.contains("schema_version: 1"), written);
        assertTrue(written.contains("contract_version: v2"), written);
    }

    static String resource(String path) throws IOException {
        try (InputStream in = TxPlanNativeTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCapability;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The compatibility manifest next to the vendored blueprint is build and review provenance: it
 * pins the reference-contract snapshot this adapter was written against. This test keeps it
 * honest — every value in it is recomputed from the blueprint or the code rather than trusted.
 */
class Cip113CompatibilityManifestTest {

    private static JsonNode manifest;
    private static JsonNode blueprint;
    private static byte[] blueprintBytes;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = resource("/blueprint/cip113/compatibility.yml")) {
            manifest = YamlSerializer.getYamlMapper().readTree(in);
        }
        try (InputStream in = resource("/blueprint/cip113/plutus.json")) {
            blueprintBytes = in.readAllBytes();
        }
        blueprint = YamlSerializer.getYamlMapper().readTree(blueprintBytes);
    }

    private static InputStream resource(String path) {
        InputStream in = Cip113CompatibilityManifestTest.class.getResourceAsStream(path);
        assertThat(in).as("missing resource %s", path).isNotNull();
        return in;
    }

    @Test
    void manifestNamesThisProtocolAndContractSnapshot() {
        assertThat(manifest.path("protocol").asText()).isEqualTo(Cip113Protocol.ID);
        assertThat(manifest.path("contract_version").asText()).isEqualTo(Cip113Protocol.CONTRACT_VERSION);
        assertThat(manifest.path("contract_version").asText())
                .isEqualTo(blueprint.path("preamble").path("version").asText());
        assertThat(manifest.path("source").path("repository").asText())
                .isEqualTo("https://github.com/cardano-foundation/cip113-programmable-tokens");
        assertThat(manifest.path("source").path("commit").asText()).matches("[0-9a-f]{40}");
    }

    @Test
    void blueprintHashIsRecomputedNotTrusted() throws Exception {
        String expected = HexUtil.encodeHexString(
                MessageDigest.getInstance("SHA-256").digest(blueprintBytes));
        assertThat(manifest.path("blueprint").path("sha256").asText())
                .as("plutus.json changed; re-vendoring is a review — update the manifest deliberately")
                .isEqualTo(expected);
        assertThat(manifest.path("blueprint").path("aiken").asText())
                .isEqualTo(blueprint.path("preamble").path("compiler").path("version").asText());
    }

    @Test
    void validatorHashesMatchTheBlueprint() {
        Map<String, String> declared = new LinkedHashMap<>();
        blueprint.path("validators").forEach(validator ->
                declared.put(validator.path("title").asText(), validator.path("hash").asText()));

        JsonNode validators = manifest.path("validators");
        assertThat(validators.isObject()).isTrue();
        assertThat(validators.size()).isGreaterThan(0);
        validators.fields().forEachRemaining(entry ->
                assertThat(entry.getValue().asText())
                        .as("manifest hash for %s", entry.getKey())
                        .isEqualTo(declared.get(entry.getKey())));
    }

    @Test
    void schemaVersionAndCapabilitiesMatchTheCode() {
        assertThat(manifest.path("schema_version").asText())
                .isEqualTo(ProgrammableTokenExtension.SCHEMA_VERSION);

        Set<String> implemented = StreamSupport.stream(
                        manifest.path("capabilities").path("implemented").spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.toSet());
        Set<String> advertised = new Cip113Protocol(mock(Cip113ProtocolService.class)).capabilities()
                .stream().map(ProgrammableTokenCapability::name).map(String::toLowerCase)
                .collect(Collectors.toSet());
        assertThat(implemented).isEqualTo(advertised);
        assertThat(manifest.path("capabilities").path("unsupported").size()).isGreaterThan(0);
    }
}

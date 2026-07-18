package com.bloxbean.cardano.client.txflow.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxFlowConformanceFixturesTest {
    private final TxFlowCodec codec = TxFlowCodec.standard();

    @Test
    void goldenPortableAndLegacyFixturesRemainReadable() throws Exception {
        FlowParseResult portable = codec.parse(resource("/fixtures/portable/minimal.yaml"),
                FlowParseOptions.serverDefaults());
        assertFalse(portable.hasErrors(), portable.getDiagnostics().toString());
        assertEquals("minimal", portable.requireFlow().getId());

        FlowParseResult legacy = codec.parse(resource("/fixtures/legacy/minimal.yaml"),
                FlowParseOptions.serverDefaults());
        assertFalse(legacy.hasErrors(), legacy.getDiagnostics().toString());
        assertTrue(legacy.getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("TXFLOW_LEGACY_FORMAT")));
    }

    @Test
    void publishedSchemasAndDiagnosticCatalogHaveStableIdentifiers() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schema = mapper.readTree(resource("/schema/txflow-v1alpha1.schema.json"));
        assertEquals("https://cardano-client.dev/schemas/txflow/v1alpha1",
                schema.path("$id").asText());
        assertEquals("https://cardano-client.dev/schemas/quicktx/transaction/v1alpha1#/$defs/transaction",
                schema.at("/$defs/step/properties/transaction/$ref").asText());
        JsonNode catalog = mapper.readTree(resource("/txflow-diagnostic-codes.json"));
        assertTrue(catalog.path("codes").has("TXFLOW_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void diagnosticCatalogContainsEveryStableCodeUsedByProductionSources() throws Exception {
        JsonNode catalog = new ObjectMapper().readTree(resource("/txflow-diagnostic-codes.json"));
        Set<String> documented = new java.util.HashSet<>();
        catalog.path("codes").fieldNames().forEachRemaining(documented::add);
        Pattern code = Pattern.compile("TXFLOW_[A-Z0-9_]+|EVENTS_COMPACTED");
        Set<String> used;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            used = files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            return code.matcher(Files.readString(path)).results()
                                    .map(result -> result.group()).toList().stream();
                        } catch (Exception failure) {
                            throw new RuntimeException(failure);
                        }
                    }).collect(Collectors.toSet());
        }
        assertTrue(documented.containsAll(used),
                () -> "Undocumented diagnostic codes: " + used.stream()
                        .filter(value -> !documented.contains(value)).sorted().toList());
    }

    @Test
    void publicValidatorUsesTheSameStableDiagnosticsAsTheCodec() throws Exception {
        String malformed = resource("/fixtures/portable/minimal.yaml")
                .replace("kind: TxFlow", "kind: SomethingElse");
        FlowValidationResult validation = TxFlowValidator.standard().validate(malformed);
        assertFalse(validation.isValid());
        assertEquals("TXFLOW_DOCUMENT_KIND", validation.diagnostics().get(0).code());
    }

    private String resource(String name) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

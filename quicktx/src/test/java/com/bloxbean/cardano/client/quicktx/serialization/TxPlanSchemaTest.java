package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the generated part of the TxPlan schema (ADR I1, I3, I4, I6). When an intent is added or changed, run
 * {@code ./gradlew :quicktx:generateTxPlanSchema} and commit the regenerated schema.
 */
class TxPlanSchemaTest {

    private static final Path SCHEMA_FILE = Path.of("src/main/resources" + TxPlanSchemaGenerator.SCHEMA_RESOURCE);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void schemaIsUpToDate() throws IOException {
        // Compare with normalized line endings, so a CRLF checkout does not fail the guard.
        String committed = Files.readString(SCHEMA_FILE).replace("\r\n", "\n");

        assertThat(TxPlanSchemaGenerator.generate(committed))
                .as("TxPlan schema is out of date: run ./gradlew :quicktx:generateTxPlanSchema")
                .isEqualTo(committed);
    }

    @Test
    void everyRegisteredIntentHasExactlyOneDefinitionWithItsTypeAsConst() throws IOException {
        JsonNode defs = schema().get("$defs");
        List<String> intentDefinitions = new ArrayList<>();
        defs.fieldNames().forEachRemaining(name -> {
            if (name.startsWith(TxPlanSchemaGenerator.INTENT_PREFIX)) intentDefinitions.add(name);
        });

        assertThat(intentDefinitions).containsExactlyInAnyOrderElementsOf(TxPlanSchemaGenerator.intentDefinitionNames());
        assertThat(intentDefinitions).hasSize(23);
        for (String name : intentDefinitions) {
            JsonNode definition = defs.get(name);
            assertThat(definition.at("/properties/type/const").asText())
                    .isEqualTo(name.substring(TxPlanSchemaGenerator.INTENT_PREFIX.length()));
            assertThat(definition.get("additionalProperties").asBoolean()).as(name).isFalse();
        }
    }

    @Test
    void generationIsIdempotentAndLeavesHandWrittenKeysAlone() throws IOException {
        ObjectNode schema = (ObjectNode) schema();
        ((ObjectNode) schema.at("/$defs/context")).put("x-marker", "hand-written");
        schema.put("x-root-marker", "hand-written");

        String once = TxPlanSchemaGenerator.generate(JSON.writeValueAsString(schema));
        JsonNode regenerated = JSON.readTree(once);

        assertThat(TxPlanSchemaGenerator.generate(once)).isEqualTo(once);
        assertThat(regenerated.at("/$defs/context/x-marker").asText()).isEqualTo("hand-written");
        assertThat(regenerated.get("x-root-marker").asText()).isEqualTo("hand-written");
        regenerated.get("$defs").fieldNames().forEachRemaining(name -> {
            if (!TxPlanSchemaGenerator.isGeneratedDefinition(name))
                assertThat(regenerated.at("/$defs/" + name)).as(name).isEqualTo(schema.at("/$defs/" + name));
        });
    }

    @Test
    void removedGeneratedDefinitionsAreRestored() throws IOException {
        ObjectNode schema = (ObjectNode) schema();
        ((ObjectNode) schema.get("$defs")).remove("intent.payment");
        ((ObjectNode) schema.get("$defs")).put("intent.removed_type", "stale");

        JsonNode regenerated = JSON.readTree(TxPlanSchemaGenerator.generate(JSON.writeValueAsString(schema)));

        assertThat(regenerated.at("/$defs/intent.payment").isObject()).isTrue();
        assertThat(regenerated.at("/$defs/intent.removed_type").isMissingNode()).isTrue();
    }

    @Test
    void txFlowReferenceTargetKeepsItsShape() throws IOException {
        JsonNode transaction = schema().at("/$defs/transaction");

        // Each may also be a whole-value placeholder, resolved before binding.
        assertThat(transaction.at("/properties/tx/anyOf/0/$ref").asText()).isEqualTo("#/$defs/tx");
        assertThat(transaction.at("/properties/context/anyOf/0/$ref").asText()).isEqualTo("#/$defs/context");
        assertThat(transaction.get("required").toString()).isEqualTo("[\"tx\"]");
    }

    private static JsonNode schema() throws IOException {
        return JSON.readTree(Files.readString(SCHEMA_FILE));
    }
}

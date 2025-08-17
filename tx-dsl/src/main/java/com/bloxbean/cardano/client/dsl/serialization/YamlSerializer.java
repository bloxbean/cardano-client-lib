package com.bloxbean.cardano.client.dsl.serialization;

import com.bloxbean.cardano.client.dsl.intention.FromIntention;
import com.bloxbean.cardano.client.dsl.intention.PaymentIntention;
import com.bloxbean.cardano.client.dsl.model.TransactionDocument;
import com.bloxbean.cardano.client.dsl.variable.VariableResolver;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.util.Map;

/**
 * Handles YAML serialization and deserialization of transaction documents.
 */
public class YamlSerializer {

    private static final ObjectMapper yamlMapper;

    static {
        YAMLFactory yamlFactory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);

        yamlMapper = new ObjectMapper(yamlFactory);

        // Configure to exclude null and empty fields
        yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Configure polymorphic type handling
        yamlMapper.registerSubtypes(
            FromIntention.class,
            PaymentIntention.class
        );
    }

    /**
     * Serialize a TransactionDocument to YAML string.
     *
     * @param document the document to serialize
     * @return YAML string representation
     */
    public static String serialize(TransactionDocument document) {
        try {
            return yamlMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to YAML", e);
        }
    }

    /**
     * Deserialize a YAML string to TransactionDocument.
     * This method first resolves any variable placeholders before Jackson deserialization.
     *
     * @param yaml the YAML string
     * @return deserialized TransactionDocument
     */
    public static TransactionDocument deserialize(String yaml) {
        return deserialize(yaml, null);
    }

    /**
     * Deserialize a YAML string to TransactionDocument with additional variable overrides.
     *
     * @param yaml the YAML string
     * @param additionalVariables additional variables to override YAML variables
     * @return deserialized TransactionDocument
     */
    public static TransactionDocument deserialize(String yaml, Map<String, Object> additionalVariables) {
        try {
            // Step 1: Extract variables from the YAML
            Map<String, Object> variables = VariableResolver.extractVariables(yaml);

            // Step 2: Apply additional variable overrides if provided
            if (additionalVariables != null) {
                variables.putAll(additionalVariables);
            }

            // Step 3: Resolve variable placeholders in the YAML content
            String resolvedYaml = VariableResolver.resolveVariables(yaml, variables);

            // Step 4: Deserialize the resolved YAML with Jackson
            return yamlMapper.readValue(resolvedYaml, TransactionDocument.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize from YAML", e);
        }
    }
}

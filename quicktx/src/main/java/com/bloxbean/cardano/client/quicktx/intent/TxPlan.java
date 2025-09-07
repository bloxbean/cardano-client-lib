package com.bloxbean.cardano.client.quicktx.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TxPlan represents a serializable transaction plan that captures all the intentions
 * and configuration attributes needed to reconstruct a Tx or ScriptTx instance.
 *
 * This is the core data model for the Plan + Replayer mechanism, enabling:
 * - Recording transaction building steps
 * - Serializing to YAML/JSON format
 * - Replaying to reconstruct equivalent transactions
 *
 * @since QuickTx Plan v1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true) // Forward compatibility
public class TxPlan {

    /**
     * Schema version for this plan format.
     * Follows semantic versioning (e.g., "1.0", "1.1", "2.0")
     */
    @JsonProperty("version")
    @Builder.Default
    private String version = "1.0";

    /**
     * Transaction type discriminator.
     * Values: "tx" for regular Tx, "script_tx" for ScriptTx
     * Optional when plan is part of a collection.
     */
    @JsonProperty("type")
    private String type;

    /**
     * Configuration attributes that don't affect transaction building order.
     * Includes: from, changeAddress, donation, metadata, validators, etc.
     */
    @JsonProperty("attributes")
    @Builder.Default
    private PlanAttributes attributes = new PlanAttributes();

    /**
     * Ordered list of high-level transaction intentions.
     * These are applied sequentially during replay to reconstruct the transaction.
     */
    @JsonProperty("intentions")
    @Builder.Default
    private List<TxIntention> intentions = new ArrayList<>();

    /**
     * Optional composition context for QuickTxBuilder integration.
     * Includes: feePayer, collateralPayer, utxoSelectionStrategy, signer hints.
     */
    @JsonProperty("context")
    private PlanContext context;

    /**
     * Variables that can be referenced in the plan using ${variable} syntax.
     * Enables parameterized plans for reuse.
     */
    @JsonProperty("variables")
    private Map<String, Object> variables;

    /**
     * Extension bag for forward-compatible fields.
     * Allows future versions to add fields without breaking v1.0 readers.
     */
    @JsonProperty("extensions")
    private Map<String, Object> extensions;

    // Convenience methods

    /**
     * Check if this is a regular Tx plan.
     */
    public boolean isTx() {
        return "tx".equals(type);
    }

    /**
     * Check if this is a ScriptTx plan.
     */
    public boolean isScriptTx() {
        return "script_tx".equals(type);
    }

    /**
     * Add an intention to this plan.
     */
    public TxPlan addIntention(TxIntention intention) {
        if (intentions == null) {
            intentions = new ArrayList<>();
        }
        intentions.add(intention);
        return this;
    }

    /**
     * Add a variable to this plan.
     */
    public TxPlan addVariable(String key, Object value) {
        if (variables == null) {
            variables = new java.util.HashMap<>();
        }
        variables.put(key, value);
        return this;
    }

    /**
     * Creates a deep copy of this plan for immutable snapshots.
     */
    public TxPlan deepCopy() {
        return TxPlan.builder()
            .version(this.version)
            .type(this.type)
            .attributes(this.attributes != null ? this.attributes.deepCopy() : null)
            .intentions(this.intentions != null ? new ArrayList<>(this.intentions) : null)
            .context(this.context != null ? this.context.deepCopy() : null)
            .variables(this.variables != null ? new java.util.HashMap<>(this.variables) : null)
            .extensions(this.extensions != null ? new java.util.HashMap<>(this.extensions) : null)
            .build();
    }

    /**
     * Validate this plan for basic consistency.
     * @throws IllegalStateException if the plan is invalid
     */
    public void validate() {
        if (version == null || version.isEmpty()) {
            throw new IllegalStateException("TxPlan version is required");
        }

        if (!version.matches("\\d+\\.\\d+")) {
            throw new IllegalStateException("Invalid version format: " + version);
        }

        if (type != null && !type.equals("tx") && !type.equals("script_tx")) {
            throw new IllegalStateException("Invalid transaction type: " + type);
        }

        if (attributes == null) {
            attributes = new PlanAttributes();
        }

        if (intentions == null) {
            intentions = new ArrayList<>();
        }
    }

    /**
     * Convert this plan to YAML format.
     * @return YAML string representation
     */
    public String toYaml() {
        try {
            return getYamlMapper().writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize TxPlan to YAML", e);
        }
    }

    /**
     * Convert this plan to pretty-printed YAML format.
     * @return pretty-printed YAML string
     */
    public String toYamlPretty() {
        try {
            return getYamlMapper().writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize TxPlan to YAML", e);
        }
    }

    /**
     * Deserialize YAML string to TxPlan.
     * @param yaml YAML string
     * @return TxPlan instance
     */
    public static TxPlan fromYaml(String yaml) {
        try {
            return getYamlMapper().readValue(yaml, TxPlan.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize TxPlan from YAML", e);
        }
    }

    private static com.fasterxml.jackson.databind.ObjectMapper getYamlMapper() {
        com.fasterxml.jackson.dataformat.yaml.YAMLFactory yamlFactory =
            new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()
                .disable(com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES);

        com.fasterxml.jackson.databind.ObjectMapper yamlMapper =
            new com.fasterxml.jackson.databind.ObjectMapper(yamlFactory);
        yamlMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        return yamlMapper;
    }
}

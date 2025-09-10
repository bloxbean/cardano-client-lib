package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.AuxDataProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Intention for attaching metadata to transactions.
 * Supports dual serialization format: human-readable JSON and lossless CBOR hex.
 *
 * During deserialization, CBOR hex takes priority for perfect round-trip fidelity,
 * with JSON as fallback for human-readable editing.
 *
 * Maps to AbstractTx.attachMetadata(Metadata metadata).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetadataIntention implements TxIntention {

    // Runtime field - original object preserved

    /**
     * Original metadata object for runtime use.
     */
    @JsonIgnore
    private Metadata metadata;

    // Serialization fields - computed from runtime object or set during deserialization

    /**
     * Metadata as JSON string for human-readable serialization.
     * Used for YAML editing and debugging.
     */
    @JsonProperty("metadata_json")
    private String metadataJson;

    /**
     * Metadata as CBOR hex string for lossless serialization.
     * Takes priority during deserialization for perfect round-trip fidelity.
     */
    @JsonProperty("metadata_cbor_hex")
    private String metadataCborHex;

    /**
     * Get metadata JSON for serialization.
     * Computed from original metadata when serializing.
     */
    @JsonProperty("metadata_json")
    public String getMetadataJson() {
        if (metadata != null) {
            try {
                return MetadataBuilder.toJson(metadata);
            } catch (Exception e) {
                // Log error and return stored JSON
            }
        }
        return metadataJson;
    }

    /**
     * Get metadata CBOR hex for serialization.
     * Computed from original metadata when serializing.
     */
    @JsonProperty("metadata_cbor_hex")
    public String getMetadataCborHex() {
        if (metadata != null) {
            try {
                return HexUtil.encodeHexString(metadata.serialize());
            } catch (Exception e) {
                // Log error and return stored hex
            }
        }
        return metadataCborHex;
    }

    @Override
    public String getType() {
        return "metadata";
    }


    @Override
    public void validate() {
        // Check that at least one format is available
        if (metadata == null &&
            (metadataJson == null || metadataJson.isEmpty()) &&
            (metadataCborHex == null || metadataCborHex.isEmpty())) {
            throw new IllegalStateException("Metadata intention requires at least one of: runtime metadata, JSON, or CBOR hex");
        }

        // Validate CBOR hex format if provided
        if (metadataCborHex != null && !metadataCborHex.isEmpty() && !metadataCborHex.startsWith("${")) {
            try {
                HexUtil.decodeHexString(metadataCborHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid metadata CBOR hex format: " + metadataCborHex);
            }
        }

        // Basic JSON validation if provided (could be variable reference)
        if (metadataJson != null && !metadataJson.isEmpty() && !metadataJson.startsWith("${")) {
            if (!metadataJson.trim().startsWith("{") && !metadataJson.trim().startsWith("[")) {
                throw new IllegalStateException("Metadata JSON should start with { or [: " + metadataJson);
            }
        }
    }

    @Override
    public TxIntention resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedMetadataJson = VariableResolver.resolve(metadataJson, variables);
        String resolvedMetadataCborHex = VariableResolver.resolve(metadataCborHex, variables);
        
        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedMetadataJson, metadataJson) || 
            !java.util.Objects.equals(resolvedMetadataCborHex, metadataCborHex)) {
            return this.toBuilder()
                .metadataJson(resolvedMetadataJson)
                .metadataCborHex(resolvedMetadataCborHex)
                .build();
        }
        
        return this;
    }

    // Factory methods for different use cases

    /**
     * Create MetadataIntention from runtime Metadata object.
     * This is used when attaching metadata programmatically.
     */
    public static MetadataIntention from(Metadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }
        return MetadataIntention.builder()
            .metadata(metadata)
            .build();
    }

    /**
     * Create MetadataIntention from JSON string.
     * Used during deserialization from YAML/JSON when only JSON is available.
     */
    public static MetadataIntention fromJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) {
            throw new IllegalArgumentException("Metadata JSON cannot be null or empty");
        }
        return MetadataIntention.builder()
            .metadataJson(metadataJson)
            .build();
    }

    /**
     * Create MetadataIntention from CBOR hex string.
     * Used during deserialization when lossless round-trip is required.
     */
    public static MetadataIntention fromCborHex(String metadataCborHex) {
        if (metadataCborHex == null || metadataCborHex.isEmpty()) {
            throw new IllegalArgumentException("Metadata CBOR hex cannot be null or empty");
        }
        return MetadataIntention.builder()
            .metadataCborHex(metadataCborHex)
            .build();
    }

    /**
     * Create MetadataIntention with both JSON and CBOR hex formats.
     * Used for complete serialization with both human-readable and lossless formats.
     */
    public static MetadataIntention fromBoth(String metadataJson, String metadataCborHex) {
        return MetadataIntention.builder()
            .metadataJson(metadataJson)
            .metadataCborHex(metadataCborHex)
            .build();
    }

    // Utility methods

    /**
     * Check if CBOR hex format is available.
     */
    @JsonIgnore
    public boolean hasCborHex() {
        return metadataCborHex != null && !metadataCborHex.isEmpty();
    }

    /**
     * Check if JSON format is available.
     */
    @JsonIgnore
    public boolean hasJson() {
        return metadataJson != null && !metadataJson.isEmpty();
    }

    /**
     * Check if this intention has runtime objects available.
     */
    @JsonIgnore
    public boolean hasRuntimeObjects() {
        return metadata != null;
    }

    // Self-processing methods for functional TxBuilder architecture

    @Override
    public TxOutputBuilder outputBuilder(IntentContext context) {
        // Phase 1: No outputs created for metadata - return null
        return null;
    }

    @Override
    public TxBuilder preApply(IntentContext context) {
        return (ctx, txn) -> {
            // Pre-processing: validate metadata availability and format
            if (metadata == null &&
                (metadataJson == null || metadataJson.isEmpty()) &&
                (metadataCborHex == null || metadataCborHex.isEmpty())) {
                throw new TxBuildException("Metadata intention requires at least one format: runtime metadata, JSON, or CBOR hex");
            }

            // Perform standard validation
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext context) {
        try {
            Metadata resolvedMetadata = null;

            // Use runtime object if available
            if (hasRuntimeObjects()) {
                resolvedMetadata = metadata;
            }
            // Priority 1: Deserialize from CBOR hex (lossless round-trip, values already resolved during YAML parsing)
            else if (hasCborHex()) {
                byte[] cborBytes = HexUtil.decodeHexString(metadataCborHex);
                resolvedMetadata = MetadataBuilder.deserialize(cborBytes);
            }
            // Priority 2: Deserialize from JSON (fallback, values already resolved during YAML parsing)
            else if (hasJson()) {
                resolvedMetadata = MetadataBuilder.metadataFromJson(metadataJson);
            }

            if (resolvedMetadata != null) {
                // Use AuxDataProviders to attach metadata - return the TxBuilder
                return AuxDataProviders.metadataProvider(resolvedMetadata);
            } else {
                // No metadata to attach - return no-op
                return (ctx, txn) -> { /* no-op */ };
            }

        } catch (Exception e) {
            throw new TxBuildException("Failed to apply MetadataIntention: " + e.getMessage(), e);
        }
    }
}

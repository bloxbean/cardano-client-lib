package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.AuxDataProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.MetadataDeserializer;
import com.bloxbean.cardano.client.quicktx.serialization.MetadataSerializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Intention for attaching metadata to transactions.
 * Supports automatic serialization/deserialization between Metadata objects and
 * YAML/CBOR hex formats. The format is automatically detected during deserialization.
 *
 * Serialization formats:
 * - YAML: Human-readable format (default)
 * - CBOR hex: Lossless binary format
 * - JSON: Legacy format (supported for deserialization only)
 *
 * Maps to AbstractTx.attachMetadata(Metadata metadata).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetadataIntent implements TxIntent {

    /**
     * The metadata to attach to the transaction.
     * Automatically serialized/deserialized using custom Jackson handlers.
     */
    @JsonProperty("metadata")
    @JsonSerialize(using = MetadataSerializer.class)
    @JsonDeserialize(using = MetadataDeserializer.class)
    private Metadata metadata;

    @Override
    public String getType() {
        return "metadata";
    }

    @Override
    public void validate() {
        if (metadata == null) {
            throw new IllegalStateException("Metadata intention requires metadata to be set");
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        return this;
    }

    /**
     * Create MetadataIntent from runtime Metadata object.
     * This is used when attaching metadata programmatically.
     */
    public static MetadataIntent from(Metadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }
        return MetadataIntent.builder()
            .metadata(metadata)
            .build();
    }

    // Self-processing methods for functional TxBuilder architecture

    @Override
    public TxOutputBuilder outputBuilder(IntentContext context) {
        // Phase 1: No outputs created for metadata
        return null;
    }

    @Override
    public TxBuilder preApply(IntentContext context) {
        return (ctx, txn) -> {
            // Pre-processing: validate metadata availability
            if (metadata == null) {
                throw new TxBuildException("Metadata intention requires metadata to be set");
            }

            // Perform standard validation
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext context) {
        try {
            if (metadata != null) {
                // Use AuxDataProviders to attach metadata
                return AuxDataProviders.metadataProvider(metadata);
            } else {
                // No metadata to attach - return no-op
                return (ctx, txn) -> { /* no-op */ };
            }
        } catch (Exception e) {
            throw new TxBuildException("Failed to apply MetadataIntent: " + e.getMessage(), e);
        }
    }
}

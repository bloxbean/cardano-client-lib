package com.bloxbean.cardano.client.txflow.codec;

import java.util.Objects;

/**
 * Selects the syntax and schema contract used when serializing a flow.
 *
 * @param format output syntax
 * @param schemaVersion serialized contract to emit
 */
public record FlowWriteOptions(FlowFormat format, FlowSchemaVersion schemaVersion) {
    /**
     * Creates writer options after validating both output dimensions.
     *
     * @param format output syntax
     * @param schemaVersion serialized contract to emit
     * @throws NullPointerException if either component is {@code null}
     */
    public FlowWriteOptions {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
    }

    /**
     * Creates writer options for a format and schema version.
     *
     * @param format output syntax
     * @param schemaVersion serialized contract
     * @return validated writer options
     * @throws NullPointerException if either argument is {@code null}
     */
    public static FlowWriteOptions of(FlowFormat format, FlowSchemaVersion schemaVersion) {
        return new FlowWriteOptions(format, schemaVersion);
    }
}

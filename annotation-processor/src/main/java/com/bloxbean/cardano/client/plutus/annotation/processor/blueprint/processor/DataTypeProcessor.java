package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.squareup.javapoet.FieldSpec;

/**
 * Strategy interface for processing different Blueprint data types.
 * Each implementation handles a specific data type (bytes, integer, list, etc.)
 *
 * This replaces the large switch statement in the original DataTypeProcessUtil
 * with a more maintainable and extensible strategy pattern.
 */
public interface DataTypeProcessor {

    /**
     * Determines if this processor can handle the given data type
     *
     * @param dataType the Blueprint data type to check
     * @return true if this processor can handle the data type
     */
    boolean canProcess(BlueprintDatatype dataType);

    /**
     * Processes the data type and generates the appropriate FieldSpec
     *
     * @param context the processing context containing all necessary information
     * @return the generated FieldSpec for the data type
     */
    FieldSpec processDataType(ProcessingContext context);

    /**
     * Returns the priority of this processor for ordering when multiple
     * processors can handle the same data type
     *
     * @return priority value (lower values = higher priority)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Returns a human-readable name for this processor (for debugging/logging)
     *
     * @return processor name
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}

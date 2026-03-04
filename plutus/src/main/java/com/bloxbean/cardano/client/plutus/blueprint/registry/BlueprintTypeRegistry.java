package com.bloxbean.cardano.client.plutus.blueprint.registry;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service-provider interface that maps blueprint schema signatures to pre-defined Java types.
 */
public interface BlueprintTypeRegistry {

    Optional<RegisteredType> lookup(SchemaSignature signature, BlueprintSchema schema, LookupContext context);

    /**
     * Returns descriptors for annotations that this registry wants the annotation processor
     * to read from the {@code @Blueprint}-annotated type element.
     *
     * <p>Each descriptor tells the processor which annotation to scan for, which element to
     * read, and what hint key/default value to use. The processor collects hints from all
     * loaded registries generically — no framework-specific knowledge required.</p>
     *
     * @return annotation hint descriptors; empty list by default (backward-compatible)
     */
    default List<AnnotationHintDescriptor> annotationHints() {
        return Collections.emptyList();
    }

}

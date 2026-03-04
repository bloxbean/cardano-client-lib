package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.registry.LookupContext;
import com.squareup.javapoet.ClassName;

import javax.lang.model.element.TypeElement;
import java.util.Optional;

public interface SharedTypeLookup {

    Optional<ClassName> lookup(String namespace, BlueprintSchema schema);

    /**
     * Looks up a shared type with additional context (e.g., stdlib version hints).
     * Default implementation delegates to {@link #lookup(String, BlueprintSchema)}.
     */
    default Optional<ClassName> lookup(String namespace, BlueprintSchema schema, LookupContext context) {
        return lookup(namespace, schema);
    }

    /**
     * Collects hint values from all loaded registries by scanning the annotation mirrors
     * of the given type element for annotations declared via
     * {@link com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistry#annotationHints()}.
     *
     * @param typeElement the {@code @Blueprint}-annotated type element
     * @return a {@link LookupContext} populated with all resolved hints
     */
    default LookupContext resolveHints(TypeElement typeElement) {
        return LookupContext.EMPTY;
    }

    static SharedTypeLookup disabled() {
        return new SharedTypeLookup() {
            @Override
            public Optional<ClassName> lookup(String namespace, BlueprintSchema schema) {
                return Optional.empty();
            }

            @Override
            public Optional<ClassName> lookup(String namespace, BlueprintSchema schema, LookupContext context) {
                return Optional.empty();
            }
        };
    }

}

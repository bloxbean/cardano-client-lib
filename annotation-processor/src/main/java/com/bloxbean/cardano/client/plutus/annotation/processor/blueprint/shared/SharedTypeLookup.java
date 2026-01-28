package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.ClassName;

import java.util.Optional;

public interface SharedTypeLookup {

    Optional<ClassName> lookup(String namespace, BlueprintSchema schema);

    static SharedTypeLookup disabled() {
        return (namespace, schema) -> Optional.empty();
    }
}


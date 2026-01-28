package com.bloxbean.cardano.client.plutus.blueprint.registry;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

import java.util.Optional;

/**
 * Service-provider interface that maps blueprint schema signatures to pre-defined Java types.
 */
public interface BlueprintTypeRegistry {

    Optional<RegisteredType> lookup(SchemaSignature signature, BlueprintSchema schema, LookupContext context);
}

package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.squareup.javapoet.FieldSpec;

import java.util.List;

/**
 * Strategy responsible for generating {@link com.squareup.javapoet.FieldSpec}s for a specific
 * {@link com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype}.
 */
public interface DataTypeProcessor {
    BlueprintDatatype supportedType();

    List<FieldSpec> process(DataTypeProcessingContext context);
}

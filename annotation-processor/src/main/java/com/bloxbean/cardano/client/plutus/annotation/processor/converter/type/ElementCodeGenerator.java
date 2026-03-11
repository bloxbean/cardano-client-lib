package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.squareup.javapoet.CodeBlock;

/**
 * Functional interface for generating code that serializes or deserializes
 * a single element within a tuple, list, map, or optional container.
 * <p>
 * This allows {@link TupleCodeGenerator} to remain agnostic about how each
 * element type is handled — the caller provides the dispatch logic as a lambda.
 */
@FunctionalInterface
public interface ElementCodeGenerator {
    CodeBlock generate(FieldType elementType, String baseName, String outputVarName, String expression);
}

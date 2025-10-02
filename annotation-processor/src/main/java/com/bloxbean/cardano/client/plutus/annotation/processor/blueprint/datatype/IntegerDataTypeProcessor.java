package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.squareup.javapoet.FieldSpec;

import javax.lang.model.element.Modifier;
import java.math.BigInteger;
import java.util.List;

/**
 * Generates field definitions for blueprint schemas of datatype {@code integer}.
 */
public class IntegerDataTypeProcessor extends AbstractDataTypeProcessor {
    public IntegerDataTypeProcessor(com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.NameStrategy nameStrategy,
                             SchemaTypeResolver typeResolver) {
        super(nameStrategy, typeResolver);
    }

    @Override
    public BlueprintDatatype supportedType() {
        return BlueprintDatatype.integer;
    }

    @Override
    public List<FieldSpec> process(DataTypeProcessingContext context) {
        FieldSpec fieldSpec = FieldSpec.builder(BigInteger.class, resolveFieldName(context))
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(context.getJavaDoc())
                .build();
        return List.of(fieldSpec);
    }
}

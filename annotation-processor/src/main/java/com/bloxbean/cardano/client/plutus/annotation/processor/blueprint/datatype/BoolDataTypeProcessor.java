package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.squareup.javapoet.FieldSpec;

import javax.lang.model.element.Modifier;
import java.util.List;

/**
 * Generates field definitions for blueprint schemas of datatype {@code bool}.
 */
public class BoolDataTypeProcessor extends AbstractDataTypeProcessor {
    public BoolDataTypeProcessor(com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.NamingStrategy nameStrategy,
                          SchemaTypeResolver typeResolver) {
        super(nameStrategy, typeResolver);
    }

    @Override
    public BlueprintDatatype supportedType() {
        return BlueprintDatatype.bool;
    }

    @Override
    public List<FieldSpec> process(DataTypeProcessingContext context) {
        FieldSpec fieldSpec = FieldSpec.builder(boolean.class, resolveFieldName(context))
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(context.getJavaDoc())
                .build();
        return List.of(fieldSpec);
    }
}

package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.NameStrategy;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.squareup.javapoet.FieldSpec;

import javax.lang.model.element.Modifier;
import java.util.List;

/**
 * Generates field definitions for schemas without an explicit datatype (raw {@link PlutusData}).
 */
public class PlutusDataTypeProcessor extends AbstractDataTypeProcessor {
    public PlutusDataTypeProcessor(NameStrategy nameStrategy, SchemaTypeResolver typeResolver) {
        super(nameStrategy, typeResolver);
    }

    @Override
    public com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype supportedType() {
        return null;
    }

    @Override
    public List<FieldSpec> process(DataTypeProcessingContext context) {
        BlueprintSchema schema = context.getSchema();
        String fieldName = resolveFieldName(context);
        FieldSpec fieldSpec = FieldSpec.builder(PlutusData.class, fieldName)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(context.getJavaDoc())
                .build();
        return List.of(fieldSpec);
    }
}

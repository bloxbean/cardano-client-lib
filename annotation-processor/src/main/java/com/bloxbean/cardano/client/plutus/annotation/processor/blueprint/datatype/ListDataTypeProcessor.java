package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;

import javax.lang.model.element.Modifier;
import java.util.List;

/**
 * Generates field definitions for blueprint schemas of datatype {@code list}.
 */
public class ListDataTypeProcessor extends AbstractDataTypeProcessor {
    public ListDataTypeProcessor(com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.NameStrategy nameStrategy,
                          SchemaTypeResolver typeResolver) {
        super(nameStrategy, typeResolver);
    }

    @Override
    public BlueprintDatatype supportedType() {
        return BlueprintDatatype.list;
    }

    @Override
    public List<FieldSpec> process(DataTypeProcessingContext context) {
        TypeName typeName = typeResolver.resolveListType(context.getNamespace(), context.getSchema());
        FieldSpec fieldSpec = FieldSpec.builder(typeName, resolveFieldName(context))
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc(context.getJavaDoc())
                .build();
        return List.of(fieldSpec);
    }
}

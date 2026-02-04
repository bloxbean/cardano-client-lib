package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.FieldSpecProcessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.NameStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.PackageResolver;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.util.Tuple;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates field definitions for blueprint schemas of datatype {@code constructor},
 * delegating to nested strategies or creating supplementary enum classes when needed.
 */
public class ConstructorDataTypeProcessor extends AbstractDataTypeProcessor {

    private final FieldSpecProcessor fieldSpecProcessor;
    private final PackageResolver packageResolver;
    private final Blueprint annotation;
    public ConstructorDataTypeProcessor(NameStrategy nameStrategy,
                                 SchemaTypeResolver typeResolver,
                                 FieldSpecProcessor fieldSpecProcessor,
                                 PackageResolver packageResolver,
                                 Blueprint annotation) {
        super(nameStrategy, typeResolver);
        this.fieldSpecProcessor = fieldSpecProcessor;
        this.packageResolver = packageResolver;
        this.annotation = annotation;
    }

    @Override
    public BlueprintDatatype supportedType() {
        return BlueprintDatatype.constructor;
    }

    @Override
    public List<FieldSpec> process(DataTypeProcessingContext context) {
        List<FieldSpec> specs = new ArrayList<>();
        BlueprintSchema schema = context.getSchema();

        if (schema.getFields() != null) {
            for (BlueprintSchema field : schema.getFields()) {
                if (field.getDataType() != null) {
                    specs.addAll(fieldSpecProcessor.createFieldSpecForDataTypes(
                            context.getNamespace(),
                            context.getJavaDoc(),
                            field,
                            context.getClassName(),
                            field.getTitle()
                    ));
                } else {
                    Tuple<FieldSpec, ClassName> tuple = fieldSpecProcessor.createDatumFieldSpec(
                            context.getNamespace(),
                            "",
                            field,
                            field.getTitle()
                    );
                    specs.add(tuple._1);
                }
            }
        }

        if (schema.getFields() == null || schema.getFields().isEmpty()) {
            specs.add(createEnumField(context));
        }

        return specs;
    }

    private FieldSpec createEnumField(DataTypeProcessingContext context) {
        BlueprintSchema schema = context.getSchema();
        AnnotationSpec constrAnnotation = AnnotationSpec.builder(Constr.class)
                .addMember("alternative", "$L", schema.getIndex())
                .build();

        String fieldName = nameStrategy.firstUpperCase(schema.getTitle());

        TypeSpec enumConstr = TypeSpec.classBuilder(fieldName)
                .addAnnotation(constrAnnotation)
                .addModifiers(Modifier.PUBLIC)
                .build();

        String pkg = packageResolver.getModelPackage(annotation, context.getNamespace());
        ClassName classIdentifier = ClassName.get(pkg, enumConstr.name);

        return FieldSpec.builder(classIdentifier, fieldName)
                .addModifiers(Modifier.PRIVATE)
                .addJavadoc("Index: " + schema.getIndex())
                .build();
    }
}

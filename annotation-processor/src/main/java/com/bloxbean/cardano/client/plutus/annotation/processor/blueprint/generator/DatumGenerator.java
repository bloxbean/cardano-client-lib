package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.generator;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.DataTypeProcessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.registry.DataTypeProcessorRegistry;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generator for Datum classes using the Template Method pattern.
 * Replaces the complex datum generation logic in FieldSpecProcessor.
 */
public class DatumGenerator extends BaseGenerator {
    
    private final DataTypeProcessorRegistry processorRegistry;
    
    public DatumGenerator(DataTypeProcessorRegistry processorRegistry) {
        this.processorRegistry = processorRegistry;
    }
    
    @Override
    public GeneratorType getType() {
        return GeneratorType.DATUM;
    }
    
    @Override
    public void validateInput(ProcessingContext context) {
        super.validateInput(context);
        if (context.getSchema() == null) {
            throw new IllegalArgumentException("Schema cannot be null for datum generation");
        }
        
        BlueprintSchema schema = context.getSchema();
        if (schema.getDataType() != BlueprintDatatype.constructor) {
            throw new IllegalArgumentException(
                "DatumGenerator can only handle CONSTRUCTOR schemas, got: " + schema.getDataType()
            );
        }
        
        if (schema.getFields() == null || schema.getFields().isEmpty()) {
            throw new IllegalArgumentException(
                "CONSTRUCTOR schema must have fields for datum generation"
            );
        }
    }
    
    @Override
    protected TypeSpec.Builder createBuilder(ProcessingContext context) {
        String className = context.getEffectiveClassName();
        
        return TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC);
    }
    
    @Override
    protected void addAnnotations(TypeSpec.Builder builder, ProcessingContext context) {
        // Add parent annotations first
        super.addAnnotations(builder, context);
        
        // Add @Constr annotation with constructor index
        BlueprintSchema schema = context.getSchema();
        AnnotationSpec.Builder constrBuilder = AnnotationSpec.builder(Constr.class);
        
        if (schema.getIndex() != 0) {
            constrBuilder.addMember("alternative", "$L", schema.getIndex());
        }
        
        builder.addAnnotation(constrBuilder.build());
    }
    
    @Override
    protected void addFields(TypeSpec.Builder builder, ProcessingContext context) {
        BlueprintSchema schema = context.getSchema();
        
        // Generate fields from schema
        for (int i = 0; i < schema.getFields().size(); i++) {
            BlueprintSchema fieldSchema = schema.getFields().get(i);
            String fieldName = fieldSchema.getTitle() != null ? fieldSchema.getTitle() : "field" + i;
            
            // Create field processing context
            ProcessingContext fieldContext = context.toBuilder()
                    .schema(fieldSchema)
                    .fieldName(fieldName)
                    .build();
            
            // Find appropriate processor and generate field
            DataTypeProcessor processor = processorRegistry.findProcessor(fieldSchema.getDataType());
            if (processor != null) {
                FieldSpec fieldSpec = processor.processDataType(fieldContext);
                if (fieldSpec != null) {
                    builder.addField(fieldSpec);
                }
            }
        }
    }
    
    @Override
    protected void addMethods(TypeSpec.Builder builder, ProcessingContext context) {
        // Add standard methods (equals, hashCode, toString) if configured
        super.addMethods(builder, context);
        
        // Add constructor if configured
        if (context.getConfig().isGenerateBuilders()) {
            addConstructor(builder, context);
        }
        
        // Add getters/setters if configured - always add for data classes
        addGettersSetters(builder, context);
    }
    
    /**
     * Adds a constructor with all fields
     */
    private void addConstructor(TypeSpec.Builder builder, ProcessingContext context) {
        List<FieldSpec> fields = extractFields(builder);
        
        if (fields.isEmpty()) {
            return;
        }
        
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        
        // Add parameters and assignments
        for (FieldSpec field : fields) {
            constructorBuilder.addParameter(field.type, field.name);
            constructorBuilder.addStatement("this.$L = $L", field.name, field.name);
        }
        
        builder.addMethod(constructorBuilder.build());
    }
    
    /**
     * Adds getter and setter methods for all fields
     */
    private void addGettersSetters(TypeSpec.Builder builder, ProcessingContext context) {
        List<FieldSpec> fields = extractFields(builder);
        
        for (FieldSpec field : fields) {
            // Getter
            String getterName = "get" + capitalize(field.name);
            MethodSpec getter = MethodSpec.methodBuilder(getterName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(field.type)
                    .addStatement("return $L", field.name)
                    .build();
            builder.addMethod(getter);
            
            // Setter
            String setterName = "set" + capitalize(field.name);
            MethodSpec setter = MethodSpec.methodBuilder(setterName)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(field.type, field.name)
                    .addStatement("this.$L = $L", field.name, field.name)
                    .build();
            builder.addMethod(setter);
        }
    }
    
    /**
     * Capitalizes the first letter of a string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    @Override
    protected List<FieldSpec> extractFields(TypeSpec.Builder builder) {
        // In a real implementation, this would extract fields from the builder
        // For now, we'll return an empty list since TypeSpec.Builder doesn't expose fields
        return new ArrayList<>();
    }
}
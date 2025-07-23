package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.generator;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Generator for Enum classes using the Template Method pattern.
 * Replaces the enum generation logic in FieldSpecProcessor.
 */
public class EnumGenerator extends BaseGenerator {
    
    @Override
    public GeneratorType getType() {
        return GeneratorType.ENUM;
    }
    
    @Override
    public void validateInput(ProcessingContext context) {
        super.validateInput(context);
        if (context.getSchema() == null) {
            throw new IllegalArgumentException("Schema cannot be null for enum generation");
        }
        
        BlueprintSchema schema = context.getSchema();
        if (schema.getAnyOf() == null || schema.getAnyOf().isEmpty()) {
            throw new IllegalArgumentException(
                "Enum schema must have anyOf definitions"
            );
        }
        
        // Validate that all anyOf items are valid for enum generation
        for (BlueprintSchema anyOf : schema.getAnyOf()) {
            if (anyOf.getDataType() != BlueprintDatatype.constructor) {
                throw new IllegalArgumentException(
                    "All anyOf items must be CONSTRUCTOR type for enum generation"
                );
            }
        }
    }
    
    @Override
    protected TypeSpec.Builder createBuilder(ProcessingContext context) {
        String className = context.getEffectiveClassName();
        
        return TypeSpec.enumBuilder(className)
                .addModifiers(Modifier.PUBLIC);
    }
    
    @Override
    protected void addAnnotations(TypeSpec.Builder builder, ProcessingContext context) {
        // Add parent annotations first
        super.addAnnotations(builder, context);
        
        // Add @Constr annotation for enums
        builder.addAnnotation(AnnotationSpec.builder(Constr.class).build());
    }
    
    @Override
    protected void addFields(TypeSpec.Builder builder, ProcessingContext context) {
        BlueprintSchema schema = context.getSchema();
        
        // Add enum constants based on anyOf definitions
        for (BlueprintSchema anyOf : schema.getAnyOf()) {
            String enumConstantName = generateEnumConstantName(anyOf);
            
            TypeSpec.Builder enumConstant = TypeSpec.anonymousClassBuilder(
                    anyOf.getIndex() != 0 ? "$L" : "",
                    anyOf.getIndex() != 0 ? anyOf.getIndex() : ""
            );
            
            // Add JavaDoc for the enum constant
            if (anyOf.getDescription() != null) {
                enumConstant.addJavadoc(anyOf.getDescription());
            }
            
            builder.addEnumConstant(enumConstantName, enumConstant.build());
        }
        
        // Add index field for constructor alternative
        FieldSpec indexField = FieldSpec.builder(
                        int.class, 
                        "index", 
                        Modifier.PRIVATE, Modifier.FINAL
                )
                .build();
        
        builder.addField(indexField);
    }
    
    @Override
    protected void addMethods(TypeSpec.Builder builder, ProcessingContext context) {
        // Add constructor
        addEnumConstructor(builder, context);
        
        // Add getter for index
        addIndexGetter(builder, context);
        
        // Add factory methods
        addFactoryMethods(builder, context);
        
        // Don't call super for standard methods unless specifically configured
        if (context.getConfig().isGenerateToString()) {
            addCustomToString(builder, context);
        }
    }
    
    /**
     * Adds the enum constructor
     */
    private void addEnumConstructor(TypeSpec.Builder builder, ProcessingContext context) {
        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addParameter(int.class, "index")
                .addStatement("this.index = index")
                .build();
        
        builder.addMethod(constructor);
    }
    
    /**
     * Adds getter for the index field
     */
    private void addIndexGetter(TypeSpec.Builder builder, ProcessingContext context) {
        MethodSpec getIndex = MethodSpec.methodBuilder("getIndex")
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return index")
                .addJavadoc("Gets the constructor alternative index")
                .addJavadoc("@return constructor index")
                .build();
        
        builder.addMethod(getIndex);
    }
    
    /**
     * Adds factory methods for creating enum instances
     */
    private void addFactoryMethods(TypeSpec.Builder builder, ProcessingContext context) {
        String enumName = context.getEffectiveClassName();
        
        // Add fromIndex factory method
        MethodSpec.Builder fromIndexBuilder = MethodSpec.methodBuilder("fromIndex")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builder.build().name != null ? 
                    builder.build().getClass() : Object.class)
                .addParameter(int.class, "index")
                .addJavadoc("Creates an enum instance from constructor index")
                .addJavadoc("@param index constructor alternative index")
                .addJavadoc("@return enum instance")
                .addJavadoc("@throws IllegalArgumentException if index is invalid");
        
        // Add switch statement for each enum constant
        fromIndexBuilder.beginControlFlow("switch (index)");
        
        BlueprintSchema schema = context.getSchema();
        for (BlueprintSchema anyOf : schema.getAnyOf()) {
            if (anyOf.getIndex() != 0) {
                String constantName = generateEnumConstantName(anyOf);
                fromIndexBuilder.addStatement("case $L: return $L", anyOf.getIndex(), constantName);
            }
        }
        
        fromIndexBuilder.addStatement("default: throw new $T(\"Invalid index: \" + index)", 
                IllegalArgumentException.class);
        fromIndexBuilder.endControlFlow();
        
        builder.addMethod(fromIndexBuilder.build());
    }
    
    /**
     * Adds a custom toString method for enums
     */
    private void addCustomToString(TypeSpec.Builder builder, ProcessingContext context) {
        MethodSpec toString = MethodSpec.methodBuilder("toString")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(String.class)
                .addStatement("return name() + \"(\" + index + \")\"")
                .build();
        
        builder.addMethod(toString);
    }
    
    /**
     * Generates enum constant name from schema
     */
    private String generateEnumConstantName(BlueprintSchema schema) {
        if (schema.getTitle() != null) {
            return schema.getTitle().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        }
        
        if (schema.getIndex() != 0) {
            return "ALTERNATIVE_" + schema.getIndex();
        }
        
        return "UNKNOWN";
    }
    
    @Override
    protected List<FieldSpec> extractFields(TypeSpec.Builder builder) {
        // Enums have predefined fields, don't extract dynamically
        return new ArrayList<>();
    }
}
package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.impl;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.DataTypeProcessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.FieldNameResolver;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.squareup.javapoet.FieldSpec;

/**
 * Processes Blueprint schemas with 'string' data type.
 * Generates Java fields of type String.
 * 
 * This replaces the string case in the original switch statement.
 */
public class StringDataTypeProcessor implements DataTypeProcessor {
    
    @Override
    public boolean canProcess(BlueprintDatatype dataType) {
        return dataType == BlueprintDatatype.string;
    }
    
    @Override
    public FieldSpec processDataType(ProcessingContext context) {
        validateInput(context);
        
        String fieldName = FieldNameResolver.resolveFieldName(context);
        
        return context.getConfig().getDefaultFieldAccess()
                .applyTo(FieldSpec.builder(String.class, fieldName))
                .addJavadoc(generateJavaDoc(context))
                .build();
    }
    
    @Override
    public int getPriority() {
        return 10; // High priority for primitive types
    }
    
    private void validateInput(ProcessingContext context) {
        if (context.getSchema().getDataType() != BlueprintDatatype.string) {
            throw new IllegalArgumentException("Schema is not of type string");
        }
    }
    
    private String generateJavaDoc(ProcessingContext context) {
        StringBuilder javaDoc = new StringBuilder();
        
        if (context.getConfig().isGenerateJavaDoc()) {
            javaDoc.append(context.getJavaDoc());
            
            if (context.getSchema().getDescription() != null) {
                if (javaDoc.length() > 0) javaDoc.append("\n");
                javaDoc.append(context.getSchema().getDescription());
            }
            
            // Add enum values if specified
            if (context.getSchema().getEnumLiterals() != null && context.getSchema().getEnumLiterals().length > 0) {
                if (javaDoc.length() > 0) javaDoc.append("\n");
                javaDoc.append("Allowed values: ");
                for (int i = 0; i < context.getSchema().getEnumLiterals().length; i++) {
                    if (i > 0) javaDoc.append(", ");
                    javaDoc.append(context.getSchema().getEnumLiterals()[i]);
                }
            }
            
            // Add length constraints if any
            if (context.getSchema().getMinLength() > 0 || context.getSchema().getMaxLength() > 0) {
                if (javaDoc.length() > 0) javaDoc.append("\n");
                javaDoc.append("Length constraints: ");
                if (context.getSchema().getMinLength() > 0) {
                    javaDoc.append("min: ").append(context.getSchema().getMinLength());
                }
                if (context.getSchema().getMaxLength() > 0) {
                    if (context.getSchema().getMinLength() > 0) javaDoc.append(", ");
                    javaDoc.append("max: ").append(context.getSchema().getMaxLength());
                }
            }
        }
        
        return javaDoc.toString();
    }
}
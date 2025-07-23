package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.impl;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.DataTypeProcessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.FieldNameResolver;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.squareup.javapoet.FieldSpec;

/**
 * Processes Blueprint schemas with 'bool' data type.
 * Generates Java fields of type boolean.
 * 
 * This replaces the bool case in the original switch statement.
 */
public class BooleanDataTypeProcessor implements DataTypeProcessor {
    
    @Override
    public boolean canProcess(BlueprintDatatype dataType) {
        return dataType == BlueprintDatatype.bool;
    }
    
    @Override
    public FieldSpec processDataType(ProcessingContext context) {
        validateInput(context);
        
        String fieldName = FieldNameResolver.resolveFieldName(context);
        
        return context.getConfig().getDefaultFieldAccess()
                .applyTo(FieldSpec.builder(boolean.class, fieldName))
                .addJavadoc(generateJavaDoc(context))
                .build();
    }
    
    @Override
    public int getPriority() {
        return 10; // High priority for primitive types
    }
    
    private void validateInput(ProcessingContext context) {
        if (context.getSchema().getDataType() != BlueprintDatatype.bool) {
            throw new IllegalArgumentException("Schema is not of type bool");
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
            
            // Add comment about boolean usage in Plutus
            if (context.getSchema().getComment() != null) {
                if (javaDoc.length() > 0) javaDoc.append("\n");
                javaDoc.append("Note: ").append(context.getSchema().getComment());
            }
        }
        
        return javaDoc.toString();
    }
}
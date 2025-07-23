package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.impl;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.DataTypeProcessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.FieldNameResolver;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.squareup.javapoet.FieldSpec;

import java.math.BigInteger;

/**
 * Processes Blueprint schemas with 'integer' data type.
 * Generates Java fields of type BigInteger.
 * 
 * This replaces the integer case in the original switch statement.
 */
public class IntegerDataTypeProcessor implements DataTypeProcessor {
    
    @Override
    public boolean canProcess(BlueprintDatatype dataType) {
        return dataType == BlueprintDatatype.integer;
    }
    
    @Override
    public FieldSpec processDataType(ProcessingContext context) {
        validateInput(context);
        
        String fieldName = FieldNameResolver.resolveFieldName(context);
        
        return context.getConfig().getDefaultFieldAccess()
                .applyTo(FieldSpec.builder(BigInteger.class, fieldName))
                .addJavadoc(generateJavaDoc(context))
                .build();
    }
    
    @Override
    public int getPriority() {
        return 10; // High priority for primitive types
    }
    
    private void validateInput(ProcessingContext context) {
        if (context.getSchema().getDataType() != BlueprintDatatype.integer) {
            throw new IllegalArgumentException("Schema is not of type integer");
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
            
            // Add constraints if any
            boolean hasConstraints = false;
            if (context.getSchema().getMinimum() != 0 || context.getSchema().getMaximum() != 0 || 
                context.getSchema().getMultipleOf() != 0) {
                
                if (javaDoc.length() > 0) javaDoc.append("\n");
                javaDoc.append("Constraints: ");
                hasConstraints = true;
                
                if (context.getSchema().getMinimum() != 0) {
                    javaDoc.append("minimum: ").append(context.getSchema().getMinimum());
                }
                if (context.getSchema().getMaximum() != 0) {
                    if (context.getSchema().getMinimum() != 0) javaDoc.append(", ");
                    javaDoc.append("maximum: ").append(context.getSchema().getMaximum());
                }
                if (context.getSchema().getMultipleOf() != 0) {
                    if (context.getSchema().getMinimum() != 0 || context.getSchema().getMaximum() != 0) {
                        javaDoc.append(", ");
                    }
                    javaDoc.append("multiple of: ").append(context.getSchema().getMultipleOf());
                }
            }
        }
        
        return javaDoc.toString();
    }
}
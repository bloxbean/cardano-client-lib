package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.impl;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.DataTypeProcessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.FieldNameResolver;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.squareup.javapoet.FieldSpec;

import javax.lang.model.element.Modifier;

/**
 * Processes Blueprint schemas with 'bytes' data type.
 * Generates Java fields of type byte[].
 * 
 * This replaces the bytes case in the original switch statement.
 */
public class BytesDataTypeProcessor implements DataTypeProcessor {
    
    @Override
    public boolean canProcess(BlueprintDatatype dataType) {
        return dataType == BlueprintDatatype.bytes;
    }
    
    @Override
    public FieldSpec processDataType(ProcessingContext context) {
        validateInput(context);
        
        String fieldName = FieldNameResolver.resolveFieldName(context);
        
        return context.getConfig().getDefaultFieldAccess()
                .applyTo(FieldSpec.builder(byte[].class, fieldName))
                .addJavadoc(generateJavaDoc(context))
                .build();
    }
    
    @Override
    public int getPriority() {
        return 10; // High priority for primitive types
    }
    
    private void validateInput(ProcessingContext context) {
        if (context.getSchema().getDataType() != BlueprintDatatype.bytes) {
            throw new IllegalArgumentException("Schema is not of type bytes");
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
            if (context.getSchema().getMinLength() > 0 || context.getSchema().getMaxLength() > 0) {
                if (javaDoc.length() > 0) javaDoc.append("\n");
                javaDoc.append("Constraints: ");
                if (context.getSchema().getMinLength() > 0) {
                    javaDoc.append("min length: ").append(context.getSchema().getMinLength());
                }
                if (context.getSchema().getMaxLength() > 0) {
                    if (context.getSchema().getMinLength() > 0) javaDoc.append(", ");
                    javaDoc.append("max length: ").append(context.getSchema().getMaxLength());
                }
            }
        }
        
        return javaDoc.toString();
    }
}
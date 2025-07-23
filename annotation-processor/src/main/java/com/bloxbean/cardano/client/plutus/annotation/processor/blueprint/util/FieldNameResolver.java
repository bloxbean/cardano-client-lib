package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config.CodeGenerationConfig;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;

import javax.annotation.processing.ProcessingEnvironment;

/**
 * Utility class for resolving field names based on schema and configuration.
 * Applies naming strategies and handles fallbacks.
 */
public class FieldNameResolver {
    
    /**
     * Resolves the field name for the given processing context
     * 
     * @param context the processing context
     * @return the resolved field name
     */
    public static String resolveFieldName(ProcessingContext context) {
        String rawName = getRawFieldName(context);
        return context.getConfig().getNamingStrategy().transform(rawName);
    }
    
    /**
     * Resolves the class name for the given processing context
     * 
     * @param context the processing context
     * @return the resolved class name
     */
    public static String resolveClassName(ProcessingContext context) {
        String rawName = getRawClassName(context);
        
        // Class names should be PascalCase regardless of strategy
        return toPascalCase(rawName);
    }
    
    /**
     * Resolves the class name with explicit parameters
     * 
     * @param processingEnv the processing environment
     * @param config the code generation config
     * @param schemaTitle the schema title
     * @param alternativeName the alternative name
     * @return the resolved class name
     */
    public static String resolveClassName(ProcessingEnvironment processingEnv, 
                                        CodeGenerationConfig config,
                                        String schemaTitle, 
                                        String alternativeName) {
        String rawName = schemaTitle != null && !schemaTitle.isEmpty() ? 
                        schemaTitle : alternativeName;
        
        if (rawName == null || rawName.isEmpty()) {
            rawName = "GeneratedClass";
        }
        
        // Class names should be PascalCase regardless of strategy
        return toPascalCase(rawName);
    }
    
    private static String getRawFieldName(ProcessingContext context) {
        // Priority: explicit fieldName > schema title > alternative name
        if (context.getFieldName() != null && !context.getFieldName().isEmpty()) {
            return context.getFieldName();
        }
        
        if (context.getSchema().getTitle() != null && !context.getSchema().getTitle().isEmpty()) {
            return context.getSchema().getTitle();
        }
        
        if (context.getAlternativeName() != null && !context.getAlternativeName().isEmpty()) {
            return context.getAlternativeName();
        }
        
        // Fallback to a generic name
        return "field";
    }
    
    private static String getRawClassName(ProcessingContext context) {
        // Priority: explicit className > schema title > alternative name
        if (context.getClassName() != null && !context.getClassName().isEmpty()) {
            return context.getClassName();
        }
        
        if (context.getSchema().getTitle() != null && !context.getSchema().getTitle().isEmpty()) {
            return context.getSchema().getTitle();
        }
        
        if (context.getAlternativeName() != null && !context.getAlternativeName().isEmpty()) {
            return context.getAlternativeName();
        }
        
        // Fallback to a generic name
        return "GeneratedClass";
    }
    
    private static String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // Split on various delimiters and camelCase boundaries
        String[] words = input
                .replaceAll("([a-z])([A-Z])", "$1 $2") // camelCase boundaries
                .split("[\\s_-]+"); // whitespace, underscore, hyphen
        
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(capitalize(word));
            }
        }
        
        return result.toString();
    }
    
    private static String capitalize(String word) {
        if (word == null || word.isEmpty()) {
            return word;
        }
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }
}
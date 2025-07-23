package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.analyzer;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

/**
 * Utility class for analyzing Blueprint schemas.
 * Provides static methods to determine schema characteristics.
 * 
 * This replaces the scattered conditional logic in the original FieldSpecProcessor.
 */
public class SchemaAnalyzer {
    
    /**
     * Determines if a schema represents an enum type
     * 
     * @param schema the schema to analyze
     * @return true if the schema is an enum
     */
    public static boolean isEnumSchema(BlueprintSchema schema) {
        if (schema.getAnyOf() == null || schema.getAnyOf().size() <= 1) {
            return false;
        }
        
        // Should not have fields at the top level
        if (schema.getFields() != null && !schema.getFields().isEmpty()) {
            return false;
        }
        
        // Each anyOf should be a constructor with no fields
        for (BlueprintSchema anyOfSchema : schema.getAnyOf()) {
            if (anyOfSchema.getDataType() != BlueprintDatatype.constructor) {
                return false;
            }
            
            if (anyOfSchema.getTitle() == null || anyOfSchema.getTitle().isEmpty()) {
                return false;
            }
            
            // Should have no fields (pure enum values)
            if (anyOfSchema.getFields() != null && !anyOfSchema.getFields().isEmpty()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Determines if a schema represents an Option type (Some/None pattern)
     * 
     * @param schema the schema to analyze
     * @return true if the schema is an Option type
     */
    public static boolean isOptionSchema(BlueprintSchema schema) {
        if (!"Option".equals(schema.getTitle())) {
            return false;
        }
        
        if (schema.getAnyOf() == null || schema.getAnyOf().size() != 2) {
            return false;
        }
        
        BlueprintSchema someSchema = null;
        BlueprintSchema noneSchema = null;
        
        for (BlueprintSchema anyOf : schema.getAnyOf()) {
            if ("Some".equals(anyOf.getTitle())) {
                someSchema = anyOf;
            } else if ("None".equals(anyOf.getTitle())) {
                noneSchema = anyOf;
            }
        }
        
        if (someSchema == null || noneSchema == null) {
            return false;
        }
        
        // Some should have exactly one field
        if (someSchema.getFields() == null || someSchema.getFields().size() != 1) {
            return false;
        }
        
        // None should have no fields
        if (noneSchema.getFields() != null && !noneSchema.getFields().isEmpty()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Determines if a schema represents a Pair type
     * 
     * @param schema the schema to analyze
     * @return true if the schema is a Pair type
     */
    public static boolean isPairSchema(BlueprintSchema schema) {
        return "Pair".equals(schema.getTitle()) && 
               schema.getDataType() == BlueprintDatatype.pair;
    }
    
    /**
     * Determines if a schema requires an interface (multiple implementations)
     * 
     * @param schema the schema to analyze
     * @return true if the schema requires an interface
     */
    public static boolean requiresInterface(BlueprintSchema schema) {
        return schema.getAnyOf() != null && schema.getAnyOf().size() > 1 && !isEnumSchema(schema);
    }
    
    /**
     * Determines if a schema is a primitive type that should be skipped
     * 
     * @param schema the schema to analyze
     * @return true if the schema should be skipped
     */
    public static boolean isPrimitiveTypeAlias(BlueprintSchema schema) {
        if (schema.getDataType() == null || !schema.getDataType().isPrimitiveType()) {
            return false;
        }
        
        // Check if it's just an alias with no additional structure
        return schema.getItems() == null &&
               (schema.getFields() == null || schema.getFields().isEmpty()) &&
               (schema.getAnyOf() == null || schema.getAnyOf().isEmpty()) &&
               (schema.getAllOf() == null || schema.getAllOf().isEmpty()) &&
               (schema.getOneOf() == null || schema.getOneOf().isEmpty()) &&
               (schema.getNotOf() == null || schema.getNotOf().isEmpty());
    }
    
    /**
     * Determines if a schema has inline definitions (not references)
     * 
     * @param schema the schema to analyze
     * @return true if the schema has inline definitions
     */
    public static boolean hasInlineDefinitions(BlueprintSchema schema) {
        return schema.getRef() == null;
    }
    
    /**
     * Gets the complexity score of a schema (for optimization decisions)
     * 
     * @param schema the schema to analyze
     * @return complexity score (higher = more complex)
     */
    public static int getComplexityScore(BlueprintSchema schema) {
        int score = 0;
        
        // Base complexity for having fields
        if (schema.getFields() != null) {
            score += schema.getFields().size() * 2;
        }
        
        // Complexity for anyOf/oneOf/allOf
        if (schema.getAnyOf() != null) {
            score += schema.getAnyOf().size() * 3;
        }
        if (schema.getOneOf() != null) {
            score += schema.getOneOf().size() * 3;
        }
        if (schema.getAllOf() != null) {
            score += schema.getAllOf().size() * 2;
        }
        
        // Complexity for nested structures
        if (schema.getDataType() != null) {
            switch (schema.getDataType()) {
                case list:
                case map:
                    score += 5;
                    break;
                case constructor:
                    score += 3;
                    break;
                case option:
                case pair:
                    score += 4;
                    break;
            }
        }
        
        return score;
    }
    
    /**
     * Checks if a schema title is valid for class generation
     * 
     * @param schema the schema to check
     * @return true if the title is valid
     */
    public static boolean hasValidTitle(BlueprintSchema schema) {
        return schema.getTitle() != null && 
               !schema.getTitle().isEmpty() &&
               !schema.getTitle().isBlank();
    }
}
package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.generator;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.squareup.javapoet.TypeSpec;

/**
 * Base interface for code generators using the Template Method pattern.
 * Each implementation generates a specific type of code (Validator, Datum, Converter, etc.)
 */
public interface CodeGenerator {
    
    /**
     * Generates the TypeSpec for the given context
     * 
     * @param context the processing context
     * @return the generated TypeSpec
     */
    TypeSpec generate(ProcessingContext context);
    
    /**
     * Returns the type of code this generator produces
     * 
     * @return generator type
     */
    GeneratorType getType();
    
    /**
     * Validates the input context before generation
     * 
     * @param context the processing context
     * @throws IllegalArgumentException if context is invalid
     */
    default void validateInput(ProcessingContext context) {
        if (context == null) {
            throw new IllegalArgumentException("ProcessingContext cannot be null");
        }
        if (context.getSchema() == null) {
            throw new IllegalArgumentException("BlueprintSchema cannot be null");
        }
    }
    
    /**
     * Enumeration of different generator types
     */
    enum GeneratorType {
        VALIDATOR("Validator"),
        DATUM("Datum"), 
        REDEEMER("Redeemer"),
        CONVERTER("Converter"),
        INTERFACE("Interface"),
        ENUM("Enum"),
        DATA_IMPLEMENTATION("DataImplementation");
        
        private final String displayName;
        
        GeneratorType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
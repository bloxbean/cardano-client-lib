package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.validation;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

/**
 * Base class for validation rules using the Chain of Responsibility pattern.
 * Each rule validates a specific aspect of a Blueprint schema.
 * 
 * This replaces the scattered validation logic in the original code
 * with a composable and extensible validation system.
 */
public abstract class ValidationRule {
    
    private ValidationRule next;
    
    /**
     * Sets the next rule in the chain
     * 
     * @param next the next validation rule
     * @return the next rule for chaining
     */
    public ValidationRule setNext(ValidationRule next) {
        this.next = next;
        return next;
    }
    
    /**
     * Validates the schema and continues the chain if validation passes
     * 
     * @param schema the schema to validate
     * @return the validation result
     */
    public final ValidationResult validate(BlueprintSchema schema) {
        ValidationResult result = doValidate(schema);
        
        if (result.isValid() && next != null) {
            ValidationResult nextResult = next.validate(schema);
            result = result.merge(nextResult);
        }
        
        return result;
    }
    
    /**
     * Performs the actual validation logic for this rule
     * 
     * @param schema the schema to validate
     * @return the validation result
     */
    protected abstract ValidationResult doValidate(BlueprintSchema schema);
    
    /**
     * Returns the name of this validation rule
     * 
     * @return rule name
     */
    public String getRuleName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * Returns a description of what this rule validates
     * 
     * @return rule description
     */
    public abstract String getDescription();
    
    /**
     * Returns the priority of this rule (lower values = higher priority)
     * 
     * @return priority value
     */
    public int getPriority() {
        return 100;
    }
}
package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents the result of schema validation.
 * Contains errors, warnings, and success status.
 */
public class ValidationResult {
    
    private final boolean valid;
    private final List<ValidationError> errors;
    private final List<ValidationWarning> warnings;
    
    private ValidationResult(boolean valid, List<ValidationError> errors, List<ValidationWarning> warnings) {
        this.valid = valid;
        this.errors = errors != null ? List.copyOf(errors) : List.of();
        this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
    
    // Factory methods
    public static ValidationResult success() {
        return new ValidationResult(true, null, null);
    }
    
    public static ValidationResult successWithWarnings(List<ValidationWarning> warnings) {
        return new ValidationResult(true, null, warnings);
    }
    
    public static ValidationResult error(String message) {
        return new ValidationResult(false, List.of(new ValidationError(message)), null);
    }
    
    public static ValidationResult error(ValidationError error) {
        return new ValidationResult(false, List.of(error), null);
    }
    
    public static ValidationResult errors(List<ValidationError> errors) {
        return new ValidationResult(false, errors, null);
    }
    
    public static ValidationResult errorsAndWarnings(List<ValidationError> errors, List<ValidationWarning> warnings) {
        return new ValidationResult(false, errors, warnings);
    }
    
    // Getters
    public boolean isValid() { return valid; }
    public List<ValidationError> getErrors() { return errors; }
    public List<ValidationWarning> getWarnings() { return warnings; }
    
    // Utility methods
    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean hasWarnings() { return !warnings.isEmpty(); }
    
    public int getErrorCount() { return errors.size(); }
    public int getWarningCount() { return warnings.size(); }
    
    /**
     * Merges this result with another result
     * 
     * @param other the other validation result
     * @return merged result
     */
    public ValidationResult merge(ValidationResult other) {
        if (other == null) return this;
        
        List<ValidationError> mergedErrors = new ArrayList<>(this.errors);
        mergedErrors.addAll(other.errors);
        
        List<ValidationWarning> mergedWarnings = new ArrayList<>(this.warnings);
        mergedWarnings.addAll(other.warnings);
        
        boolean mergedValid = this.valid && other.valid;
        
        return new ValidationResult(mergedValid, mergedErrors, mergedWarnings);
    }
    
    /**
     * Returns a formatted summary of the validation result
     * 
     * @return formatted summary
     */
    public String getSummary() {
        if (valid && !hasWarnings()) {
            return "Validation passed";
        }
        
        StringBuilder sb = new StringBuilder();
        if (!valid) {
            sb.append("Validation failed with ").append(getErrorCount()).append(" error(s)");
        } else {
            sb.append("Validation passed");
        }
        
        if (hasWarnings()) {
            if (sb.length() > 0) sb.append(" and ");
            sb.append(getWarningCount()).append(" warning(s)");
        }
        
        return sb.toString();
    }
    
    /**
     * Returns all error messages as a list
     * 
     * @return list of error messages
     */
    public List<String> getErrorMessages() {
        return errors.stream()
                .map(ValidationError::getMessage)
                .collect(Collectors.toList());
    }
    
    /**
     * Returns all warning messages as a list
     * 
     * @return list of warning messages
     */
    public List<String> getWarningMessages() {
        return warnings.stream()
                .map(ValidationWarning::getMessage)
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationResult that = (ValidationResult) o;
        return valid == that.valid &&
               Objects.equals(errors, that.errors) &&
               Objects.equals(warnings, that.warnings);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(valid, errors, warnings);
    }
    
    @Override
    public String toString() {
        return "ValidationResult{" +
               "valid=" + valid +
               ", errors=" + errors.size() +
               ", warnings=" + warnings.size() +
               '}';
    }
}
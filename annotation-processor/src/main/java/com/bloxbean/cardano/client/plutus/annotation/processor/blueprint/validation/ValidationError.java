package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.validation;

import java.util.Objects;

/**
 * Represents a validation error with context information.
 * Provides detailed information about what went wrong during validation.
 */
public class ValidationError {
    
    private final String message;
    private final String field;
    private final ErrorType type;
    private final String code;
    
    public ValidationError(String message) {
        this(message, null, ErrorType.GENERAL, null);
    }
    
    public ValidationError(String message, String field) {
        this(message, field, ErrorType.FIELD_ERROR, null);
    }
    
    public ValidationError(String message, String field, ErrorType type) {
        this(message, field, type, null);
    }
    
    public ValidationError(String message, String field, ErrorType type, String code) {
        this.message = Objects.requireNonNull(message, "Message cannot be null");
        this.field = field;
        this.type = type != null ? type : ErrorType.GENERAL;
        this.code = code;
    }
    
    // Getters
    public String getMessage() { return message; }
    public String getField() { return field; }
    public ErrorType getType() { return type; }
    public String getCode() { return code; }
    
    // Convenience methods
    public boolean hasField() { return field != null; }
    public boolean hasCode() { return code != null; }
    
    /**
     * Returns a formatted error message with field context if available
     * 
     * @return formatted message
     */
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        
        if (hasField()) {
            sb.append("Field '").append(field).append("': ");
        }
        
        sb.append(message);
        
        if (hasCode()) {
            sb.append(" (").append(code).append(")");
        }
        
        return sb.toString();
    }
    
    /**
     * Enumeration of error types for categorization
     */
    public enum ErrorType {
        GENERAL("General"),
        FIELD_ERROR("Field Error"),
        TYPE_MISMATCH("Type Mismatch"),
        MISSING_REQUIRED("Missing Required"),
        INVALID_FORMAT("Invalid Format"),
        CONSTRAINT_VIOLATION("Constraint Violation"),
        REFERENCE_ERROR("Reference Error");
        
        private final String displayName;
        
        ErrorType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationError that = (ValidationError) o;
        return Objects.equals(message, that.message) &&
               Objects.equals(field, that.field) &&
               type == that.type &&
               Objects.equals(code, that.code);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(message, field, type, code);
    }
    
    @Override
    public String toString() {
        return "ValidationError{" +
               "message='" + message + '\'' +
               (field != null ? ", field='" + field + '\'' : "") +
               ", type=" + type +
               (code != null ? ", code='" + code + '\'' : "") +
               '}';
    }
}
package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.validation;

import java.util.Objects;

/**
 * Represents a validation warning with context information.
 * Warnings indicate potential issues that don't prevent code generation but should be reviewed.
 */
public class ValidationWarning {
    
    private final String message;
    private final String field;
    private final WarningType type;
    private final String code;
    
    public ValidationWarning(String message) {
        this(message, null, WarningType.GENERAL, null);
    }
    
    public ValidationWarning(String message, String field) {
        this(message, field, WarningType.FIELD_WARNING, null);
    }
    
    public ValidationWarning(String message, String field, WarningType type) {
        this(message, field, type, null);
    }
    
    public ValidationWarning(String message, String field, WarningType type, String code) {
        this.message = Objects.requireNonNull(message, "Message cannot be null");
        this.field = field;
        this.type = type != null ? type : WarningType.GENERAL;
        this.code = code;
    }
    
    // Getters
    public String getMessage() { return message; }
    public String getField() { return field; }
    public WarningType getType() { return type; }
    public String getCode() { return code; }
    
    // Convenience methods
    public boolean hasField() { return field != null; }
    public boolean hasCode() { return code != null; }
    
    /**
     * Returns a formatted warning message with field context if available
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
     * Enumeration of warning types for categorization
     */
    public enum WarningType {
        GENERAL("General"),
        FIELD_WARNING("Field Warning"),
        DEPRECATED("Deprecated"),
        PERFORMANCE("Performance"),
        STYLE("Style"),
        BEST_PRACTICE("Best Practice"),
        COMPATIBILITY("Compatibility");
        
        private final String displayName;
        
        WarningType(String displayName) {
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
        ValidationWarning that = (ValidationWarning) o;
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
        return "ValidationWarning{" +
               "message='" + message + '\'' +
               (field != null ? ", field='" + field + '\'' : "") +
               ", type=" + type +
               (code != null ? ", code='" + code + '\'' : "") +
               '}';
    }
}
package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.exception;

/**
 * Exception thrown when code generation fails.
 * Provides detailed context about what went wrong during the generation process.
 */
public class CodeGenerationException extends RuntimeException {
    
    private final String component;
    private final String phase;
    
    public CodeGenerationException(String message) {
        super(message);
        this.component = null;
        this.phase = null;
    }
    
    public CodeGenerationException(String message, Throwable cause) {
        super(message, cause);
        this.component = null;
        this.phase = null;
    }
    
    public CodeGenerationException(String message, String component, String phase) {
        super(message);
        this.component = component;
        this.phase = phase;
    }
    
    public CodeGenerationException(String message, String component, String phase, Throwable cause) {
        super(message, cause);
        this.component = component;
        this.phase = phase;
    }
    
    /**
     * Returns the component that failed (e.g., "ValidatorGenerator", "DatumProcessor")
     * 
     * @return component name or null
     */
    public String getComponent() {
        return component;
    }
    
    /**
     * Returns the phase where the failure occurred (e.g., "validation", "generation", "writing")
     * 
     * @return phase name or null
     */
    public String getPhase() {
        return phase;
    }
    
    /**
     * Returns a detailed error message with context
     * 
     * @return detailed error message
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        
        if (component != null) {
            sb.append("[").append(component).append("] ");
        }
        
        if (phase != null) {
            sb.append("(").append(phase).append(") ");
        }
        
        sb.append(getMessage());
        
        if (getCause() != null) {
            sb.append(" - Caused by: ").append(getCause().getMessage());
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "CodeGenerationException{" +
               "component='" + component + '\'' +
               ", phase='" + phase + '\'' +
               ", message='" + getMessage() + '\'' +
               '}';
    }
}
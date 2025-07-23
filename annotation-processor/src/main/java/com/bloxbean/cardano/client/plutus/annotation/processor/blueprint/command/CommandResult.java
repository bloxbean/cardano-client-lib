package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command;

import java.util.List;
import java.util.Optional;

/**
 * Represents the result of executing a SchemaCommand.
 * Provides success/failure status, messages, and optional generated artifacts.
 */
public class CommandResult {
    
    private final boolean success;
    private final String message;
    private final List<String> warnings;
    private final Optional<Exception> exception;
    private final Optional<Object> artifact; // Generated TypeSpec, ClassName, etc.
    
    private CommandResult(boolean success, String message, List<String> warnings, 
                         Exception exception, Object artifact) {
        this.success = success;
        this.message = message;
        this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
        this.exception = Optional.ofNullable(exception);
        this.artifact = Optional.ofNullable(artifact);
    }
    
    // Factory methods for creating results
    public static CommandResult success(String message) {
        return new CommandResult(true, message, null, null, null);
    }
    
    public static CommandResult success(String message, Object artifact) {
        return new CommandResult(true, message, null, null, artifact);
    }
    
    public static CommandResult successWithWarnings(String message, List<String> warnings) {
        return new CommandResult(true, message, warnings, null, null);
    }
    
    public static CommandResult failure(String message) {
        return new CommandResult(false, message, null, null, null);
    }
    
    public static CommandResult failure(String message, Exception exception) {
        return new CommandResult(false, message, null, exception, null);
    }
    
    public static CommandResult skip(String reason) {
        return new CommandResult(true, "Skipped: " + reason, null, null, null);
    }
    
    // Getters
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public Optional<Exception> getException() {
        return exception;
    }
    
    public Optional<Object> getArtifact() {
        return artifact;
    }
    
    public <T> Optional<T> getArtifact(Class<T> type) {
        return artifact.filter(type::isInstance).map(type::cast);
    }
    
    // Utility methods
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public boolean hasException() {
        return exception.isPresent();
    }
    
    public boolean hasArtifact() {
        return artifact.isPresent();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CommandResult{")
          .append("success=").append(success)
          .append(", message='").append(message).append('\'');
        
        if (hasWarnings()) {
            sb.append(", warnings=").append(warnings.size());
        }
        
        if (hasException()) {
            sb.append(", exception=").append(exception.get().getClass().getSimpleName());
        }
        
        if (hasArtifact()) {
            sb.append(", artifact=").append(artifact.get().getClass().getSimpleName());
        }
        
        sb.append('}');
        return sb.toString();
    }
}
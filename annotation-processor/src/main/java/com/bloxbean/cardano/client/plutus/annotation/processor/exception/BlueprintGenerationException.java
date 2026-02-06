package com.bloxbean.cardano.client.plutus.annotation.processor.exception;

/**
 * Exception thrown when blueprint processing fails during annotation processing.
 *
 * <p>This exception indicates a problem with the blueprint structure or content
 * that prevents successful Java class generation. Common causes include:</p>
 * <ul>
 *   <li>Missing or invalid titles in schema definitions</li>
 *   <li>Invalid definition keys that cannot be parsed</li>
 *   <li>Malformed schema structures</li>
 *   <li>CIP-57 specification violations</li>
 * </ul>
 *
 * <p><b>Note:</b> This is a RuntimeException to integrate with annotation processor
 * error handling - the annotation processor will catch it and report compilation errors.</p>
 */
public class BlueprintGenerationException extends RuntimeException {

    private final String definitionKey;

    /**
     * Constructs a new BlueprintGenerationException with the specified detail message.
     *
     * @param message the detail message explaining what went wrong
     */
    public BlueprintGenerationException(String message) {
        super(message);
        this.definitionKey = null;
    }

    /**
     * Constructs a new BlueprintGenerationException with the specified detail message and cause.
     *
     * @param message the detail message explaining what went wrong
     * @param cause the underlying cause of the exception
     */
    public BlueprintGenerationException(String message, Throwable cause) {
        super(message, cause);
        this.definitionKey = null;
    }

    /**
     * Private constructor for creating exceptions with definition key context.
     *
     * @param definitionKey the blueprint definition key that caused the error
     * @param message the detail message explaining what went wrong
     * @param withContext marker parameter to distinguish from public constructors
     */
    private BlueprintGenerationException(String definitionKey, String message, boolean withContext) {
        super(String.format("Cannot process blueprint definition '%s': %s", definitionKey, message));
        this.definitionKey = definitionKey;
    }

    /**
     * Constructs a new BlueprintGenerationException for a specific definition key.
     *
     * @param definitionKey the blueprint definition key that caused the error
     * @param message the detail message explaining what went wrong
     * @return a new BlueprintGenerationException with formatted message
     */
    public static BlueprintGenerationException forDefinition(String definitionKey, String message) {
        return new BlueprintGenerationException(definitionKey, message, true);
    }

    /**
     * Checks if this exception already has definition key context.
     *
     * @return true if the exception was created with forDefinition(), false otherwise
     */
    public boolean hasDefinitionContext() {
        return definitionKey != null;
    }
}

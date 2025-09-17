package com.bloxbean.cardano.statetrees.rocksdb.exceptions;

/**
 * Exception thrown when RocksDB configuration is invalid or initialization fails.
 * 
 * <p>This exception is used for configuration-related errors such as invalid
 * database paths, malformed options, missing column families, or other
 * setup issues that prevent proper RocksDB initialization or operation.</p>
 * 
 * <p><b>Common Configuration Issues:</b></p>
 * <ul>
 *   <li>Invalid or inaccessible database paths</li>
 *   <li>Conflicting or malformed database options</li>
 *   <li>Missing required column families</li>
 *   <li>Incompatible database versions or formats</li>
 *   <li>Insufficient permissions for database operations</li>
 *   <li>Resource limits preventing initialization</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * try {
 *     RocksDbNodeStore store = new RocksDbNodeStore("/invalid/path");
 * } catch (RuntimeException e) {
 *     if (e.getCause() instanceof RocksDBException) {
 *         throw new RocksDbConfigurationException(
 *             "Invalid database path: /invalid/path", 
 *             "database.path", 
 *             "/invalid/path", 
 *             e.getCause());
 *     }
 * }
 * 
 * // Handling configuration exceptions
 * try {
 *     initializeDatabase();
 * } catch (RocksDbConfigurationException e) {
 *     if ("database.path".equals(e.getConfigurationKey())) {
 *         logger.error("Database path configuration error: " + e.getConfigurationValue());
 *         // Provide fallback path or prompt user
 *         retryWithFallbackPath();
 *     } else {
 *         throw new ServiceException("Database configuration error", e);
 *     }
 * }
 * }</pre>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class RocksDbConfigurationException extends RocksDbStorageException {
    
    /**
     * The configuration key that caused the error (e.g., "database.path").
     */
    private final String configurationKey;
    
    /**
     * The problematic configuration value (if applicable).
     */
    private final String configurationValue;
    
    /**
     * Creates a new RocksDbConfigurationException with a simple message.
     * 
     * @param message the detailed error message
     */
    public RocksDbConfigurationException(String message) {
        super(message);
        this.configurationKey = null;
        this.configurationValue = null;
    }
    
    /**
     * Creates a new RocksDbConfigurationException with a message and cause.
     * 
     * @param message the detailed error message
     * @param cause the underlying cause of the configuration error
     */
    public RocksDbConfigurationException(String message, Throwable cause) {
        super(message, cause);
        this.configurationKey = null;
        this.configurationValue = null;
    }
    
    /**
     * Creates a new RocksDbConfigurationException with specific configuration details.
     * 
     * @param message the detailed error message
     * @param configurationKey the configuration key that caused the error
     * @param configurationValue the problematic configuration value
     * @param cause the underlying cause of the configuration error
     */
    public RocksDbConfigurationException(String message, String configurationKey, 
                                        String configurationValue, Throwable cause) {
        super(enhanceMessage(message, configurationKey, configurationValue), cause);
        this.configurationKey = configurationKey;
        this.configurationValue = configurationValue;
    }
    
    /**
     * Returns the configuration key that caused the error.
     * 
     * @return the configuration key, or null if not specified
     */
    public String getConfigurationKey() {
        return configurationKey;
    }
    
    /**
     * Returns the problematic configuration value.
     * 
     * @return the configuration value, or null if not specified
     */
    public String getConfigurationValue() {
        return configurationValue;
    }
    
    /**
     * Checks if this exception is related to a specific configuration key.
     * 
     * @param key the configuration key to check
     * @return true if this exception is for the specified key, false otherwise
     */
    public boolean isConfigurationKey(String key) {
        return configurationKey != null && configurationKey.equals(key);
    }
    
    /**
     * Returns detailed diagnostic information about the configuration error.
     * 
     * <p>This method provides comprehensive debugging information including
     * the configuration details, error context, and potential resolution
     * suggestions.</p>
     * 
     * @return detailed diagnostic information
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("RocksDB Configuration Error:\n");
        
        if (configurationKey != null) {
            info.append("  Configuration Key: ").append(configurationKey).append("\n");
        }
        
        if (configurationValue != null) {
            info.append("  Configuration Value: ").append(configurationValue).append("\n");
        }
        
        info.append("  Error Message: ").append(getMessage()).append("\n");
        
        Throwable cause = getCause();
        if (cause != null) {
            info.append("  Cause: ").append(cause.getClass().getSimpleName())
                .append(" - ").append(cause.getMessage()).append("\n");
        }
        
        // Provide resolution suggestions based on common configuration errors
        info.append("  Resolution Suggestions:\n");
        
        if (configurationKey != null) {
            switch (configurationKey) {
                case "database.path":
                    info.append("    - Verify the database path exists and is accessible\n");
                    info.append("    - Check file system permissions\n");
                    info.append("    - Ensure sufficient disk space is available\n");
                    break;
                case "column.family":
                    info.append("    - Verify all required column families are specified\n");
                    info.append("    - Check column family names for typos\n");
                    info.append("    - Ensure column family options are valid\n");
                    break;
                case "db.options":
                    info.append("    - Review database option values for validity\n");
                    info.append("    - Check for conflicting option combinations\n");
                    info.append("    - Verify system resource limits\n");
                    break;
                default:
                    info.append("    - Review the configuration value for correctness\n");
                    info.append("    - Check the documentation for valid values\n");
                    info.append("    - Verify system prerequisites are met\n");
            }
        } else {
            info.append("    - Review all configuration values for correctness\n");
            info.append("    - Check system prerequisites and permissions\n");
            info.append("    - Verify RocksDB library compatibility\n");
        }
        
        return info.toString();
    }
    
    /**
     * Enhances the error message with configuration-specific information.
     * 
     * @param baseMessage the base error message
     * @param configurationKey the configuration key
     * @param configurationValue the configuration value
     * @return the enhanced message
     */
    private static String enhanceMessage(String baseMessage, String configurationKey, String configurationValue) {
        StringBuilder enhanced = new StringBuilder(baseMessage);
        
        if (configurationKey != null || configurationValue != null) {
            enhanced.append(" (");
            
            if (configurationKey != null) {
                enhanced.append("key: ").append(configurationKey);
                
                if (configurationValue != null) {
                    enhanced.append(", value: ").append(configurationValue);
                }
            } else {
                enhanced.append("value: ").append(configurationValue);
            }
            
            enhanced.append(")");
        }
        
        return enhanced.toString();
    }
}
package com.bloxbean.cardano.client.watcher.visualizer;

/**
 * Defines different visualization styles for chain diagrams.
 * 
 * Each style determines how the chain structure and progress
 * are rendered in the console output.
 */
public enum VisualizationStyle {
    
    /**
     * Basic ASCII characters (+-|) for maximum compatibility.
     * Works in all terminals and consoles.
     */
    SIMPLE_ASCII,
    
    /**
     * Unicode box drawing characters (┌─┐│└┘) for better visual appearance.
     * Requires Unicode support in the terminal.
     */
    UNICODE_BOX,
    
    /**
     * Minimal representation with reduced detail.
     * Useful for logs or when space is limited.
     */
    COMPACT,
    
    /**
     * Full information including all metadata.
     * Best for debugging and detailed analysis.
     */
    DETAILED;
    
    /**
     * Get the default visualization style based on terminal capabilities.
     * 
     * @return the recommended style for the current environment
     */
    public static VisualizationStyle getDefault() {
        if (TerminalCapabilities.supportsUnicode()) {
            return UNICODE_BOX;
        }
        return SIMPLE_ASCII;
    }
}
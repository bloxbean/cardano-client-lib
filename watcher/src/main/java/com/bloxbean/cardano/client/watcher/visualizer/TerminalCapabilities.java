package com.bloxbean.cardano.client.watcher.visualizer;

/**
 * Utility class for detecting terminal capabilities.
 * 
 * This class helps determine what visualization features are supported
 * by the current terminal environment, allowing the visualizer to
 * adapt its output accordingly.
 */
public class TerminalCapabilities {
    
    private static Boolean unicodeSupport = null;
    private static Boolean ansiColorSupport = null;
    
    /**
     * Check if the terminal supports Unicode characters.
     * 
     * @return true if Unicode is supported, false otherwise
     */
    public static boolean supportsUnicode() {
        if (unicodeSupport == null) {
            unicodeSupport = detectUnicodeSupport();
        }
        return unicodeSupport;
    }
    
    /**
     * Check if the terminal supports ANSI color codes.
     * 
     * @return true if ANSI colors are supported, false otherwise
     */
    public static boolean supportsAnsiColors() {
        if (ansiColorSupport == null) {
            ansiColorSupport = detectAnsiColorSupport();
        }
        return ansiColorSupport;
    }
    
    /**
     * Get the best visualization style for the current terminal.
     * 
     * @return the recommended visualization style
     */
    public static VisualizationStyle getBestStyle() {
        if (supportsUnicode()) {
            return VisualizationStyle.UNICODE_BOX;
        }
        return VisualizationStyle.SIMPLE_ASCII;
    }
    
    /**
     * Get the terminal width in columns.
     * 
     * @return the terminal width, or 80 as default
     */
    public static int getTerminalWidth() {
        try {
            String columns = System.getenv("COLUMNS");
            if (columns != null) {
                return Integer.parseInt(columns);
            }
        } catch (NumberFormatException ignored) {
        }
        return 80; // Default width
    }
    
    /**
     * Check if the output is being redirected (not a TTY).
     * 
     * @return true if output is redirected, false if TTY
     */
    public static boolean isOutputRedirected() {
        // Check if we're in a TTY
        return System.console() == null;
    }
    
    /**
     * Reset cached capability detection.
     * Useful for testing or when terminal environment changes.
     */
    public static void resetCache() {
        unicodeSupport = null;
        ansiColorSupport = null;
    }
    
    // Private detection methods
    
    private static boolean detectUnicodeSupport() {
        // Check file encoding
        String encoding = System.getProperty("file.encoding");
        if (encoding != null) {
            String lowerEncoding = encoding.toLowerCase();
            if (lowerEncoding.contains("utf") || lowerEncoding.contains("unicode")) {
                return true;
            }
        }
        
        // Check LANG environment variable
        String lang = System.getenv("LANG");
        if (lang != null) {
            String lowerLang = lang.toLowerCase();
            if (lowerLang.contains("utf") || lowerLang.contains("unicode")) {
                return true;
            }
        }
        
        // Check LC_ALL environment variable
        String lcAll = System.getenv("LC_ALL");
        if (lcAll != null && lcAll.toLowerCase().contains("utf")) {
            return true;
        }
        
        // Check if we're on Windows (usually doesn't support Unicode well in console)
        String os = System.getProperty("os.name");
        if (os != null && os.toLowerCase().contains("windows")) {
            // Check if running in Windows Terminal (supports Unicode)
            String wtSession = System.getenv("WT_SESSION");
            return wtSession != null;
        }
        
        // Default to false for safety
        return false;
    }
    
    private static boolean detectAnsiColorSupport() {
        // If output is redirected, don't use colors
        if (isOutputRedirected()) {
            return false;
        }
        
        // Check TERM environment variable
        String term = System.getenv("TERM");
        if (term != null) {
            if (term.equals("dumb")) {
                return false;
            }
            if (term.contains("color") || term.contains("256")) {
                return true;
            }
        }
        
        // Check for common CI environment variables that usually support colors
        if (System.getenv("CI") != null || 
            System.getenv("GITHUB_ACTIONS") != null ||
            System.getenv("JENKINS_HOME") != null) {
            return true;
        }
        
        // Check if we're in IntelliJ IDEA (supports colors)
        String ideaPrefix = System.getProperty("idea.launcher.port");
        if (ideaPrefix != null) {
            return true;
        }
        
        // Check for Windows Terminal
        String wtSession = System.getenv("WT_SESSION");
        if (wtSession != null) {
            return true;
        }
        
        // Check for NO_COLOR environment variable (standard for disabling colors)
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        
        // Check for COLORTERM (indicates color support)
        if (System.getenv("COLORTERM") != null) {
            return true;
        }
        
        // Default based on OS
        String os = System.getProperty("os.name");
        if (os != null) {
            String lowerOs = os.toLowerCase();
            // macOS and Linux usually support colors
            if (lowerOs.contains("mac") || lowerOs.contains("linux") || lowerOs.contains("unix")) {
                return true;
            }
            // Windows Command Prompt doesn't support ANSI by default
            if (lowerOs.contains("windows")) {
                return false;
            }
        }
        
        return false;
    }
}
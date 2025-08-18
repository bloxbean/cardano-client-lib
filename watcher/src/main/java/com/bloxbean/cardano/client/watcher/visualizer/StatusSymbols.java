package com.bloxbean.cardano.client.watcher.visualizer;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;

import java.util.Map;

/**
 * Provides status symbol mappings for different visualization styles.
 * 
 * This class maps WatchStatus values to visual representations
 * appropriate for different terminal capabilities.
 */
public class StatusSymbols {
    
    /**
     * Unicode/emoji symbols for enhanced visual display.
     * These provide the best visual clarity when supported.
     */
    private static final Map<WatchStatus, String> UNICODE_SYMBOLS = Map.of(
        WatchStatus.BUILDING, "🔨",
        WatchStatus.PENDING, "⏳",
        WatchStatus.WATCHING, "👁",
        WatchStatus.SUBMITTED, "📤",
        WatchStatus.RETRYING, "🔄",
        WatchStatus.CONFIRMED, "✅",
        WatchStatus.FAILED, "❌",
        WatchStatus.CANCELLED, "⊘",
        WatchStatus.REBUILDING, "↩️"
    );
    
    /**
     * Simple ASCII symbols for maximum compatibility.
     * These work in all terminals but are less visually distinctive.
     */
    private static final Map<WatchStatus, String> ASCII_SYMBOLS = Map.of(
        WatchStatus.BUILDING, "[#]",
        WatchStatus.PENDING, "[ ]",
        WatchStatus.WATCHING, "[~]",
        WatchStatus.SUBMITTED, "[>]",
        WatchStatus.RETRYING, "[R]",
        WatchStatus.CONFIRMED, "[✓]",
        WatchStatus.FAILED, "[X]",
        WatchStatus.CANCELLED, "[-]",
        WatchStatus.REBUILDING, "[<]"
    );
    
    /**
     * Compact single-character symbols for minimal space usage.
     */
    private static final Map<WatchStatus, String> COMPACT_SYMBOLS = Map.of(
        WatchStatus.BUILDING, "#",
        WatchStatus.PENDING, ".",
        WatchStatus.WATCHING, "~",
        WatchStatus.SUBMITTED, ">",
        WatchStatus.RETRYING, "R",
        WatchStatus.CONFIRMED, "✓",
        WatchStatus.FAILED, "X",
        WatchStatus.CANCELLED, "-",
        WatchStatus.REBUILDING, "<"
    );
    
    /**
     * Text labels for detailed status display.
     */
    private static final Map<WatchStatus, String> TEXT_LABELS = Map.of(
        WatchStatus.BUILDING, "BUILDING",
        WatchStatus.PENDING, "PENDING",
        WatchStatus.WATCHING, "WATCHING",
        WatchStatus.SUBMITTED, "SUBMITTED",
        WatchStatus.RETRYING, "RETRYING",
        WatchStatus.CONFIRMED, "CONFIRMED",
        WatchStatus.FAILED, "FAILED",
        WatchStatus.CANCELLED, "CANCELLED",
        WatchStatus.REBUILDING, "REBUILDING"
    );
    
    /**
     * Get the status symbol for the given status and style.
     * 
     * @param status the watch status
     * @param style the visualization style
     * @return the appropriate symbol for the status
     */
    public static String getSymbol(WatchStatus status, VisualizationStyle style) {
        if (status == null) {
            return "?";
        }
        
        switch (style) {
            case UNICODE_BOX:
                return UNICODE_SYMBOLS.getOrDefault(status, "?");
            case COMPACT:
                return COMPACT_SYMBOLS.getOrDefault(status, "?");
            case DETAILED:
                return String.format("%-10s", TEXT_LABELS.getOrDefault(status, "UNKNOWN"));
            case SIMPLE_ASCII:
            default:
                return ASCII_SYMBOLS.getOrDefault(status, "[?]");
        }
    }
    
    /**
     * Get a colored status symbol if terminal supports ANSI colors.
     * 
     * @param status the watch status
     * @param style the visualization style
     * @return the symbol with ANSI color codes if supported
     */
    public static String getColoredSymbol(WatchStatus status, VisualizationStyle style) {
        if (!TerminalCapabilities.supportsAnsiColors()) {
            return getSymbol(status, style);
        }
        
        String symbol = getSymbol(status, style);
        String colorCode = getColorCode(status);
        
        return colorCode + symbol + AnsiColors.RESET;
    }
    
    /**
     * Get the ANSI color code for a status.
     */
    private static String getColorCode(WatchStatus status) {
        if (status == null) {
            return AnsiColors.GRAY;
        }
        
        switch (status) {
            case CONFIRMED:
                return AnsiColors.GREEN;
            case FAILED:
            case REBUILDING:
                return AnsiColors.RED;
            case CANCELLED:
            case RETRYING:
                return AnsiColors.YELLOW;
            case SUBMITTED:
            case WATCHING:
            case BUILDING:
                return AnsiColors.CYAN;
            case PENDING:
            default:
                return AnsiColors.GRAY;
        }
    }
    
    /**
     * ANSI color codes for terminal output.
     */
    private static class AnsiColors {
        static final String RESET = "\u001B[0m";
        static final String RED = "\u001B[31m";
        static final String GREEN = "\u001B[32m";
        static final String YELLOW = "\u001B[33m";
        static final String CYAN = "\u001B[36m";
        static final String GRAY = "\u001B[90m";
    }
}
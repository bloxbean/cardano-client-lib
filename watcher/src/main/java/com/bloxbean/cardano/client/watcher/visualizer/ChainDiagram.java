package com.bloxbean.cardano.client.watcher.visualizer;

/**
 * Builder class for constructing chain visualization diagrams.
 *
 * This class provides utilities for drawing boxes, connectors,
 * progress bars, and other visual elements that make up the
 * chain visualization.
 */
public class ChainDiagram {

    private final StringBuilder diagram;
    private final VisualizationStyle style;
    private final int width;

    // Box drawing characters for different styles
    private static class BoxChars {
        final String topLeft, topRight, bottomLeft, bottomRight;
        final String horizontal, vertical;
        final String connector;

        BoxChars(String tl, String tr, String bl, String br, String h, String v, String c) {
            this.topLeft = tl;
            this.topRight = tr;
            this.bottomLeft = bl;
            this.bottomRight = br;
            this.horizontal = h;
            this.vertical = v;
            this.connector = c;
        }
    }

    private static final BoxChars ASCII_BOX = new BoxChars(
        "+", "+", "+", "+", "-", "|", "-->"
    );

    private static final BoxChars UNICODE_BOX = new BoxChars(
        "┌", "┐", "└", "┘", "─", "│", "───▶"
    );

    private static final BoxChars UNICODE_DOUBLE_BOX = new BoxChars(
        "╔", "╗", "╚", "╝", "═", "║", "═══▶"
    );

    /**
     * Create a new ChainDiagram builder.
     *
     * @param style the visualization style to use
     * @param width the maximum width for the diagram
     */
    public ChainDiagram(VisualizationStyle style, int width) {
        this.diagram = new StringBuilder();
        this.style = style;
        this.width = width;
    }

    /**
     * Create a new ChainDiagram with default width.
     *
     * @param style the visualization style to use
     */
    public ChainDiagram(VisualizationStyle style) {
        this(style, TerminalCapabilities.getTerminalWidth());
    }

    /**
     * Draw a box with title and content.
     *
     * @param title the box title
     * @param content the box content (can be multi-line)
     * @param boxWidth the width of the box
     * @return this diagram for chaining
     */
    public ChainDiagram drawBox(String title, String content, int boxWidth) {
        BoxChars chars = getBoxChars(false);

        // Ensure minimum box width
        boxWidth = Math.max(boxWidth, title != null ? title.length() + 4 : 10);
        boxWidth = Math.min(boxWidth, width);

        // Top border
        diagram.append(chars.topLeft);
        if (title != null && !title.isEmpty()) {
            String paddedTitle = " " + title + " ";
            int remaining = boxWidth - paddedTitle.length() - 2;
            int leftPad = remaining / 2;
            int rightPad = remaining - leftPad;

            for (int i = 0; i < leftPad; i++) {
                diagram.append(chars.horizontal);
            }
            diagram.append(paddedTitle);
            for (int i = 0; i < rightPad; i++) {
                diagram.append(chars.horizontal);
            }
        } else {
            for (int i = 0; i < boxWidth - 2; i++) {
                diagram.append(chars.horizontal);
            }
        }
        diagram.append(chars.topRight).append("\n");

        // Content lines
        if (content != null && !content.isEmpty()) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                diagram.append(chars.vertical);
                diagram.append(padOrTruncate(line, boxWidth - 2));
                diagram.append(chars.vertical).append("\n");
            }
        }

        // Bottom border
        diagram.append(chars.bottomLeft);
        for (int i = 0; i < boxWidth - 2; i++) {
            diagram.append(chars.horizontal);
        }
        diagram.append(chars.bottomRight).append("\n");

        return this;
    }

    /**
     * Draw a header box with double lines (if supported).
     *
     * @param title the main title
     * @param subtitle optional subtitle
     * @return this diagram for chaining
     */
    public ChainDiagram drawHeader(String title, String subtitle) {
        BoxChars chars = getBoxChars(true);
        int boxWidth = Math.min(width, 60);

        // Top border
        diagram.append(chars.topLeft);
        for (int i = 0; i < boxWidth - 2; i++) {
            diagram.append(chars.horizontal);
        }
        diagram.append(chars.topRight).append("\n");

        // Title
        diagram.append(chars.vertical);
        diagram.append(center(title, boxWidth - 2));
        diagram.append(chars.vertical).append("\n");

        // Subtitle if provided
        if (subtitle != null && !subtitle.isEmpty()) {
            diagram.append(chars.vertical);
            diagram.append(center(subtitle, boxWidth - 2));
            diagram.append(chars.vertical).append("\n");
        }

        // Bottom border
        diagram.append(chars.bottomLeft);
        for (int i = 0; i < boxWidth - 2; i++) {
            diagram.append(chars.horizontal);
        }
        diagram.append(chars.bottomRight).append("\n");

        return this;
    }

    /**
     * Draw a progress bar.
     *
     * @param progress the progress value (0.0 to 1.0)
     * @param barWidth the width of the progress bar
     * @return this diagram for chaining
     */
    public ChainDiagram drawProgressBar(double progress, int barWidth) {
        progress = Math.max(0, Math.min(1, progress)); // Clamp to 0-1

        int filled = (int) (progress * barWidth);
        int empty = barWidth - filled;

        diagram.append("[");

        // Use different characters based on style
        String fillChar = style == VisualizationStyle.UNICODE_BOX ? "█" : "=";
        String emptyChar = style == VisualizationStyle.UNICODE_BOX ? "░" : "-";

        for (int i = 0; i < filled; i++) {
            diagram.append(fillChar);
        }
        for (int i = 0; i < empty; i++) {
            diagram.append(emptyChar);
        }

        diagram.append("] ");
        diagram.append(String.format("%.1f%%", progress * 100));

        return this;
    }

    /**
     * Draw a connector between steps.
     *
     * @return this diagram for chaining
     */
    public ChainDiagram drawConnector() {
        BoxChars chars = getBoxChars(false);
        diagram.append(" ").append(chars.connector).append(" ");
        return this;
    }

    /**
     * Draw a horizontal line separator.
     *
     * @param width the width of the line
     * @return this diagram for chaining
     */
    public ChainDiagram drawSeparator(int width) {
        BoxChars chars = getBoxChars(false);
        for (int i = 0; i < width; i++) {
            diagram.append(chars.horizontal);
        }
        diagram.append("\n");
        return this;
    }

    /**
     * Add a new line to the diagram.
     *
     * @return this diagram for chaining
     */
    public ChainDiagram newLine() {
        diagram.append("\n");
        return this;
    }

    /**
     * Add text with optional indentation.
     *
     * @param text the text to add
     * @param indent the number of spaces to indent
     * @return this diagram for chaining
     */
    public ChainDiagram addText(String text, int indent) {
        for (int i = 0; i < indent; i++) {
            diagram.append(" ");
        }
        diagram.append(text);
        return this;
    }

    /**
     * Add centered text.
     *
     * @param text the text to center
     * @return this diagram for chaining
     */
    public ChainDiagram addCenteredText(String text) {
        diagram.append(center(text, width));
        return this;
    }

    /**
     * Build the final diagram string.
     *
     * @return the complete diagram
     */
    public String build() {
        return diagram.toString();
    }

    // Helper methods

    private BoxChars getBoxChars(boolean doubleLines) {
        if (style == VisualizationStyle.UNICODE_BOX && doubleLines) {
            return UNICODE_DOUBLE_BOX;
        } else if (style == VisualizationStyle.UNICODE_BOX) {
            return UNICODE_BOX;
        } else {
            return ASCII_BOX;
        }
    }

    private String center(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) {
            return text.substring(0, width);
        }

        int totalPadding = width - text.length();
        int leftPadding = totalPadding / 2;
        int rightPadding = totalPadding - leftPadding;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < leftPadding; i++) {
            result.append(" ");
        }
        result.append(text);
        for (int i = 0; i < rightPadding; i++) {
            result.append(" ");
        }
        return result.toString();
    }

    private String padOrTruncate(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) {
            return text.substring(0, width);
        } else {
            // Always pad with a leading space and fill to width
            return String.format(" %-" + (width - 1) + "s", text);
        }
    }
}

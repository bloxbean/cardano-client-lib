package com.bloxbean.cardano.client.watcher.visualizer.renderers;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.visualizer.model.*;

import java.util.List;

/**
 * SVG renderer that converts ChainVisualizationModel to SVG format.
 * 
 * This renderer demonstrates the power of the abstraction layer by producing
 * high-quality vector graphics suitable for web embedding or documentation.
 */
public class SvgChainRenderer {
    
    private static final int BOX_WIDTH = 120;
    private static final int BOX_HEIGHT = 80;
    private static final int BOX_SPACING = 40;
    private static final int MARGIN = 20;
    
    /**
     * Render chain structure as SVG
     */
    public static String renderStructure(ChainVisualizationModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }
        
        List<StepVisualizationModel> steps = model.getSteps();
        int totalHeight = MARGIN * 2 + steps.size() * (BOX_HEIGHT + BOX_SPACING) - BOX_SPACING;
        int totalWidth = BOX_WIDTH + MARGIN * 2;
        
        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
           .append("width=\"").append(totalWidth).append("\" ")
           .append("height=\"").append(totalHeight).append("\" ")
           .append("viewBox=\"0 0 ").append(totalWidth).append(" ").append(totalHeight).append("\">\n");
        
        // Add styles
        svg.append("<defs>\n");
        svg.append("  <style type=\"text/css\"><![CDATA[\n");
        svg.append("    .step-box { fill: #f0f0f0; stroke: #333; stroke-width: 2; }\n");
        svg.append("    .step-box-completed { fill: #d4edda; stroke: #28a745; stroke-width: 2; }\n");
        svg.append("    .step-box-failed { fill: #f8d7da; stroke: #dc3545; stroke-width: 2; }\n");
        svg.append("    .step-box-running { fill: #fff3cd; stroke: #ffc107; stroke-width: 2; }\n");
        svg.append("    .step-text { font-family: Arial, sans-serif; font-size: 12px; fill: #333; }\n");
        svg.append("    .step-title { font-family: Arial, sans-serif; font-size: 14px; font-weight: bold; fill: #333; }\n");
        svg.append("    .chain-title { font-family: Arial, sans-serif; font-size: 16px; font-weight: bold; fill: #333; }\n");
        svg.append("    .connector { stroke: #666; stroke-width: 2; marker-end: url(#arrowhead); }\n");
        svg.append("  ]]></style>\n");
        
        // Add arrow marker
        svg.append("  <marker id=\"arrowhead\" markerWidth=\"10\" markerHeight=\"7\" ")
           .append("refX=\"9\" refY=\"3.5\" orient=\"auto\">\n");
        svg.append("    <polygon points=\"0 0, 10 3.5, 0 7\" fill=\"#666\" />\n");
        svg.append("  </marker>\n");
        svg.append("</defs>\n");
        
        // Add title
        ChainVisualizationModel.ChainMetadata metadata = model.getMetadata();
        svg.append("<text x=\"").append(totalWidth / 2).append("\" y=\"15\" ")
           .append("text-anchor=\"middle\" class=\"chain-title\">")
           .append(escapeXml(metadata.getChainId()))
           .append("</text>\n");
        
        // Render steps
        int y = MARGIN + 25;
        for (int i = 0; i < steps.size(); i++) {
            StepVisualizationModel step = steps.get(i);
            
            // Step box
            String boxClass = getStepBoxClass(step.getStatus());
            svg.append("<rect x=\"").append(MARGIN).append("\" y=\"").append(y)
               .append("\" width=\"").append(BOX_WIDTH).append("\" height=\"").append(BOX_HEIGHT)
               .append("\" class=\"").append(boxClass).append("\" rx=\"5\" />\n");
            
            // Step title
            svg.append("<text x=\"").append(MARGIN + BOX_WIDTH / 2).append("\" y=\"").append(y + 20)
               .append("\" text-anchor=\"middle\" class=\"step-title\">")
               .append(escapeXml(step.getStepId()))
               .append("</text>\n");
            
            // Step description
            if (step.getDescription() != null) {
                String[] descLines = wrapText(step.getDescription(), 15);
                for (int lineIndex = 0; lineIndex < Math.min(descLines.length, 2); lineIndex++) {
                    svg.append("<text x=\"").append(MARGIN + BOX_WIDTH / 2).append("\" y=\"")
                       .append(y + 35 + lineIndex * 12).append("\" text-anchor=\"middle\" class=\"step-text\">")
                       .append(escapeXml(descLines[lineIndex]))
                       .append("</text>\n");
                }
            }
            
            // Status indicator
            svg.append("<text x=\"").append(MARGIN + BOX_WIDTH / 2).append("\" y=\"").append(y + 65)
               .append("\" text-anchor=\"middle\" class=\"step-text\">")
               .append(getStatusText(step.getStatus()))
               .append("</text>\n");
            
            // Connector to next step
            if (i < steps.size() - 1) {
                int lineY = y + BOX_HEIGHT + BOX_SPACING / 2;
                svg.append("<line x1=\"").append(MARGIN + BOX_WIDTH / 2).append("\" y1=\"").append(y + BOX_HEIGHT)
                   .append("\" x2=\"").append(MARGIN + BOX_WIDTH / 2).append("\" y2=\"").append(lineY + BOX_SPACING / 2)
                   .append("\" class=\"connector\" />\n");
            }
            
            y += BOX_HEIGHT + BOX_SPACING;
        }
        
        svg.append("</svg>");
        return svg.toString();
    }
    
    /**
     * Render progress visualization as SVG
     */
    public static String renderProgress(ChainVisualizationModel model) {
        // For now, delegate to structure rendering with progress colors
        // In a full implementation, this would show real-time progress bars
        return renderStructure(model);
    }
    
    /**
     * Render UTXO flow as SVG
     */
    public static String renderUtxoFlow(ChainVisualizationModel model) {
        if (model == null || model.getUtxoFlow() == null) {
            return renderStructure(model); // Fallback to structure view
        }
        
        // In a full implementation, this would render a node-link diagram
        // showing UTXO flows between steps with proper graph layout
        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"400\" height=\"200\" viewBox=\"0 0 400 200\">\n");
        svg.append("<text x=\"200\" y=\"100\" text-anchor=\"middle\" fill=\"#666\" font-family=\"Arial, sans-serif\">")
           .append("UTXO Flow Diagram (Full implementation pending)")
           .append("</text>\n");
        svg.append("</svg>");
        
        return svg.toString();
    }
    
    // Helper methods
    
    private static String getStepBoxClass(WatchStatus status) {
        if (status == null) return "step-box";
        
        switch (status) {
            case CONFIRMED:
                return "step-box-completed";
            case FAILED:
                return "step-box-failed";
            case WATCHING:
            case SUBMITTED:
            case RETRYING:
            case REBUILDING:
                return "step-box-running";
            default:
                return "step-box";
        }
    }
    
    private static String getStatusText(WatchStatus status) {
        if (status == null) return "PENDING";
        
        switch (status) {
            case CONFIRMED: return "✓ CONFIRMED";
            case FAILED: return "✗ FAILED";
            case WATCHING: return "👁 WATCHING";
            case SUBMITTED: return "📤 SUBMITTED";
            case RETRYING: return "🔄 RETRYING";
            case REBUILDING: return "🔨 REBUILDING";
            case CANCELLED: return "⊘ CANCELLED";
            default: return status.name();
        }
    }
    
    private static String[] wrapText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return new String[]{text != null ? text : ""};
        }
        
        // Simple word wrapping
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxLength) {
                result.append(currentLine.toString()).append("\n");
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        
        if (currentLine.length() > 0) {
            result.append(currentLine.toString());
        }
        
        return result.toString().split("\n");
    }
    
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
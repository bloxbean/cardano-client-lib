package com.bloxbean.cardano.client.watcher.visualizer.renderers;

import com.bloxbean.cardano.client.watcher.visualizer.ChainDiagram;
import com.bloxbean.cardano.client.watcher.visualizer.StatusSymbols;
import com.bloxbean.cardano.client.watcher.visualizer.VisualizationStyle;
import com.bloxbean.cardano.client.watcher.visualizer.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * ASCII renderer that converts ChainVisualizationModel to ASCII art representation.
 *
 * This renderer consumes the abstract visualization model and produces ASCII/Unicode
 * text output. It replaces the direct domain object rendering in ChainVisualizer
 * with a clean model-based approach.
 */
public class AsciiChainRenderer {

    private static final int DEFAULT_BOX_WIDTH = 16;

    /**
     * Render chain structure visualization from model
     */
    public static String renderStructure(ChainVisualizationModel model, VisualizationStyle style) {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }

        ChainDiagram diagram = new ChainDiagram(style);

        // Header with chain metadata
        ChainVisualizationModel.ChainMetadata metadata = model.getMetadata();
        String title = "Chain: " + metadata.getChainId();
        String subtitle = "Steps: " + metadata.getTotalSteps() +
                         (metadata.getDescription() != null ? " | " + metadata.getDescription() : "");
        diagram.drawHeader(title, subtitle).newLine();

        List<StepVisualizationModel> steps = model.getSteps();

        if (style == VisualizationStyle.COMPACT) {
            return renderStructureCompact(steps, diagram);
        } else {
            return renderStructureDetailed(steps, diagram);
        }
    }

    /**
     * Render chain progress visualization from model
     */
    public static String renderProgress(ChainVisualizationModel model, VisualizationStyle style) {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }

        ChainDiagram diagram = new ChainDiagram(style);

        // Header with progress information
        ChainVisualizationModel.ChainMetadata metadata = model.getMetadata();
        ExecutionStateModel execution = model.getExecution();

        String title = "Chain: " + metadata.getChainId();
        String subtitle = "Progress: ";
        diagram.drawHeader(title, subtitle);

        // Progress bar
        diagram.addText("", 4);
        diagram.drawProgressBar(execution.getProgress(), 20);

        // Add timing information
        if (execution.getTotalDuration() != null) {
            diagram.addText(" | ⏱ " + formatDuration(execution.getTotalDuration()), 0);
        } else if (execution.getStartedAt() != null) {
            Duration elapsed = Duration.between(execution.getStartedAt(), Instant.now());
            diagram.addText(" | ⏱ " + formatDuration(elapsed), 0);
        }

        diagram.newLine().newLine();

        // Step statuses
        List<StepVisualizationModel> steps = model.getSteps();

        if (style == VisualizationStyle.COMPACT) {
            return renderProgressCompact(steps, diagram, style);
        } else {
            return renderProgressDetailed(steps, diagram, style);
        }
    }

    /**
     * Render UTXO flow visualization from model
     */
    public static String renderUtxoFlow(ChainVisualizationModel model, VisualizationStyle style) {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }

        ChainDiagram diagram = new ChainDiagram(style);
        diagram.drawHeader("UTXO Flow Diagram", null).newLine();

        UtxoFlowModel utxoFlow = model.getUtxoFlow();
        if (utxoFlow == null || utxoFlow.getNodes() == null || utxoFlow.getNodes().isEmpty()) {
            diagram.addCenteredText("No UTXO flow data available yet").newLine();
        } else {
            // Render UTXO nodes
            List<UtxoFlowModel.UtxoNode> nodes = utxoFlow.getNodes();
            for (int i = 0; i < nodes.size(); i++) {
                UtxoFlowModel.UtxoNode node = nodes.get(i);

                // Use the name which may include transaction hash
                String boxContent = node.getName() != null ? node.getName() : node.getStepId();
                
                // Add value information if available
                if (node.getInputValue() != null) {
                    boxContent += "\nIn: " + node.getInputValue();
                }
                if (node.getOutputValue() != null) {
                    boxContent += "\nOut: " + node.getOutputValue();
                }

                diagram.drawBox(node.getStepId(), boxContent, DEFAULT_BOX_WIDTH);

                // Add connector if not last node
                if (i < nodes.size() - 1) {
                    diagram.drawConnector();
                }
            }
        }

        return diagram.build();
    }

    // Private rendering methods

    private static String renderStructureCompact(List<StepVisualizationModel> steps, ChainDiagram diagram) {
        // Simple horizontal layout for compact view
        for (int i = 0; i < steps.size(); i++) {
            StepVisualizationModel step = steps.get(i);
            String stepInfo = step.getStepId();

            if (step.getDependencies() != null && !step.getDependencies().isEmpty()) {
                stepInfo += "*"; // Mark steps with dependencies
            }

            diagram.addText("[" + stepInfo + "]", 0);

            if (i < steps.size() - 1) {
                diagram.addText(" -> ", 0);
            }
        }
        diagram.newLine();

        // Legend
        diagram.newLine().addText("* = Has UTXO dependencies", 0);

        return diagram.build();
    }

    private static String renderStructureDetailed(List<StepVisualizationModel> steps, ChainDiagram diagram) {
        // Draw steps as boxes with connectors
        for (int i = 0; i < steps.size(); i++) {
            StepVisualizationModel step = steps.get(i);

            String content = step.getDescription() != null ? step.getDescription() : "Transaction step";
            List<String> dependencies = step.getDependencies();

            if (dependencies != null && !dependencies.isEmpty()) {
                content += "\nDependencies: " + dependencies.size();
                for (String dep : dependencies) {
                    content += "\n • " + dep;
                    // Truncate long dependency lists
                    if (content.length() > 100) {
                        content += "\n • ...";
                        break;
                    }
                }
            }

            diagram.drawBox(step.getStepId(), content, 20);

            if (i < steps.size() - 1) {
                diagram.newLine().addCenteredText("│").newLine().addCenteredText("▼").newLine();
            }
        }

        return diagram.build();
    }

    private static String renderProgressCompact(List<StepVisualizationModel> steps,
                                              ChainDiagram diagram,
                                              VisualizationStyle style) {
        // Single line status
        for (int i = 0; i < steps.size(); i++) {
            StepVisualizationModel step = steps.get(i);

            String symbol = StatusSymbols.getColoredSymbol(step.getStatus(), VisualizationStyle.COMPACT);
            diagram.addText("[" + step.getStepId() + ":" + symbol + "]", 0);

            if (i < steps.size() - 1) {
                diagram.addText(" ", 0);
            }
        }

        return diagram.build();
    }

    private static String renderProgressDetailed(List<StepVisualizationModel> steps,
                                               ChainDiagram diagram,
                                               VisualizationStyle style) {
        boolean first = true;

        for (StepVisualizationModel step : steps) {
            if (!first) {
                diagram.drawConnector();
            }
            first = false;

            // Step box content
            StringBuilder content = new StringBuilder();
            String symbol = StatusSymbols.getColoredSymbol(step.getStatus(), style);
            content.append(symbol).append("\n");
            content.append(StatusSymbols.getSymbol(step.getStatus(), VisualizationStyle.DETAILED));

            // Add transaction info if available
            if (step.getTransaction() != null && step.getTransaction().getHash() != null) {
                content.append("\nTx: ").append(truncateHash(step.getTransaction().getHash()));
            }

            // Add execution info if available
            if (step.getExecution() != null) {
                StepVisualizationModel.StepExecutionModel execution = step.getExecution();
                if (execution.getCompletedAt() != null) {
                    content.append("\nCompleted");
                }
                if (execution.getDuration() != null) {
                    content.append("\nDuration: ").append(formatDuration(execution.getDuration()));
                }
            }

            // Add error info if available
            if (step.getError() != null) {
                String errorMsg = step.getError().getMessage();
                if (errorMsg != null && errorMsg.length() > 20) {
                    errorMsg = errorMsg.substring(0, 17) + "...";
                }
                content.append("\nError: ").append(errorMsg);
            }

            diagram.drawBox(step.getStepId(), content.toString(), DEFAULT_BOX_WIDTH);
        }

        return diagram.build();
    }

    // Utility methods

    private static String truncateHash(String hash) {
        if (hash == null) return "null";
        return hash.length() > 8 ? hash.substring(0, 8) + ".." : hash;
    }

    private static String formatDuration(Duration duration) {
        if (duration == null) return "0s";

        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "." + (duration.toMillis() % 1000) / 100 + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
    }
}

package com.bloxbean.cardano.client.watcher.visualizer;

import com.bloxbean.cardano.client.watcher.api.WatchHandle;
import com.bloxbean.cardano.client.watcher.chain.BasicWatchHandle;
import com.bloxbean.cardano.client.watcher.chain.Watcher;
import com.bloxbean.cardano.client.watcher.visualizer.extraction.ChainModelExtractor;
import com.bloxbean.cardano.client.watcher.visualizer.json.JsonSerializer;
import com.bloxbean.cardano.client.watcher.visualizer.model.ChainVisualizationModel;
import com.bloxbean.cardano.client.watcher.visualizer.renderers.AsciiChainRenderer;
import com.bloxbean.cardano.client.watcher.visualizer.renderers.SvgChainRenderer;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;

/**
 * Main class for visualizing transaction chains.
 *
 * Provides static methods for generating ASCII art representations
 * of chain structure, execution progress, and UTXO dependencies.
 */
public class ChainVisualizer {

    private static final int DEFAULT_BOX_WIDTH = 16;
    private static final int DEFAULT_REFRESH_INTERVAL_MS = 1000;

    /**
     * Visualize the static structure of a chain before execution.
     *
     * @param builder the chain builder containing the steps
     * @return ASCII art representation of the chain structure
     */
    public static String visualizeStructure(Watcher.WatcherBuilder builder) {
        return visualizeStructure(builder, TerminalCapabilities.getBestStyle());
    }

    /**
     * Visualize the static structure of a chain with specific style.
     *
     * @param builder the chain builder containing the steps
     * @param style the visualization style to use
     * @return ASCII art representation of the chain structure
     */
    public static String visualizeStructure(Watcher.WatcherBuilder builder, VisualizationStyle style) {
        // Use new abstraction layer approach
        ChainVisualizationModel model = ChainModelExtractor.extractModel(builder);
        return AsciiChainRenderer.renderStructure(model, style);
    }

    /**
     * Visualize the current execution progress of a chain.
     *
     * @param handle the chain handle containing execution state
     * @return ASCII art representation of the current progress
     */
    public static String visualizeProgress(BasicWatchHandle handle) {
        return visualizeProgress(handle, TerminalCapabilities.getBestStyle());
    }

    /**
     * Visualize the current execution progress with specific style.
     *
     * @param handle the chain handle containing execution state
     * @param style the visualization style to use
     * @return ASCII art representation of the current progress
     */
    public static String visualizeProgress(BasicWatchHandle handle, VisualizationStyle style) {
        // Use new abstraction layer approach
        ChainVisualizationModel model = ChainModelExtractor.extractModel(handle);
        return AsciiChainRenderer.renderProgress(model, style);
    }

    /**
     * Visualize UTXO flow between steps.
     *
     * @param handle the chain handle
     * @return ASCII art representation of UTXO dependencies
     */
    public static String visualizeUtxoFlow(BasicWatchHandle handle) {
        return visualizeUtxoFlow(handle, TerminalCapabilities.getBestStyle());
    }

    /**
     * Visualize UTXO flow between steps with specific style.
     *
     * @param handle the chain handle
     * @param style the visualization style to use
     * @return ASCII art representation of UTXO dependencies
     */
    public static String visualizeUtxoFlow(BasicWatchHandle handle, VisualizationStyle style) {
        // Use new abstraction layer approach
        ChainVisualizationModel model = ChainModelExtractor.extractModel(handle);
        return AsciiChainRenderer.renderUtxoFlow(model, style);
    }

    /**
     * Start live monitoring of chain execution with console updates.
     *
     * @param handle the chain handle to monitor
     * @param output the output stream to write to
     */
    public static void startLiveMonitoring(WatchHandle handle, PrintStream output) {
        startLiveMonitoring(handle, output, DEFAULT_REFRESH_INTERVAL_MS);
    }

    /**
     * Start live monitoring with custom refresh interval.
     *
     * @param handle the chain handle to monitor
     * @param output the output stream to write to
     * @param refreshIntervalMs refresh interval in milliseconds
     */
    public static void startLiveMonitoring(WatchHandle handle, PrintStream output, int refreshIntervalMs) {
        if (!(handle instanceof BasicWatchHandle)) {
            output.println("Live monitoring not supported for this handle type");
            return;
        }

        BasicWatchHandle basicHandle = (BasicWatchHandle) handle;

        CompletableFuture.runAsync(() -> {
            try {
                while (!basicHandle.isCompleted()) {
                    // Clear screen (ANSI escape sequence)
                    if (TerminalCapabilities.supportsAnsiColors()) {
                        output.print("\033[2J\033[H");
                    } else {
                        // Just add some newlines if ANSI not supported
                        for (int i = 0; i < 3; i++) {
                            output.println();
                        }
                    }

                    String progress = visualizeProgress(basicHandle);
                    output.println(progress);
                    output.flush();

                    Thread.sleep(refreshIntervalMs);
                }

                // Final update
                String finalProgress = visualizeProgress(basicHandle);
                output.println("\n=== FINAL STATE ===");
                output.println(finalProgress);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                output.println("Monitoring interrupted");
            }
        });
    }

    // ================================
    // Abstraction Layer APIs
    // ================================

    /**
     * Export the abstract visualization model from a BasicWatchHandle.
     * This model can be used by external tools or different renderers.
     *
     * @param handle the chain handle
     * @return abstract visualization model
     */
    public static ChainVisualizationModel exportModel(BasicWatchHandle handle) {
        return ChainModelExtractor.extractModel(handle);
    }

    /**
     * Export the abstract visualization model from a WatcherBuilder.
     * This shows the planned chain structure before execution.
     *
     * @param builder the chain builder
     * @return abstract visualization model
     */
    public static ChainVisualizationModel exportModel(Watcher.WatcherBuilder builder) {
        return ChainModelExtractor.extractModel(builder);
    }

    /**
     * Export chain visualization data as JSON string.
     * This enables external tool integration and data persistence.
     *
     * @param handle the chain handle
     * @return JSON representation of chain visualization data
     */
    public static String exportJson(BasicWatchHandle handle) {
        ChainVisualizationModel model = exportModel(handle);
        return JsonSerializer.serializePretty(model);
    }

    /**
     * Export chain visualization data as JSON string from builder.
     *
     * @param builder the chain builder
     * @return JSON representation of chain visualization data
     */
    public static String exportJson(Watcher.WatcherBuilder builder) {
        ChainVisualizationModel model = exportModel(builder);
        return JsonSerializer.serializePretty(model);
    }

    /**
     * Export chain visualization data as compact JSON string.
     * Useful for transmission or storage where size matters.
     *
     * @param handle the chain handle
     * @return compact JSON representation
     */
    public static String exportJsonCompact(BasicWatchHandle handle) {
        ChainVisualizationModel model = exportModel(handle);
        return JsonSerializer.serializeCompact(model);
    }

    /**
     * Export chain visualization data as compact JSON string from builder.
     *
     * @param builder the chain builder
     * @return compact JSON representation
     */
    public static String exportJsonCompact(Watcher.WatcherBuilder builder) {
        ChainVisualizationModel model = exportModel(builder);
        return JsonSerializer.serializeCompact(model);
    }

    /**
     * Import chain visualization model from JSON string.
     * This enables external data integration and testing.
     *
     * @param json JSON string containing visualization data
     * @return deserialized chain visualization model
     */
    public static ChainVisualizationModel importModel(String json) {
        return JsonSerializer.deserialize(json);
    }

    /**
     * Validate that a JSON string contains valid chain visualization data.
     *
     * @param json JSON string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidJson(String json) {
        return JsonSerializer.isValid(json);
    }

    // ================================
    // Future Extension Points
    // ================================

    /**
     * Export chain visualization as SVG format.
     * Produces high-quality vector graphics suitable for web embedding or documentation.
     *
     * @param handle the chain handle
     * @return SVG representation of the chain
     */
    public static String exportSvg(BasicWatchHandle handle) {
        ChainVisualizationModel model = exportModel(handle);
        return SvgChainRenderer.renderStructure(model);
    }

    /**
     * Export chain visualization as SVG format from builder.
     *
     * @param builder the chain builder
     * @return SVG representation of the chain
     */
    public static String exportSvg(Watcher.WatcherBuilder builder) {
        ChainVisualizationModel model = exportModel(builder);
        return SvgChainRenderer.renderStructure(model);
    }

    // Note: Old private helper methods have been moved to AsciiChainRenderer
    // This refactoring supports the new abstraction layer architecture
}

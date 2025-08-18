package com.bloxbean.cardano.client.watcher.visualizer;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.chain.BasicWatchHandle;

/**
 * Demo class to show what the chain visualizer can produce.
 * This is not a unit test but a demonstration program.
 */
public class ChainVisualizerDemo {

    public static void main(String[] args) {
        System.out.println("🎨 === CARDANO CHAIN VISUALIZER DEMO ===\n");

        // Demo 1: Simple ASCII Box Drawing
        System.out.println("📋 Demo 1: Chain Diagram Components");
        ChainDiagram diagram = new ChainDiagram(VisualizationStyle.SIMPLE_ASCII);
        diagram.drawHeader("DeFi Yield Farming Chain", "3 Steps | Status: Executing")
               .newLine()
               .drawBox("deposit", "Send 100 ADA\nto liquidity pool", 16)
               .newLine()
               .addCenteredText("|")
               .newLine()
               .addCenteredText("v")
               .newLine()
               .drawBox("stake", "Stake LP tokens\nfrom step 1", 16)
               .newLine()
               .addCenteredText("|")
               .newLine()
               .addCenteredText("v")
               .newLine()
               .drawBox("harvest", "Claim rewards\n(conditional)", 16);

        System.out.println(diagram.build());

        // Demo 2: Unicode Box Drawing
        System.out.println("📋 Demo 2: Unicode Chain Structure");
        ChainDiagram unicodeDiagram = new ChainDiagram(VisualizationStyle.UNICODE_BOX);
        unicodeDiagram.drawHeader("Payment Chain", "Processing...")
                      .newLine()
                      .drawBox("payment-1", "1.5 ADA → Alice", 15);
        unicodeDiagram.drawConnector();
        unicodeDiagram.drawBox("payment-2", "2.0 ADA → Bob", 15);
        unicodeDiagram.drawConnector();
        unicodeDiagram.drawBox("payment-3", "0.5 ADA → Charlie", 15);

        System.out.println(unicodeDiagram.build());

        // Demo 3: Progress Visualization
        System.out.println("📊 Demo 3: Progress Visualization");

        // Create a mock handle for progress demo
        BasicWatchHandle mockHandle = createMockHandle();

        String progressDemo = ChainVisualizer.visualizeProgress(mockHandle, VisualizationStyle.DETAILED);
        System.out.println(progressDemo);

        // Demo 4: Different Status Symbols
        System.out.println("📋 Demo 4: Status Symbol Styles");
        System.out.println("ASCII Style:");
        for (WatchStatus status : WatchStatus.values()) {
            String symbol = StatusSymbols.getSymbol(status, VisualizationStyle.SIMPLE_ASCII);
            System.out.println("  " + status + ": " + symbol);
        }

        System.out.println("\nUnicode Style:");
        for (WatchStatus status : WatchStatus.values()) {
            String symbol = StatusSymbols.getSymbol(status, VisualizationStyle.UNICODE_BOX);
            System.out.println("  " + status + ": " + symbol);
        }

        // Demo 5: Progress Bars
        System.out.println("\n📊 Demo 5: Progress Bars");
        ChainDiagram progressDemo1 = new ChainDiagram(VisualizationStyle.SIMPLE_ASCII);
        progressDemo1.addText("ASCII Style: ", 0).drawProgressBar(0.25, 20).newLine()
                     .addText("            ", 0).drawProgressBar(0.50, 20).newLine()
                     .addText("            ", 0).drawProgressBar(0.75, 20).newLine()
                     .addText("            ", 0).drawProgressBar(1.00, 20);
        System.out.println(progressDemo1.build());

        ChainDiagram progressDemo2 = new ChainDiagram(VisualizationStyle.UNICODE_BOX);
        progressDemo2.addText("Unicode Style: ", 0).drawProgressBar(0.33, 20).newLine()
                     .addText("               ", 0).drawProgressBar(0.66, 20).newLine()
                     .addText("               ", 0).drawProgressBar(1.00, 20);
        System.out.println(progressDemo2.build());

        System.out.println("\n🎨 === END DEMO ===");

        // Demo 6: Terminal Capabilities
        System.out.println("\n🖥  Demo 6: Terminal Capabilities Detection");
        System.out.println("Unicode Support: " + TerminalCapabilities.supportsUnicode());
        System.out.println("ANSI Colors: " + TerminalCapabilities.supportsAnsiColors());
        System.out.println("Terminal Width: " + TerminalCapabilities.getTerminalWidth());
        System.out.println("Output Redirected: " + TerminalCapabilities.isOutputRedirected());
        System.out.println("Best Style: " + TerminalCapabilities.getBestStyle());
    }

    private static BasicWatchHandle createMockHandle() {
        BasicWatchHandle handle = new BasicWatchHandle("demo-chain", 3, "Demo chain for progress visualization");

        // Simulate execution progress
        handle.updateStepStatus("deposit", WatchStatus.CONFIRMED);
        handle.updateStepStatus("stake", WatchStatus.WATCHING);
        handle.updateStepStatus("harvest", WatchStatus.PENDING);

        // Add some step results
        // This would normally be done by the execution system
        // For demo purposes, we'll simulate it

        return handle;
    }
}

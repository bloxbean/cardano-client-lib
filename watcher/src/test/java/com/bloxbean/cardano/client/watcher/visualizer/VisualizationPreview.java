package com.bloxbean.cardano.client.watcher.visualizer;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;

/**
 * Simple preview of what the visualization output should look like.
 * Run this to see the expected format.
 */
public class VisualizationPreview {

    public static void main(String[] args) {
        System.out.println("🎨 === VISUALIZATION PREVIEW ===\n");

        // Show what a chain structure visualization should look like
        System.out.println("📊 Chain Structure Visualization (SIMPLE_ASCII):");
        showExpectedStructureOutput();

        System.out.println("\n📊 Chain Structure Visualization (UNICODE_BOX):");
        showExpectedUnicodeOutput();

        System.out.println("\n📈 Progress Visualization (COMPACT):");
        showExpectedProgressOutput();

        System.out.println("\n📈 Progress Visualization (DETAILED):");
        showExpectedDetailedOutput();

        System.out.println("\n🎨 === END PREVIEW ===");
    }

    private static void showExpectedStructureOutput() {
        System.out.println("+======================================+");
        System.out.println("| Chain: mvp-demo-chain               |");
        System.out.println("| Steps: 2 | MVP Demo: Two-step...   |");
        System.out.println("+======================================+");
        System.out.println();
        System.out.println("+----------------+");
        System.out.println("| mvp-demo       |");
        System.out.println("| MVP Demo: Comp |");
        System.out.println("| lex transaction|");
        System.out.println("| Dependencies: 1|");
        System.out.println("| • imaginary... |");
        System.out.println("+----------------+");
        System.out.println("        |");
        System.out.println("        ▼");
        System.out.println("+----------------+");
        System.out.println("| followup       |");
        System.out.println("| Follow-up tran |");
        System.out.println("| saction using  |");
        System.out.println("| Dependencies: 1|");
        System.out.println("| • mvp-demo     |");
        System.out.println("+----------------+");
    }

    private static void showExpectedUnicodeOutput() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║ Chain: mvp-demo-chain               ║");
        System.out.println("║ Steps: 2 | MVP Demo: Two-step...   ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();
        System.out.println("┌────────────────┐");
        System.out.println("│ mvp-demo       │");
        System.out.println("│ MVP Demo: Comp │");
        System.out.println("│ lex transaction│");
        System.out.println("│ Dependencies: 1│");
        System.out.println("│ • imaginary... │");
        System.out.println("└────────────────┘");
        System.out.println("        │");
        System.out.println("        ▼");
        System.out.println("┌────────────────┐");
        System.out.println("│ followup       │");
        System.out.println("│ Follow-up tran │");
        System.out.println("│ saction using  │");
        System.out.println("│ Dependencies: 1│");
        System.out.println("│ • mvp-demo     │");
        System.out.println("└────────────────┘");
    }

    private static void showExpectedProgressOutput() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║ Chain: mvp-demo-chain               ║");
        System.out.println("║ Progress:                            ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println("    [          ] 0.0% | ⏱ 0.1s");
        System.out.println();
        System.out.println("[mvp-demo:.] [followup:.] ");
    }

    private static void showExpectedDetailedOutput() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║ Chain: mvp-demo-chain               ║");
        System.out.println("║ Progress:                            ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println("    [          ] 0.0% | ⏱ 0.1s");
        System.out.println();
        System.out.println("┌────────────────┐ ───▶ ┌────────────────┐");
        System.out.println("│ mvp-demo       │      │ followup       │");
        System.out.println("├────────────────┤      ├────────────────┤");
        System.out.println("│      ⏳        │      │      ⏳        │");
        System.out.println("│   PENDING      │      │   PENDING      │");
        System.out.println("└────────────────┘      └────────────────┘");
    }

    /**
     * Test actual visualization components
     */
    public static void testActualComponents() {
        System.out.println("\n🔧 Testing Actual Components:");

        // Test ChainDiagram
        ChainDiagram diagram = new ChainDiagram(VisualizationStyle.SIMPLE_ASCII);
        diagram.drawHeader("Test Chain", "2 steps")
               .newLine()
               .drawBox("step1", "First step", 15)
               .drawConnector()
               .drawBox("step2", "Second step", 15);

        System.out.println(diagram.build());

        // Test Status Symbols
        System.out.println("\nStatus Symbols:");
        for (WatchStatus status : WatchStatus.values()) {
            String asciiSymbol = StatusSymbols.getSymbol(status, VisualizationStyle.SIMPLE_ASCII);
            String unicodeSymbol = StatusSymbols.getSymbol(status, VisualizationStyle.UNICODE_BOX);
            System.out.println(status + ": " + asciiSymbol + " / " + unicodeSymbol);
        }

        // Test Progress Bar
        System.out.println("\nProgress Bars:");
        ChainDiagram progressTest = new ChainDiagram(VisualizationStyle.SIMPLE_ASCII);
        progressTest.drawProgressBar(0.0, 20).newLine()
                    .drawProgressBar(0.25, 20).newLine()
                    .drawProgressBar(0.50, 20).newLine()
                    .drawProgressBar(0.75, 20).newLine()
                    .drawProgressBar(1.0, 20);
        System.out.println(progressTest.build());
    }
}

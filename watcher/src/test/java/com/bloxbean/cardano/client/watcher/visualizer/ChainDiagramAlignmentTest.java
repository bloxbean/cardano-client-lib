package com.bloxbean.cardano.client.watcher.visualizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify the ChainDiagram alignment fix.
 * This specifically tests the padOrTruncate method that was recently fixed
 * to ensure proper box alignment in chain visualizations.
 */
public class ChainDiagramAlignmentTest {

    @Test
    @DisplayName("Test padOrTruncate method produces correct width")
    public void testPadOrTruncateWidth() {
        ChainDiagram diagram = new ChainDiagram(VisualizationStyle.SIMPLE_ASCII, 80);
        
        // Use reflection to test the private padOrTruncate method
        try {
            java.lang.reflect.Method padOrTruncate = ChainDiagram.class.getDeclaredMethod("padOrTruncate", String.class, int.class);
            padOrTruncate.setAccessible(true);
            
            int targetWidth = 20;
            
            // Test various input strings
            String[] testInputs = {
                "Short",
                "Medium length text",
                "This is a very long text that should be truncated completely",
                "",
                null,
                "Exactly20Characters!"
            };
            
            for (String input : testInputs) {
                String result = (String) padOrTruncate.invoke(diagram, input, targetWidth);
                
                assertEquals(targetWidth, result.length(), 
                    "padOrTruncate should always return string of exact target width. " +
                    "Input: '" + input + "', Expected length: " + targetWidth + 
                    ", Actual length: " + result.length() + ", Result: '" + result + "'");
            }
        } catch (Exception e) {
            fail("Failed to test padOrTruncate method: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test box drawing produces properly aligned content")
    public void testBoxAlignment() {
        ChainDiagram diagram = new ChainDiagram(VisualizationStyle.SIMPLE_ASCII, 80);
        
        String result = diagram
            .drawBox("Test Title", "Line 1\nLine 2\nLong line that should be handled correctly", 30)
            .build();
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // Check that the result contains expected box characters
        assertTrue(result.contains("+"), "Should contain ASCII box characters");
        assertTrue(result.contains("-"), "Should contain horizontal lines");
        assertTrue(result.contains("|"), "Should contain vertical lines");
        
        // Print for visual verification
        System.out.println("Box alignment test result:");
        System.out.println(result);
    }

    @Test
    @DisplayName("Test Unicode box drawing")
    public void testUnicodeBoxDrawing() {
        ChainDiagram diagram = new ChainDiagram(VisualizationStyle.UNICODE_BOX, 80);
        
        String result = diagram
            .drawBox("Unicode Test", "Content line 1\nContent line 2", 25)
            .build();
        
        assertNotNull(result);
        
        // Check for Unicode box characters
        assertTrue(result.contains("┌") || result.contains("┐") || 
                   result.contains("└") || result.contains("┘"), 
                   "Should contain Unicode box characters");
        
        System.out.println("Unicode box test result:");
        System.out.println(result);
    }

    @Test
    @DisplayName("Test complete chain visualization alignment")
    public void testCompleteChainVisualization() {
        ChainDiagram diagram = new ChainDiagram(VisualizationStyle.UNICODE_BOX, 100);
        
        String result = diagram
            .drawHeader("Transaction Chain Test", "Alignment Verification")
            .newLine()
            .drawBox("Step 1", "Initialize\nPrepare inputs\nValidate", 25)
            .drawConnector()
            .drawBox("Step 2", "Build transaction\nCalculate fees\nAdd metadata", 25)
            .drawConnector()
            .drawBox("Step 3", "Submit to network\nWait confirmation\nUpdate status", 25)
            .newLine()
            .addText("Progress: ", 0)
            .drawProgressBar(0.75, 30)
            .build();
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // Verify structure
        assertTrue(result.contains("Transaction Chain Test"), "Should contain header title");
        assertTrue(result.contains("Step 1"), "Should contain step boxes");
        assertTrue(result.contains("───▶") || result.contains("-->"), "Should contain connectors");
        assertTrue(result.contains("75.0%"), "Should contain progress percentage");
        
        System.out.println("Complete chain visualization:");
        System.out.println(result);
    }

    @Test
    @DisplayName("Test alignment consistency across different content lengths")
    public void testAlignmentConsistency() {
        ChainDiagram diagram = new ChainDiagram(VisualizationStyle.SIMPLE_ASCII, 80);
        
        // Test boxes with different content lengths to ensure consistent alignment
        String result = diagram
            .drawBox("Short", "A", 20)
            .drawBox("Medium", "Medium length content here", 20)  
            .drawBox("Long", "This is a very long line that should be truncated properly to fit", 20)
            .drawBox("Empty", "", 20)
            .build();
        
        assertNotNull(result);
        
        // Split into lines and check each box maintains consistent width
        String[] lines = result.split("\n");
        
        boolean foundBoxLines = false;
        for (String line : lines) {
            // Look for content lines (those with | at start and end)
            if (line.startsWith("|") && line.endsWith("|")) {
                assertEquals(20, line.length(), 
                    "All box lines should have consistent width. Line: '" + line + "'");
                foundBoxLines = true;
            }
        }
        
        assertTrue(foundBoxLines, "Should have found box content lines to verify");
        
        System.out.println("Alignment consistency test:");
        System.out.println(result);
    }
}
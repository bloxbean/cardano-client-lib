package com.bloxbean.cardano.statetrees.rocksdb.benchmarks;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Comprehensive benchmark suite for RocksDB modernization performance baselines.
 * 
 * <p>This benchmark suite runs all performance tests for the RocksDB modernization
 * project to establish baseline metrics before and after each phase of modernization.
 * It provides a standardized way to measure performance impact and ensure that
 * modernization improvements don't introduce performance regressions.</p>
 * 
 * <p><b>Benchmark Coverage:</b></p>
 * <ul>
 *   <li>Type-safe key operations (creation, conversion, comparison)</li>
 *   <li>Resource management (registration, cleanup, error handling)</li>
 *   <li>Exception handling (structured vs generic exceptions)</li>
 *   <li>Memory allocation patterns</li>
 * </ul>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Run complete baseline suite
 * RocksDbModernizationBenchmarkSuite.main(new String[]{});
 * 
 * // Or run programmatically
 * RocksDbModernizationBenchmarkSuite suite = new RocksDbModernizationBenchmarkSuite();
 * BenchmarkResults results = suite.runAllBenchmarks();
 * System.out.println("Overall performance impact: " + results.getSummary());
 * }</pre>
 * 
 * <p><b>Output Format:</b> Results are formatted in a standardized way that
 * can be easily compared across different modernization phases and tracked
 * over time for performance regression analysis.</p>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public class RocksDbModernizationBenchmarkSuite {
    
    private static final String BENCHMARK_VERSION = "1.0.0";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Runs the complete benchmark suite and returns aggregate results.
     * 
     * @return comprehensive benchmark results
     */
    public BenchmarkResults runAllBenchmarks() {
        System.out.println("=".repeat(60));
        System.out.println("RocksDB Modernization Benchmark Suite v" + BENCHMARK_VERSION);
        System.out.println("Started: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
        System.out.println("=".repeat(60));
        
        BenchmarkResults results = new BenchmarkResults();
        
        // Run key performance benchmarks
        System.out.println("\n1. Type-Safe Key System Performance");
        System.out.println("-".repeat(40));
        RocksDbKeyBenchmark keyBenchmark = new RocksDbKeyBenchmark();
        long keyBenchmarkStart = System.currentTimeMillis();
        keyBenchmark.runSimplePerformanceTest();
        results.addBenchmarkTime("KeySystem", System.currentTimeMillis() - keyBenchmarkStart);
        
        // Run resource management benchmarks
        System.out.println("\n2. Resource Management Performance");
        System.out.println("-".repeat(40));
        ResourceManagementBenchmark resourceBenchmark = new ResourceManagementBenchmark();
        long resourceBenchmarkStart = System.currentTimeMillis();
        resourceBenchmark.runSimplePerformanceTest();
        results.addBenchmarkTime("ResourceManagement", System.currentTimeMillis() - resourceBenchmarkStart);
        
        // Memory usage analysis
        System.out.println("\n3. Memory Usage Analysis");
        System.out.println("-".repeat(40));
        runMemoryAnalysis(results);
        
        // System information
        System.out.println("\n4. System Information");
        System.out.println("-".repeat(40));
        printSystemInfo(results);
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Benchmark Suite Completed");
        System.out.println("Total Duration: " + results.getTotalDuration() + " ms");
        System.out.println("=".repeat(60));
        
        return results;
    }
    
    /**
     * Analyzes memory usage patterns of modernized components.
     */
    private void runMemoryAnalysis(BenchmarkResults results) {
        Runtime runtime = Runtime.getRuntime();
        
        // Force garbage collection for baseline
        System.gc();
        Thread.yield();
        System.gc();
        
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("Baseline memory usage: " + formatBytes(baselineMemory));
        
        // Test key system memory usage
        System.out.print("Testing key system memory impact... ");
        long beforeKeys = runtime.totalMemory() - runtime.freeMemory();
        
        // Create a large number of keys to measure memory impact
        Object[] keys = new Object[10000];
        for (int i = 0; i < keys.length; i++) {
            byte[] hash = new byte[32];
            java.util.Arrays.fill(hash, (byte) i);
            
            if (i % 4 == 0) {
                keys[i] = com.bloxbean.cardano.statetrees.rocksdb.keys.NodeHashKey.of(hash);
            } else if (i % 4 == 1) {
                keys[i] = com.bloxbean.cardano.statetrees.rocksdb.keys.VersionKey.of(i);
            } else if (i % 4 == 2) {
                keys[i] = com.bloxbean.cardano.statetrees.rocksdb.keys.SpecialKey.of("test_" + i);
            } else {
                keys[i] = hash; // Raw bytes for comparison
            }
        }
        
        long afterKeys = runtime.totalMemory() - runtime.freeMemory();
        long keyMemoryDelta = afterKeys - beforeKeys;
        System.out.println("+" + formatBytes(keyMemoryDelta));
        results.addMemoryUsage("KeySystem", keyMemoryDelta);
        
        // Clean up for next test
        keys = null;
        System.gc();
        Thread.yield();
        
        // Test resource management memory usage
        System.out.print("Testing resource management memory impact... ");
        long beforeResources = runtime.totalMemory() - runtime.freeMemory();
        
        // Create resource managers to measure memory impact
        Object[] resourceManagers = new Object[1000];
        for (int i = 0; i < resourceManagers.length; i++) {
            com.bloxbean.cardano.statetrees.rocksdb.resources.RocksDbResources resources = 
                new com.bloxbean.cardano.statetrees.rocksdb.resources.RocksDbResources();
            
            // Register some mock resources
            for (int j = 0; j < 5; j++) {
                resources.register("resource_" + j, new MockAutoCloseable("test"));
            }
            
            resourceManagers[i] = resources;
        }
        
        long afterResources = runtime.totalMemory() - runtime.freeMemory();
        long resourceMemoryDelta = afterResources - beforeResources;
        System.out.println("+" + formatBytes(resourceMemoryDelta));
        results.addMemoryUsage("ResourceManagement", resourceMemoryDelta);
        
        // Cleanup
        for (Object rm : resourceManagers) {
            ((com.bloxbean.cardano.statetrees.rocksdb.resources.RocksDbResources) rm).close();
        }
        resourceManagers = null;
        System.gc();
    }
    
    /**
     * Prints system information relevant to benchmark results.
     */
    private void printSystemInfo(BenchmarkResults results) {
        Runtime runtime = Runtime.getRuntime();
        
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("Java Vendor: " + System.getProperty("java.vendor"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("Architecture: " + System.getProperty("os.arch"));
        System.out.println("Available Processors: " + runtime.availableProcessors());
        System.out.println("Max Memory: " + formatBytes(runtime.maxMemory()));
        System.out.println("Total Memory: " + formatBytes(runtime.totalMemory()));
        System.out.println("Free Memory: " + formatBytes(runtime.freeMemory()));
        
        results.setSystemInfo(System.getProperty("java.version"), 
                             System.getProperty("os.name"), 
                             runtime.availableProcessors());
    }
    
    /**
     * Formats byte count into human-readable string.
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Main method for running the benchmark suite standalone.
     */
    public static void main(String[] args) {
        RocksDbModernizationBenchmarkSuite suite = new RocksDbModernizationBenchmarkSuite();
        BenchmarkResults results = suite.runAllBenchmarks();
        
        // Save results for comparison with future runs
        System.out.println("\n" + results.getSummary());
    }
    
    /**
     * Simple AutoCloseable implementation for testing.
     */
    private static class MockAutoCloseable implements AutoCloseable {
        private final String name;
        
        public MockAutoCloseable(String name) {
            this.name = name;
        }
        
        @Override
        public void close() {
            // Mock cleanup
        }
    }
    
    /**
     * Container for benchmark results and analysis.
     */
    public static class BenchmarkResults {
        private final java.util.Map<String, Long> benchmarkTimes = new java.util.HashMap<>();
        private final java.util.Map<String, Long> memoryUsage = new java.util.HashMap<>();
        private String javaVersion;
        private String osName;
        private int processors;
        private final long startTime = System.currentTimeMillis();
        
        public void addBenchmarkTime(String name, long timeMs) {
            benchmarkTimes.put(name, timeMs);
        }
        
        public void addMemoryUsage(String component, long bytes) {
            memoryUsage.put(component, bytes);
        }
        
        public void setSystemInfo(String javaVersion, String osName, int processors) {
            this.javaVersion = javaVersion;
            this.osName = osName;
            this.processors = processors;
        }
        
        public long getTotalDuration() {
            return System.currentTimeMillis() - startTime;
        }
        
        public String getSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append("Benchmark Results Summary:\n");
            summary.append("- Java: ").append(javaVersion).append("\n");
            summary.append("- OS: ").append(osName).append("\n");
            summary.append("- CPUs: ").append(processors).append("\n");
            summary.append("- Duration: ").append(getTotalDuration()).append(" ms\n");
            
            if (!memoryUsage.isEmpty()) {
                summary.append("- Memory Impact:\n");
                memoryUsage.forEach((component, bytes) -> 
                    summary.append("  * ").append(component).append(": ").append(formatBytes(bytes)).append("\n"));
            }
            
            return summary.toString();
        }
    }
}
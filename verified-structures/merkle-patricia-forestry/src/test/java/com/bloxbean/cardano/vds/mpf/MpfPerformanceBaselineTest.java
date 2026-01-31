package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance baseline tests to track performance metrics before and after refactoring.
 * These tests establish timing baselines for critical operations.
 */
public class MpfPerformanceBaselineTest {
    private static final HashFunction HASH_FN = Blake2b256::digest;
    private TestNodeStore store;
    private MpfTrie trie;
    private SecureRandom random;

    @BeforeEach
    void setUp() {
        store = new TestNodeStore();
        trie = new MpfTrie(store);
        random = new SecureRandom();
        random.setSeed(42); // Fixed seed for reproducible results
    }

    @Test
    void baselineSequentialInserts() {
        int numOperations = 1000;
        List<byte[]> keys = generateRandomKeys(numOperations);
        List<byte[]> values = generateRandomValues(numOperations);

        long startTime = System.nanoTime();

        for (int i = 0; i < numOperations; i++) {
            trie.put(keys.get(i), values.get(i));
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        System.out.println("Baseline Sequential Inserts:");
        System.out.println("  Operations: " + numOperations);
        System.out.println("  Duration: " + durationMs + "ms");
        System.out.println("  Ops/sec: " + (numOperations * 1000L / Math.max(durationMs, 1)));
        System.out.println("  Avg per op: " + (durationMs * 1_000_000.0 / numOperations) + "ns");

        // Verify all data was stored correctly
        for (int i = 0; i < numOperations; i++) {
            assertArrayEquals(values.get(i), trie.get(keys.get(i)));
        }

        assertTrue(durationMs > 0, "Operation should take measurable time");
    }

    @Test
    void baselineRandomAccess() {
        // Pre-populate trie
        int numEntries = 1000;
        List<byte[]> keys = generateRandomKeys(numEntries);
        List<byte[]> values = generateRandomValues(numEntries);

        for (int i = 0; i < numEntries; i++) {
            trie.put(keys.get(i), values.get(i));
        }

        // Perform random gets
        int numReads = 5000;
        long startTime = System.nanoTime();

        for (int i = 0; i < numReads; i++) {
            int randomIndex = random.nextInt(numEntries);
            byte[] result = trie.get(keys.get(randomIndex));
            assertNotNull(result);
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        System.out.println("Baseline Random Access:");
        System.out.println("  Reads: " + numReads);
        System.out.println("  Duration: " + durationMs + "ms");
        System.out.println("  Reads/sec: " + (numReads * 1000L / Math.max(durationMs, 1)));
        System.out.println("  Avg per read: " + (durationMs * 1_000_000.0 / numReads) + "ns");
    }

    @Test
    void baselineDeletions() {
        // Pre-populate trie
        int numEntries = 500;
        List<byte[]> keys = generateRandomKeys(numEntries);
        List<byte[]> values = generateRandomValues(numEntries);

        for (int i = 0; i < numEntries; i++) {
            trie.put(keys.get(i), values.get(i));
        }

        // Delete half of the entries
        int numDeletions = numEntries / 2;
        long startTime = System.nanoTime();

        for (int i = 0; i < numDeletions; i++) {
            trie.delete(keys.get(i));
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        System.out.println("Baseline Deletions:");
        System.out.println("  Deletions: " + numDeletions);
        System.out.println("  Duration: " + durationMs + "ms");
        System.out.println("  Deletes/sec: " + (numDeletions * 1000L / Math.max(durationMs, 1)));

        // Verify deletions worked
        for (int i = 0; i < numDeletions; i++) {
            assertNull(trie.get(keys.get(i)));
        }
        for (int i = numDeletions; i < numEntries; i++) {
            assertArrayEquals(values.get(i), trie.get(keys.get(i)));
        }
    }

    // Note: Prefix scanning benchmark removed - MpfTrie hashes keys which destroys prefix relationships.
    // Aiken's merkle-patricia-forestry also does not support prefix scanning.

    @Test
    void baselineMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();

        // Force garbage collection to get baseline
        System.gc();
        Thread.yield();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Insert data
        int numEntries = 10000;
        for (int i = 0; i < numEntries; i++) {
            byte[] key = intToBytes(i);
            byte[] value = ("value-" + i).getBytes();
            trie.put(key, value);
        }

        // Measure memory after
        System.gc();
        Thread.yield();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        long memoryUsed = memoryAfter - memoryBefore;
        double avgBytesPerEntry = (double) memoryUsed / numEntries;

        System.out.println("Baseline Memory Usage:");
        System.out.println("  Entries: " + numEntries);
        System.out.println("  Memory used: " + (memoryUsed / 1024) + " KB");
        System.out.println("  Avg bytes per entry: " + String.format("%.2f", avgBytesPerEntry));

        assertTrue(memoryUsed > 0, "Should use some memory");
        assertTrue(avgBytesPerEntry > 0, "Should use memory per entry");
    }

    @Test
    void baselineMixedWorkload() {
        int totalOperations = 1000;
        List<byte[]> keys = generateRandomKeys(totalOperations);
        List<byte[]> values = generateRandomValues(totalOperations);

        long startTime = System.nanoTime();

        // Mixed workload: 60% puts, 30% gets, 10% deletes
        for (int i = 0; i < totalOperations; i++) {
            double rand = random.nextDouble();

            if (rand < 0.6) {
                // Put operation
                trie.put(keys.get(i), values.get(i));
            } else if (rand < 0.9) {
                // Get operation (on existing key if available)
                int keyIndex = Math.min(i, random.nextInt(Math.max(i, 1)));
                trie.get(keys.get(keyIndex));
            } else {
                // Delete operation (on existing key if available)
                if (i > 10) {
                    int keyIndex = random.nextInt(i - 5);
                    trie.delete(keys.get(keyIndex));
                }
            }
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        System.out.println("Baseline Mixed Workload:");
        System.out.println("  Operations: " + totalOperations);
        System.out.println("  Duration: " + durationMs + "ms");
        System.out.println("  Ops/sec: " + (totalOperations * 1000L / Math.max(durationMs, 1)));
    }

    // Helper methods
    private List<byte[]> generateRandomKeys(int count) {
        List<byte[]> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] key = new byte[8];
            random.nextBytes(key);
            keys.add(key);
        }
        return keys;
    }

    private List<byte[]> generateRandomValues(int count) {
        List<byte[]> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] value = new byte[32 + random.nextInt(32)]; // Variable length values
            random.nextBytes(value);
            values.add(value);
        }
        return values;
    }

    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    private byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) hex = "0" + hex;
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

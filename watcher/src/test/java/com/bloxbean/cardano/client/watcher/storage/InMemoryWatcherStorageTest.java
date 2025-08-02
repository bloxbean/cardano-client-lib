package com.bloxbean.cardano.client.watcher.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import com.bloxbean.cardano.client.watcher.api.WatchStatus;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InMemoryWatcherStorage implementation.
 * Extends the abstract test contract and adds specific tests for in-memory behavior.
 */
public class InMemoryWatcherStorageTest extends WatcherStorageTest {
    
    private InMemoryWatcherStorage inMemoryStorage;
    
    @Override
    protected WatcherStorage createStorage() {
        inMemoryStorage = new InMemoryWatcherStorage();
        return inMemoryStorage;
    }
    
    @BeforeEach
    void setUpInMemory() {
        // Additional setup for in-memory specific tests
        inMemoryStorage = (InMemoryWatcherStorage) storage;
    }
    
    @Test
    void testConcurrentAccess() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        // Create multiple threads doing concurrent operations
        List<CompletableFuture<Void>> futures = IntStream.range(0, 100)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                StoredWatch watch = StoredWatch.builder()
                    .watchId("concurrent-" + i)
                    .transactionId("tx-" + i)
                    .status(WatchStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();
                
                // Store, retrieve, update, remove cycle
                storage.store(watch);
                storage.retrieve("concurrent-" + i);
                storage.update(watch.toBuilder().status(WatchStatus.WATCHING).build());
                storage.remove("concurrent-" + i);
            }, executor))
            .collect(java.util.stream.Collectors.toList());
        
        // Wait for all operations to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Concurrent operations failed: " + e.getMessage());
        }
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Verify no data corruption - storage should be empty
        assertEquals(0, storage.findActive().size());
    }
    
    @Test
    void testMemoryCleanup() {
        // Add many watches
        for (int i = 0; i < 1000; i++) {
            StoredWatch watch = StoredWatch.builder()
                .watchId("cleanup-" + i)
                .transactionId("tx-" + i)
                .status(WatchStatus.PENDING)
                .createdAt(Instant.now())
                .build();
            storage.store(watch);
        }
        
        // Verify they're all there
        assertEquals(1000, storage.findByStatus(WatchStatus.PENDING).size());
        
        // Remove half of them
        for (int i = 0; i < 500; i++) {
            storage.remove("cleanup-" + i);
        }
        
        // Verify cleanup worked
        assertEquals(500, storage.findByStatus(WatchStatus.PENDING).size());
        
        // Force cleanup if available
        if (inMemoryStorage != null) {
            inMemoryStorage.cleanup();
        }
        
        assertEquals(500, storage.findByStatus(WatchStatus.PENDING).size());
    }
    
    @Test
    void testPerformance() {
        long startTime = System.currentTimeMillis();
        
        // Store 1000 watches
        for (int i = 0; i < 1000; i++) {
            StoredWatch watch = StoredWatch.builder()
                .watchId("perf-" + i)
                .transactionId("tx-" + i)
                .status(WatchStatus.PENDING)
                .createdAt(Instant.now())
                .build();
            storage.store(watch);
        }
        
        long storeTime = System.currentTimeMillis() - startTime;
        
        // Retrieve all watches
        startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            storage.retrieve("perf-" + i);
        }
        long retrieveTime = System.currentTimeMillis() - startTime;
        
        // Performance assertions
        assertTrue(storeTime < 1000, "Store operations should complete in < 1s, took: " + storeTime + "ms");
        assertTrue(retrieveTime < 500, "Retrieve operations should complete in < 500ms, took: " + retrieveTime + "ms");
        
        // Average should be < 1ms per operation
        assertTrue((storeTime + retrieveTime) / 2000.0 < 1.0, "Average operation time should be < 1ms");
    }
    
    @RepeatedTest(5)
    void testThreadSafety() throws InterruptedException {
        final String watchId = "thread-safe-test";
        final int numThreads = 20;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        // Initial watch
        StoredWatch initialWatch = StoredWatch.builder()
            .watchId(watchId)
            .transactionId("tx-thread-safe")
            .status(WatchStatus.PENDING)
            .createdAt(Instant.now())
            .retryCount(0)
            .build();
        
        storage.store(initialWatch);
        
        // Multiple threads updating the same watch
        List<CompletableFuture<Void>> futures = IntStream.range(0, numThreads)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                for (int j = 0; j < 10; j++) {
                    var current = storage.retrieve(watchId).orElseThrow();
                    var updated = current.toBuilder()
                        .retryCount(current.getRetryCount() + 1)
                        .updatedAt(Instant.now())
                        .build();
                    storage.update(updated);
                }
            }, executor))
            .collect(java.util.stream.Collectors.toList());
        
        // Wait for completion
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Thread safety test failed: " + e.getMessage());
        }
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Verify final state
        var finalWatch = storage.retrieve(watchId).orElseThrow();
        assertEquals(watchId, finalWatch.getWatchId());
        assertTrue(finalWatch.getRetryCount() > 0, "Retry count should have been incremented");
        assertNotNull(finalWatch.getUpdatedAt());
    }
    
    @Test
    void testStorageMetrics() {
        // Test basic metrics if available
        assertEquals(0, inMemoryStorage.size());
        
        // Add some watches
        for (int i = 0; i < 10; i++) {
            StoredWatch watch = StoredWatch.builder()
                .watchId("metrics-" + i)
                .transactionId("tx-" + i)
                .status(WatchStatus.PENDING)
                .createdAt(Instant.now())
                .build();
            storage.store(watch);
        }
        
        assertEquals(10, inMemoryStorage.size());
        
        // Remove some
        for (int i = 0; i < 5; i++) {
            storage.remove("metrics-" + i);
        }
        
        assertEquals(5, inMemoryStorage.size());
    }
}
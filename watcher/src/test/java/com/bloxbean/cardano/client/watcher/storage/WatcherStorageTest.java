package com.bloxbean.cardano.client.watcher.storage;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test interface for WatcherStorage implementations.
 * This provides a contract that all storage implementations must satisfy.
 */
public abstract class WatcherStorageTest {
    
    protected WatcherStorage storage;
    
    /**
     * Subclasses must implement this to provide the storage instance to test.
     */
    protected abstract WatcherStorage createStorage();
    
    @BeforeEach
    void setUp() {
        storage = createStorage();
    }
    
    @Test
    void testStoreAndRetrieve() {
        // Given
        StoredWatch watch = StoredWatch.builder()
                .watchId("test-watch-1")
                .transactionId("tx-123")
                .status(WatchStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        
        // When
        storage.store(watch);
        Optional<StoredWatch> retrieved = storage.retrieve("test-watch-1");
        
        // Then
        assertTrue(retrieved.isPresent());
        assertEquals(watch.getWatchId(), retrieved.get().getWatchId());
        assertEquals(watch.getTransactionId(), retrieved.get().getTransactionId());
        assertEquals(watch.getStatus(), retrieved.get().getStatus());
    }
    
    @Test
    void testRetrieveNonExistent() {
        // When
        Optional<StoredWatch> result = storage.retrieve("non-existent");
        
        // Then
        assertFalse(result.isPresent());
    }
    
    @Test
    void testUpdate() {
        // Given
        StoredWatch original = StoredWatch.builder()
                .watchId("test-watch-2")
                .transactionId("tx-456")
                .status(WatchStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        
        storage.store(original);
        
        StoredWatch updated = original.toBuilder()
                .status(WatchStatus.CONFIRMED)
                .updatedAt(Instant.now())
                .build();
        
        // When
        storage.update(updated);
        Optional<StoredWatch> retrieved = storage.retrieve("test-watch-2");
        
        // Then
        assertTrue(retrieved.isPresent());
        assertEquals(WatchStatus.CONFIRMED, retrieved.get().getStatus());
        assertNotNull(retrieved.get().getUpdatedAt());
    }
    
    @Test
    void testUpdateNonExistent() {
        // Given
        StoredWatch watch = StoredWatch.builder()
                .watchId("non-existent")
                .transactionId("tx-789")
                .status(WatchStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        
        // When/Then
        assertThrows(StorageException.class, () -> storage.update(watch));
    }
    
    @Test
    void testRemove() {
        // Given
        StoredWatch watch = StoredWatch.builder()
                .watchId("test-watch-3")
                .transactionId("tx-789")
                .status(WatchStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        
        storage.store(watch);
        
        // When
        boolean removed = storage.remove("test-watch-3");
        Optional<StoredWatch> retrieved = storage.retrieve("test-watch-3");
        
        // Then
        assertTrue(removed);
        assertFalse(retrieved.isPresent());
    }
    
    @Test
    void testRemoveNonExistent() {
        // When
        boolean removed = storage.remove("non-existent");
        
        // Then
        assertFalse(removed);
    }
    
    @Test
    void testFindByStatus() {
        // Given
        StoredWatch pending1 = StoredWatch.builder()
                .watchId("pending-1")
                .transactionId("tx-p1")
                .status(WatchStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        
        StoredWatch pending2 = StoredWatch.builder()
                .watchId("pending-2")
                .transactionId("tx-p2")
                .status(WatchStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        
        StoredWatch confirmed = StoredWatch.builder()
                .watchId("confirmed-1")
                .transactionId("tx-c1")
                .status(WatchStatus.CONFIRMED)
                .createdAt(Instant.now())
                .build();
        
        storage.store(pending1);
        storage.store(pending2);
        storage.store(confirmed);
        
        // When
        List<StoredWatch> pendingWatches = storage.findByStatus(WatchStatus.PENDING);
        List<StoredWatch> confirmedWatches = storage.findByStatus(WatchStatus.CONFIRMED);
        
        // Then
        assertEquals(2, pendingWatches.size());
        assertEquals(1, confirmedWatches.size());
        assertTrue(pendingWatches.stream().allMatch(w -> w.getStatus() == WatchStatus.PENDING));
        assertTrue(confirmedWatches.stream().allMatch(w -> w.getStatus() == WatchStatus.CONFIRMED));
    }
    
    @Test
    void testFindActive() {
        // Given
        StoredWatch pending = StoredWatch.builder()
                .watchId("active-1")
                .transactionId("tx-a1")
                .status(WatchStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        
        StoredWatch watching = StoredWatch.builder()
                .watchId("active-2")
                .transactionId("tx-a2")
                .status(WatchStatus.WATCHING)
                .createdAt(Instant.now())
                .build();
        
        StoredWatch confirmed = StoredWatch.builder()
                .watchId("inactive-1")
                .transactionId("tx-i1")
                .status(WatchStatus.CONFIRMED)
                .createdAt(Instant.now())
                .build();
        
        StoredWatch failed = StoredWatch.builder()
                .watchId("inactive-2")
                .transactionId("tx-i2")
                .status(WatchStatus.FAILED)
                .createdAt(Instant.now())
                .build();
        
        storage.store(pending);
        storage.store(watching);
        storage.store(confirmed);
        storage.store(failed);
        
        // When
        List<StoredWatch> activeWatches = storage.findActive();
        
        // Then
        assertEquals(2, activeWatches.size());
        assertTrue(activeWatches.stream().anyMatch(w -> w.getWatchId().equals("active-1")));
        assertTrue(activeWatches.stream().anyMatch(w -> w.getWatchId().equals("active-2")));
    }
    
    @Test
    void testStoreBatch() {
        // Given
        List<StoredWatch> watches = List.of(
                StoredWatch.builder()
                        .watchId("batch-1")
                        .transactionId("tx-b1")
                        .status(WatchStatus.PENDING)
                        .createdAt(Instant.now())
                        .build(),
                StoredWatch.builder()
                        .watchId("batch-2")
                        .transactionId("tx-b2")
                        .status(WatchStatus.PENDING)
                        .createdAt(Instant.now())
                        .build()
        );
        
        // When
        storage.storeBatch(watches);
        
        // Then
        Optional<StoredWatch> watch1 = storage.retrieve("batch-1");
        Optional<StoredWatch> watch2 = storage.retrieve("batch-2");
        
        assertTrue(watch1.isPresent());
        assertTrue(watch2.isPresent());
    }
    
    @Test
    void testUpdateBatch() {
        // Given
        List<StoredWatch> original = List.of(
                StoredWatch.builder()
                        .watchId("update-batch-1")
                        .transactionId("tx-ub1")
                        .status(WatchStatus.PENDING)
                        .createdAt(Instant.now())
                        .build(),
                StoredWatch.builder()
                        .watchId("update-batch-2")
                        .transactionId("tx-ub2")
                        .status(WatchStatus.PENDING)
                        .createdAt(Instant.now())
                        .build()
        );
        
        storage.storeBatch(original);
        
        List<StoredWatch> updated = original.stream()
                .map(w -> w.toBuilder().status(WatchStatus.WATCHING).updatedAt(Instant.now()).build())
                .collect(java.util.stream.Collectors.toList());
        
        // When
        storage.updateBatch(updated);
        
        // Then
        Optional<StoredWatch> watch1 = storage.retrieve("update-batch-1");
        Optional<StoredWatch> watch2 = storage.retrieve("update-batch-2");
        
        assertTrue(watch1.isPresent());
        assertTrue(watch2.isPresent());
        assertEquals(WatchStatus.WATCHING, watch1.get().getStatus());
        assertEquals(WatchStatus.WATCHING, watch2.get().getStatus());
    }
}
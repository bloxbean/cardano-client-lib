package com.bloxbean.cardano.client.watcher.event;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WatchEvent hierarchy.
 */
public class WatchEventTest {
    
    @Test
    void testTransactionSubmittedEvent() {
        String watchId = "test-watch-1";
        String txHash = "abcd1234";
        Instant timestamp = Instant.now();
        
        TransactionSubmittedEvent event = new TransactionSubmittedEvent(watchId, txHash, timestamp);
        
        assertEquals(WatchEventType.TRANSACTION_SUBMITTED, event.getEventType());
        assertEquals(watchId, event.getWatchId());
        assertEquals(txHash, event.getTransactionHash());
        assertEquals(timestamp, event.getTimestamp());
        assertNotNull(event.getEventId());
        assertNotNull(event.toString());
    }
    
    @Test
    void testTransactionConfirmedEvent() {
        String watchId = "test-watch-2";
        String txHash = "efgh5678";
        long blockHeight = 12345L;
        int confirmations = 3;
        Instant timestamp = Instant.now();
        
        TransactionConfirmedEvent event = new TransactionConfirmedEvent(
            watchId, txHash, blockHeight, confirmations, timestamp);
        
        assertEquals(WatchEventType.TRANSACTION_CONFIRMED, event.getEventType());
        assertEquals(watchId, event.getWatchId());
        assertEquals(txHash, event.getTransactionHash());
        assertEquals(blockHeight, event.getBlockHeight());
        assertEquals(confirmations, event.getConfirmations());
        assertEquals(timestamp, event.getTimestamp());
    }
    
    @Test
    void testTransactionFailedEvent() {
        String watchId = "test-watch-3";
        String txHash = "ijkl9012";
        String error = "Insufficient funds";
        Instant timestamp = Instant.now();
        
        TransactionFailedEvent event = new TransactionFailedEvent(watchId, txHash, error, timestamp);
        
        assertEquals(WatchEventType.TRANSACTION_FAILED, event.getEventType());
        assertEquals(watchId, event.getWatchId());
        assertEquals(txHash, event.getTransactionHash());
        assertEquals(error, event.getError());
        assertEquals(timestamp, event.getTimestamp());
    }
    
    @Test
    void testWatchStatusChangedEvent() {
        String watchId = "test-watch-4";
        WatchStatus oldStatus = WatchStatus.PENDING;
        WatchStatus newStatus = WatchStatus.WATCHING;
        Instant timestamp = Instant.now();
        
        WatchStatusChangedEvent event = new WatchStatusChangedEvent(
            watchId, oldStatus, newStatus, timestamp);
        
        assertEquals(WatchEventType.WATCH_STATUS_CHANGED, event.getEventType());
        assertEquals(watchId, event.getWatchId());
        assertEquals(oldStatus, event.getOldStatus());
        assertEquals(newStatus, event.getNewStatus());
        assertEquals(timestamp, event.getTimestamp());
    }
    
    @Test
    void testRollbackDetectedEvent() {
        String watchId = "test-watch-5";
        String txHash = "mnop3456";
        long fromHeight = 1000L;
        long toHeight = 995L;
        Instant timestamp = Instant.now();
        
        RollbackDetectedEvent event = new RollbackDetectedEvent(
            watchId, txHash, fromHeight, toHeight, timestamp);
        
        assertEquals(WatchEventType.ROLLBACK_DETECTED, event.getEventType());
        assertEquals(watchId, event.getWatchId());
        assertEquals(txHash, event.getTransactionHash());
        assertEquals(fromHeight, event.getFromHeight());
        assertEquals(toHeight, event.getToHeight());
        assertEquals(timestamp, event.getTimestamp());
    }
    
    @Test
    void testEventEquality() {
        String watchId = "test-watch-6";
        String txHash = "qrst7890";
        Instant timestamp = Instant.now();
        
        TransactionSubmittedEvent event1 = new TransactionSubmittedEvent(watchId, txHash, timestamp);
        TransactionSubmittedEvent event2 = new TransactionSubmittedEvent(watchId, txHash, timestamp);
        
        // Events should not be equal even with same content (different IDs)
        assertNotEquals(event1, event2);
        assertNotEquals(event1.getEventId(), event2.getEventId());
    }
    
    @Test
    void testEventOrdering() {
        Instant now = Instant.now();
        Instant later = now.plusSeconds(1);
        
        TransactionSubmittedEvent event1 = new TransactionSubmittedEvent("watch-1", "tx-1", now);
        TransactionSubmittedEvent event2 = new TransactionSubmittedEvent("watch-2", "tx-2", later);
        
        assertTrue(event1.compareTo(event2) < 0);
        assertTrue(event2.compareTo(event1) > 0);
        assertEquals(0, event1.compareTo(event1));
    }
}
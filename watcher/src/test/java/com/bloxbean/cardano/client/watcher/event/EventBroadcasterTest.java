package com.bloxbean.cardano.client.watcher.event;

import com.bloxbean.cardano.client.watcher.api.WatchEventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EventBroadcaster.
 */
public class EventBroadcasterTest {
    
    private EventBroadcaster broadcaster;
    
    @BeforeEach
    void setUp() {
        broadcaster = new EventBroadcaster();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (broadcaster != null) {
            broadcaster.close();
        }
    }
    
    @Test
    void testAddAndRemoveListener() {
        WatchEventListener listener = event -> {};
        
        assertEquals(0, broadcaster.getListenerCount());
        
        broadcaster.addListener(listener);
        assertEquals(1, broadcaster.getListenerCount());
        
        boolean removed = broadcaster.removeListener(listener);
        assertTrue(removed);
        assertEquals(0, broadcaster.getListenerCount());
        
        // Try to remove non-existent listener
        boolean removedAgain = broadcaster.removeListener(listener);
        assertFalse(removedAgain);
    }
    
    @Test
    void testBroadcastEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<WatchEvent> receivedEvents = new CopyOnWriteArrayList<>();
        
        WatchEventListener listener = event -> {
            receivedEvents.add(event);
            latch.countDown();
        };
        
        broadcaster.addListener(listener);
        
        TransactionSubmittedEvent event = new TransactionSubmittedEvent("test-watch", "tx-hash");
        broadcaster.broadcastEvent(event);
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, receivedEvents.size());
        assertEquals(event.getEventId(), receivedEvents.get(0).getEventId());
    }
    
    @Test
    void testMultipleListeners() throws InterruptedException {
        int numListeners = 5;
        CountDownLatch latch = new CountDownLatch(numListeners);
        AtomicInteger eventCount = new AtomicInteger(0);
        
        // Add multiple listeners
        for (int i = 0; i < numListeners; i++) {
            broadcaster.addListener(event -> {
                eventCount.incrementAndGet();
                latch.countDown();
            });
        }
        
        TransactionSubmittedEvent event = new TransactionSubmittedEvent("test-watch", "tx-hash");
        broadcaster.broadcastEvent(event);
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(numListeners, eventCount.get());
    }
    
    @Test
    void testEventOrdering() throws InterruptedException {
        int numEvents = 10;
        CountDownLatch latch = new CountDownLatch(numEvents);
        List<WatchEvent> receivedEvents = new CopyOnWriteArrayList<>();
        
        WatchEventListener listener = event -> {
            receivedEvents.add(event);
            latch.countDown();
        };
        
        broadcaster.addListener(listener);
        
        // Send events rapidly
        for (int i = 0; i < numEvents; i++) {
            TransactionSubmittedEvent event = new TransactionSubmittedEvent("test-watch-" + i, "tx-hash-" + i);
            broadcaster.broadcastEvent(event);
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(numEvents, receivedEvents.size());
        
        // Verify events are received in order (by timestamp)
        for (int i = 1; i < receivedEvents.size(); i++) {
            assertTrue(receivedEvents.get(i - 1).getTimestamp().compareTo(receivedEvents.get(i).getTimestamp()) <= 0);
        }
    }
    
    @Test
    void testListenerFailureIsolation() throws InterruptedException {
        CountDownLatch goodLatch = new CountDownLatch(1);
        CountDownLatch badLatch = new CountDownLatch(1);
        
        // Good listener
        broadcaster.addListener(event -> {
            goodLatch.countDown();
        });
        
        // Bad listener that throws exception
        broadcaster.addListener(event -> {
            badLatch.countDown();
            throw new RuntimeException("Test exception");
        });
        
        TransactionSubmittedEvent event = new TransactionSubmittedEvent("test-watch", "tx-hash");
        broadcaster.broadcastEvent(event);
        
        // Both listeners should be called despite one failing
        assertTrue(goodLatch.await(5, TimeUnit.SECONDS));
        assertTrue(badLatch.await(5, TimeUnit.SECONDS));
    }
    
    @Test
    void testSynchronousBroadcast() throws Exception {
        AtomicInteger eventCount = new AtomicInteger(0);
        
        broadcaster.addListener(event -> {
            eventCount.incrementAndGet();
        });
        
        TransactionSubmittedEvent event = new TransactionSubmittedEvent("test-watch", "tx-hash");
        
        CompletableFuture<Void> future = broadcaster.broadcastEventSync(event);
        future.get(5, TimeUnit.SECONDS);
        
        assertEquals(1, eventCount.get());
    }
    
    @Test
    void testNullEventHandling() {
        // Should not throw exception or cause issues
        broadcaster.broadcastEvent(null);
        assertEquals(0, broadcaster.getQueueSize());
    }
    
    @Test
    void testNullListenerHandling() {
        int initialCount = broadcaster.getListenerCount();
        
        broadcaster.addListener(null);
        assertEquals(initialCount, broadcaster.getListenerCount());
    }
    
    @Test
    void testShutdown() throws Exception {
        assertTrue(broadcaster.isRunning());
        
        broadcaster.close();
        
        // Should not accept new events after shutdown
        TransactionSubmittedEvent event = new TransactionSubmittedEvent("test-watch", "tx-hash");
        broadcaster.broadcastEvent(event);
        
        // Give it a moment to potentially process
        Thread.sleep(100);
        
        // Queue should be empty or event should not be processed
        assertFalse(broadcaster.isRunning());
    }
    
    @Test
    void testConcurrentListenerManagement() throws InterruptedException {
        int numThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        
        // Concurrently add and remove listeners
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    WatchEventListener listener = event -> {};
                    broadcaster.addListener(listener);
                    Thread.sleep(10);
                    broadcaster.removeListener(listener);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        
        // Should end up with no listeners
        assertEquals(0, broadcaster.getListenerCount());
    }
}
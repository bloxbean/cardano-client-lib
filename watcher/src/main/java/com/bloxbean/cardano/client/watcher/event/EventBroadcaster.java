package com.bloxbean.cardano.client.watcher.event;

import com.bloxbean.cardano.client.watcher.api.WatchEventListener;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous event broadcaster for watch events.
 * 
 * Provides reliable, ordered event delivery with error isolation between listeners.
 */
public class EventBroadcaster implements AutoCloseable {
    
    private final List<WatchEventListener> listeners;
    private final ExecutorService executorService;
    private final BlockingQueue<WatchEvent> eventQueue;
    private final AtomicBoolean running;
    private final Thread eventProcessor;
    
    public EventBroadcaster() {
        this.listeners = new CopyOnWriteArrayList<>();
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "watch-event-broadcaster");
            thread.setDaemon(true);
            return thread;
        });
        this.eventQueue = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean(true);
        this.eventProcessor = new Thread(this::processEvents, "watch-event-processor");
        this.eventProcessor.setDaemon(true);
        this.eventProcessor.start();
    }
    
    /**
     * Add a listener to receive watch events.
     * 
     * @param listener the listener to add
     */
    public void addListener(WatchEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a listener from receiving watch events.
     * 
     * @param listener the listener to remove
     * @return true if the listener was removed, false if it wasn't found
     */
    public boolean removeListener(WatchEventListener listener) {
        return listeners.remove(listener);
    }
    
    /**
     * Get the number of registered listeners.
     * 
     * @return the listener count
     */
    public int getListenerCount() {
        return listeners.size();
    }
    
    /**
     * Broadcast an event to all registered listeners.
     * 
     * Events are queued and delivered asynchronously to preserve order.
     * 
     * @param event the event to broadcast
     */
    public void broadcastEvent(WatchEvent event) {
        if (event != null && running.get()) {
            try {
                eventQueue.offer(event);
            } catch (Exception e) {
                // Log error but don't fail - event broadcasting should be resilient
                System.err.println("Failed to queue event: " + e.getMessage());
            }
        }
    }
    
    /**
     * Broadcast an event and wait for all listeners to process it.
     * 
     * @param event the event to broadcast
     * @return a future that completes when all listeners have processed the event
     */
    public CompletableFuture<Void> broadcastEventSync(WatchEvent event) {
        if (event == null || !running.get()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            deliverToListeners(event);
        }, executorService);
    }
    
    /**
     * Process events from the queue in order.
     */
    private void processEvents() {
        while (running.get()) {
            try {
                WatchEvent event = eventQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    deliverToListeners(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log error but continue processing
                System.err.println("Error processing event: " + e.getMessage());
            }
        }
        
        // Process remaining events before shutdown
        WatchEvent event;
        while ((event = eventQueue.poll()) != null) {
            deliverToListeners(event);
        }
    }
    
    /**
     * Deliver an event to all listeners.
     * 
     * Each listener is called in a separate task to isolate failures.
     */
    private void deliverToListeners(WatchEvent event) {
        if (listeners.isEmpty()) {
            return;
        }
        
        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        
        for (WatchEventListener listener : listeners) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    // Isolate listener failures - one bad listener shouldn't affect others
                    System.err.println("Listener failed to process event " + event.getEventId() + ": " + e.getMessage());
                }
            }, executorService);
            futures.add(future);
        }
        
        // Wait for all listeners to complete (with timeout)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Some listeners may have timed out or failed, but we continue
            System.err.println("Some listeners failed or timed out for event " + event.getEventId());
        }
    }
    
    /**
     * Get the current queue size (for monitoring).
     * 
     * @return the number of events waiting to be processed
     */
    public int getQueueSize() {
        return eventQueue.size();
    }
    
    /**
     * Check if the broadcaster is running.
     * 
     * @return true if running, false if shutdown
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Shutdown the broadcaster and clean up resources.
     */
    @Override
    public void close() {
        running.set(false);
        
        try {
            // Wait for event processor to finish
            eventProcessor.interrupt();
            eventProcessor.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Shutdown executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
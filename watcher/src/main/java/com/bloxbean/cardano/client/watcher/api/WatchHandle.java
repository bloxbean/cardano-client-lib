package com.bloxbean.cardano.client.watcher.api;

import java.util.concurrent.CompletableFuture;

/**
 * Handle for a watched transaction providing access to the result and control operations.
 */
public class WatchHandle {
    
    private final String watchId;
    private final CompletableFuture<WatchResult> future;
    
    public WatchHandle(String watchId, CompletableFuture<WatchResult> future) {
        this.watchId = watchId;
        this.future = future;
    }
    
    /**
     * Get the unique watch identifier.
     * 
     * @return the watch ID
     */
    public String getWatchId() {
        return watchId;
    }
    
    /**
     * Get the future that will complete when the transaction is confirmed or fails.
     * 
     * @return the completion future
     */
    public CompletableFuture<WatchResult> getFuture() {
        return future;
    }
    
    /**
     * Cancel the watch operation if possible.
     * 
     * @return true if the watch was cancelled
     */
    public boolean cancel() {
        return future.cancel(false);
    }
    
    /**
     * Check if the watch operation is done (completed, failed, or cancelled).
     * 
     * @return true if done
     */
    public boolean isDone() {
        return future.isDone();
    }
    
    /**
     * Check if the watch operation was cancelled.
     * 
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return future.isCancelled();
    }
}
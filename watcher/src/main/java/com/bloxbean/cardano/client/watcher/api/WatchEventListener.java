package com.bloxbean.cardano.client.watcher.api;

import com.bloxbean.cardano.client.watcher.event.WatchEvent;

/**
 * Functional interface for listening to watch events.
 */
@FunctionalInterface
public interface WatchEventListener {
    
    /**
     * Called when a watch event occurs.
     * 
     * @param event the watch event
     */
    void onEvent(WatchEvent event);
}
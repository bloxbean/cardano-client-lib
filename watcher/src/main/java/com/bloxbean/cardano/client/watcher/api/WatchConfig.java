package com.bloxbean.cardano.client.watcher.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for watching transactions.
 */
public class WatchConfig {
    
    private final Duration timeout;
    private final int maxRetries;
    private final List<WatchEventListener> listeners;
    private final String description;
    
    private WatchConfig(Duration timeout, int maxRetries, List<WatchEventListener> listeners, String description) {
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.listeners = Collections.unmodifiableList(new ArrayList<>(listeners));
        this.description = description;
    }
    
    public Duration getTimeout() {
        return timeout;
    }
    
    /**
     * Get timeout in milliseconds for backward compatibility.
     * 
     * @return timeout in milliseconds
     */
    public long getTimeoutMs() {
        return timeout.toMillis();
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public List<WatchEventListener> getListeners() {
        return listeners;
    }
    
    /**
     * Get the optional description for this watch configuration.
     * 
     * @return optional description
     */
    public java.util.Optional<String> getDescription() {
        return java.util.Optional.ofNullable(description);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static WatchConfig defaultConfig() {
        return builder().build();
    }
    
    public static class Builder {
        private Duration timeout = Duration.ofMinutes(10);
        private int maxRetries = 3;
        private List<WatchEventListener> listeners = new ArrayList<>();
        private String description;
        
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder addListener(WatchEventListener listener) {
            this.listeners.add(listener);
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public WatchConfig build() {
            return new WatchConfig(timeout, maxRetries, listeners, description);
        }
    }
}
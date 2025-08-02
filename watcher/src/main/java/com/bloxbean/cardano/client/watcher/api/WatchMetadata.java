package com.bloxbean.cardano.client.watcher.api;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Metadata associated with a watch result.
 */
public class WatchMetadata {
    
    private final Instant submittedAt;
    private final Instant confirmedAt;
    private final int retryCount;
    private final Map<String, Object> additionalData;
    
    public WatchMetadata(Instant submittedAt, Instant confirmedAt, int retryCount, Map<String, Object> additionalData) {
        this.submittedAt = submittedAt;
        this.confirmedAt = confirmedAt;
        this.retryCount = retryCount;
        this.additionalData = additionalData != null ? Map.copyOf(additionalData) : Collections.emptyMap();
    }
    
    public Instant getSubmittedAt() {
        return submittedAt;
    }
    
    public Instant getConfirmedAt() {
        return confirmedAt;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }
}
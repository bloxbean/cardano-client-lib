package com.bloxbean.cardano.client.watcher.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all watch events.
 * 
 * Provides common functionality for event identification, ordering, and metadata.
 */
public abstract class WatchEvent implements Comparable<WatchEvent> {
    
    private final String eventId;
    private final String watchId;
    private final Instant timestamp;
    private final WatchEventType eventType;
    
    protected WatchEvent(String watchId, WatchEventType eventType, Instant timestamp) {
        this.eventId = UUID.randomUUID().toString();
        this.watchId = Objects.requireNonNull(watchId, "watchId cannot be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }
    
    protected WatchEvent(String watchId, WatchEventType eventType) {
        this(watchId, eventType, Instant.now());
    }
    
    /**
     * Get the unique event identifier.
     * 
     * @return the event ID
     */
    public String getEventId() {
        return eventId;
    }
    
    /**
     * Get the watch identifier this event belongs to.
     * 
     * @return the watch ID
     */
    public String getWatchId() {
        return watchId;
    }
    
    /**
     * Get the timestamp when this event occurred.
     * 
     * @return the event timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Get the type of this event.
     * 
     * @return the event type
     */
    public WatchEventType getEventType() {
        return eventType;
    }
    
    /**
     * Get the type of this event (backwards compatibility).
     * 
     * @return the event type
     * @deprecated use getEventType() instead
     */
    @Deprecated
    public WatchEventType getType() {
        return eventType;
    }
    
    @Override
    public int compareTo(WatchEvent other) {
        if (other == null) {
            return 1;
        }
        return this.timestamp.compareTo(other.timestamp);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WatchEvent that = (WatchEvent) o;
        return Objects.equals(eventId, that.eventId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "eventId='" + eventId + '\'' +
                ", watchId='" + watchId + '\'' +
                ", eventType=" + eventType +
                ", timestamp=" + timestamp +
                '}';
    }
}
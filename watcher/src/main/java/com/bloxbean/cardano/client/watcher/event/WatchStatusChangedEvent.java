package com.bloxbean.cardano.client.watcher.event;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;

import java.time.Instant;

/**
 * Event emitted when a watch status changes.
 */
public class WatchStatusChangedEvent extends WatchEvent {
    
    private final WatchStatus oldStatus;
    private final WatchStatus newStatus;
    
    public WatchStatusChangedEvent(String watchId, WatchStatus oldStatus, WatchStatus newStatus, Instant timestamp) {
        super(watchId, WatchEventType.WATCH_STATUS_CHANGED, timestamp);
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }
    
    public WatchStatusChangedEvent(String watchId, WatchStatus oldStatus, WatchStatus newStatus) {
        this(watchId, oldStatus, newStatus, Instant.now());
    }
    
    /**
     * Get the previous status.
     * 
     * @return the old status
     */
    public WatchStatus getOldStatus() {
        return oldStatus;
    }
    
    /**
     * Get the new status.
     * 
     * @return the new status
     */
    public WatchStatus getNewStatus() {
        return newStatus;
    }
    
    @Override
    public String toString() {
        return "WatchStatusChangedEvent{" +
                "eventId='" + getEventId() + '\'' +
                ", watchId='" + getWatchId() + '\'' +
                ", oldStatus=" + oldStatus +
                ", newStatus=" + newStatus +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}
package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;

/**
 * Integration test POJO covering enum element types inside collections and Optional.
 * The annotation processor generates {@code SampleEnumCollectionsMetadataConverter} from this class.
 *
 * <p>Covered scenarios:
 * <ul>
 *   <li>{@code List<OrderStatus>}      → MetadataList of enum name strings</li>
 *   <li>{@code Set<OrderStatus>}       → MetadataList; deserialized as LinkedHashSet</li>
 *   <li>{@code SortedSet<OrderStatus>} → MetadataList; deserialized as TreeSet (natural order)</li>
 *   <li>{@code Optional<OrderStatus>}  → present: enum name string; absent: key omitted</li>
 * </ul>
 */
@MetadataType
public class SampleEnumCollections {

    private List<OrderStatus> statusList;
    private Set<OrderStatus> statusSet;
    private SortedSet<OrderStatus> sortedStatuses;
    private Optional<OrderStatus> optionalStatus;

    public List<OrderStatus> getStatusList() { return statusList; }
    public void setStatusList(List<OrderStatus> statusList) { this.statusList = statusList; }

    public Set<OrderStatus> getStatusSet() { return statusSet; }
    public void setStatusSet(Set<OrderStatus> statusSet) { this.statusSet = statusSet; }

    public SortedSet<OrderStatus> getSortedStatuses() { return sortedStatuses; }
    public void setSortedStatuses(SortedSet<OrderStatus> sortedStatuses) { this.sortedStatuses = sortedStatuses; }

    public Optional<OrderStatus> getOptionalStatus() { return optionalStatus; }
    public void setOptionalStatus(Optional<OrderStatus> optionalStatus) { this.optionalStatus = optionalStatus; }
}

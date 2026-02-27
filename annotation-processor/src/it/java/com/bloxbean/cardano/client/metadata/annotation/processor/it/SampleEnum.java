package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

/**
 * Integration test POJO covering plain {@code enum} field support.
 * The annotation processor generates {@code SampleEnumMetadataConverter} from this class.
 *
 * <p>Covered scenarios:
 * <ul>
 *   <li>Enum field with default key (field name)</li>
 *   <li>Enum field with custom {@code @MetadataField(key=...)} override</li>
 *   <li>Null enum field → key absent in map</li>
 * </ul>
 */
@MetadataType
public class SampleEnum {

    /** Stored under key "status" (field name). */
    private OrderStatus status;

    /** Stored under key "st" (custom key). */
    @MetadataField(key = "st")
    private OrderStatus statusWithKey;

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public OrderStatus getStatusWithKey() { return statusWithKey; }
    public void setStatusWithKey(OrderStatus statusWithKey) { this.statusWithKey = statusWithKey; }
}

package com.bloxbean.cardano.client.quicktx.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents ordering and limiting criteria for UTxO filter results.
 * Applied after filtering to control the final result set.
 *
 * <p>Processing order:
 * <ol>
 *   <li>Apply filter AST to select matching UTxOs</li>
 *   <li>Sort by specified order criteria</li>
 *   <li>Apply limit to return top N results</li>
 * </ol>
 *
 * <p>Ordering always includes canonical tie-breakers (txHash ASC, outputIndex ASC)
 * to ensure deterministic results.
 *
 * @see Order
 */
public final class Selection {
    private final List<Order> order;
    private final Integer limit; // null means ALL

    private Selection(List<Order> order, Integer limit) {
        this.order = order == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(order));
        this.limit = limit;
    }

    /**
     * Creates a new Selection with the specified order and limit.
     *
     * @param order the ordering criteria (may be null or empty for default order)
     * @param limit the maximum number of results to return (null means no limit)
     * @return a new Selection instance
     */
    public static Selection of(List<Order> order, Integer limit) {
        return new Selection(order, limit);
    }

    public List<Order> getOrder() {
        return order;
    }

    public Integer getLimit() {
        return limit;
    }
}


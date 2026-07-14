package com.bloxbean.cardano.client.txflow.store;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Composite proof that a worker may commit an execution mutation.
 *
 * <p>The execution lease is required for every durable append. Resource leases contain every
 * spending-resource claim that must remain current for that append. A store validates the owner,
 * epoch, and expiry of the complete fence atomically with the snapshot and journal write. Every
 * lease must name the append's target execution, and resource-lease owners must match the active
 * execution-lease owner.</p>
 *
 * <p>The resource list is defensively copied and unmodifiable. Each resource identity may occur
 * only once so every adapter receives the same unambiguous fence.</p>
 *
 * @param executionLease current lease for the execution being mutated
 * @param resourceLeases current leases for its claimed spending resources
 */
public record MutationFence(ExecutionLease executionLease, List<ResourceLease> resourceLeases) {
    /**
     * Creates a composite mutation fence with an immutable resource-lease list.
     *
     * @param executionLease current lease for the execution being mutated
     * @param resourceLeases current leases for its claimed spending resources; {@code null} is
     *        treated as an empty list
     * @throws IllegalArgumentException when the list contains the same resource identity more
     *         than once
     */
    public MutationFence {
        resourceLeases = List.copyOf(resourceLeases != null ? resourceLeases : List.of());
        Set<String> identities = new HashSet<>();
        for (ResourceLease lease : resourceLeases) {
            if (!identities.add(lease.resourceId())) {
                throw new IllegalArgumentException(
                        "resource leases must have unique resource identities");
            }
        }
    }

    /**
     * Creates a fence for an execution that does not hold spending-resource leases.
     *
     * @param lease current execution lease
     * @return a composite fence with no resource claims
     */
    public static MutationFence executionOnly(ExecutionLease lease) {
        return new MutationFence(lease, List.of());
    }
}

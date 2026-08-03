package com.bloxbean.cardano.client.txflow.resource;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, process-local {@link FlowResourceCatalog} for programmatic setup
 * and tests.
 *
 * <p>Registration is keyed by normalized {@link ResourceRef}. Registering a
 * second descriptor for the same reference replaces the previous descriptor.</p>
 */
public final class InMemoryFlowResourceCatalog implements FlowResourceCatalog {
    private final Map<ResourceRef, ResourceDescriptor> resources = new ConcurrentHashMap<>();

    /**
     * Registers or replaces a resource descriptor.
     *
     * @param descriptor public resource metadata
     * @return this catalog for fluent setup
     */
    public InMemoryFlowResourceCatalog register(ResourceDescriptor descriptor) {
        resources.put(descriptor.ref(), descriptor);
        return this;
    }

    /**
     * Looks up a descriptor by its normalized logical reference.
     *
     * @param ref resource reference
     * @return registered descriptor, if present
     */
    @Override
    public Optional<ResourceDescriptor> resolve(ResourceRef ref) {
        return Optional.ofNullable(resources.get(ref));
    }
}

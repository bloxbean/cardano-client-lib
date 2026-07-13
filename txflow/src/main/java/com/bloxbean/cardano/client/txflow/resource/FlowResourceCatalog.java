package com.bloxbean.cardano.client.txflow.resource;

import java.util.Optional;

/**
 * Catalog of public metadata for logical resources referenced by portable flows.
 *
 * <p>The compiler uses descriptors for existence, network, capability, and
 * spending-contention preflight. A descriptor identifies what a resource can
 * do without exposing signer keys, scripts, or other private runtime material.</p>
 */
public interface FlowResourceCatalog {
    /**
     * Resolves the descriptor for a normalized logical resource reference.
     *
     * @param ref logical resource reference
     * @return descriptor, or an empty value when the resource is not registered
     */
    Optional<ResourceDescriptor> resolve(ResourceRef ref);
}

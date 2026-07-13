package com.bloxbean.cardano.client.txflow.resource;

import java.util.Objects;
import java.util.Set;

/**
 * Non-secret metadata used to preflight a logical flow resource.
 *
 * <p>{@code spendingIdentity} is the canonical contention key. Resources with
 * different aliases but the same non-null identity are treated as the same
 * spending source by execution coordination. It should therefore describe the
 * resolved account, address, or script-controlled source rather than repeat the
 * alias in {@link ResourceRef}.</p>
 *
 * @param ref normalized logical reference used by a flow document
 * @param network network to which the resource belongs, or {@code null} when
 *                it is not network-specific
 * @param capabilities operations the resource can satisfy; {@code null} is
 *                     normalized to an empty immutable set
 * @param spendingIdentity canonical spending-contention identity, or
 *                         {@code null} when the descriptor does not identify a
 *                         spending source
 */
public record ResourceDescriptor(ResourceRef ref, String network,
                                 Set<ResourceCapability> capabilities, String spendingIdentity) {
    /**
     * Creates normalized public metadata for a logical resource.
     *
     * @param ref required logical resource reference
     * @param network resource network; blank values become {@code null}
     * @param capabilities advertised capabilities; {@code null} becomes an empty set
     * @param spendingIdentity canonical contention identity; surrounding
     *                         whitespace is removed and blank values become {@code null}
     */
    public ResourceDescriptor {
        Objects.requireNonNull(ref, "ref");
        capabilities = Set.copyOf(capabilities != null ? capabilities : Set.of());
        if (network != null && network.isBlank()) network = null;
        if (spendingIdentity != null) {
            spendingIdentity = spendingIdentity.trim();
            if (spendingIdentity.isEmpty()) spendingIdentity = null;
        }
    }

    /**
     * Tests whether this descriptor advertises a capability.
     *
     * @param capability capability required by preflight
     * @return whether the capability is present
     */
    public boolean supports(ResourceCapability capability) {
        return capabilities.contains(capability);
    }
}

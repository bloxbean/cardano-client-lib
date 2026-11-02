package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;

import java.util.Set;

/** Strategy that materializes protocol-neutral programmable-token operations. */
public interface ProgrammableTokenProtocol {
    ProgrammableTokenProtocolDescriptor descriptor();

    Set<ProgrammableTokenCapability> capabilities();

    /**
     * Validates metadata that affects protocol dispatch.
     *
     * <p>This default checks the protocol id only; {@code contract_version} is not interpreted
     * here, because what a version means is protocol-specific. A protocol may enforce it — the
     * CIP-113 adapter refuses a plan written for another contract suite. Exact deployments are
     * selected and checked using their deployment metadata.</p>
     *
     * @param metadata persisted extension metadata to validate
     */
    default void validateMetadata(ExtensionMetadata metadata) {
        ProgrammableTokenProtocolDescriptor descriptor = descriptor();
        if (!descriptor.getId().equals(metadata.getProtocol()))
            throw new IllegalArgumentException("Expected protocol " + descriptor.getId()
                    + " but got " + metadata.getProtocol());
    }

    TxBuildExtension newBuildExtension(ExtensionMetadata metadata);
}

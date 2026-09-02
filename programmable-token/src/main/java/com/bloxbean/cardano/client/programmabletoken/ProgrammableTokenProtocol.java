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
     * <p>{@code contract_version} is currently descriptive provenance only. It is deliberately
     * not validated here because CIP-113 does not yet define how a public deployment exposes a
     * trustworthy contract-suite version. Exact deployments are selected and checked using their
     * deployment metadata instead.</p>
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

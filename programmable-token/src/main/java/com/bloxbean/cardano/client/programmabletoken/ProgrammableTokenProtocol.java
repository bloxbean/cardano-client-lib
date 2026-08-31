package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;

import java.util.Set;

/** Strategy that materializes protocol-neutral programmable-token operations. */
public interface ProgrammableTokenProtocol {
    ProgrammableTokenProtocolDescriptor descriptor();

    Set<ProgrammableTokenCapability> capabilities();

    default void validateMetadata(ExtensionMetadata metadata) {
        ProgrammableTokenProtocolDescriptor descriptor = descriptor();
        if (!descriptor.getId().equals(metadata.getProtocol()))
            throw new IllegalArgumentException("Expected protocol " + descriptor.getId()
                    + " but got " + metadata.getProtocol());
        if (metadata.getContractVersion() != null
                && !descriptor.getContractVersion().equals(metadata.getContractVersion()))
            throw new IllegalArgumentException("Unsupported contract_version "
                    + metadata.getContractVersion() + " for protocol " + descriptor.getId());
    }

    TxBuildExtension newBuildExtension(ExtensionMetadata metadata);
}

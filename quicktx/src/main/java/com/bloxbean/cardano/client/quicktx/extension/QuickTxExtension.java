package com.bloxbean.cardano.client.quicktx.extension;

import java.util.Set;

/** Builder-scoped extension descriptor and build-participant factory. */
public interface QuickTxExtension {
    String id();

    String schemaVersion();

    Set<String> operations();

    default ExtensionMetadata metadata() {
        return ExtensionMetadata.builder()
                .extension(id())
                .schemaVersion(schemaVersion())
                .build();
    }

    default int order() {
        return 0;
    }

    TxBuildExtension newBuildExtension();

    /** Validate persisted metadata before any build or backend access. */
    default void validateMetadata(ExtensionMetadata metadata) {
        if (metadata == null)
            throw new IllegalArgumentException("Extension metadata is required for " + id());
        if (!id().equals(metadata.getExtension()))
            throw new IllegalArgumentException("Expected extension " + id() + " but got "
                    + metadata.getExtension());
        if (!schemaVersion().equals(metadata.getSchemaVersion()))
            throw new IllegalArgumentException("Unsupported schema_version "
                    + metadata.getSchemaVersion() + " for extension " + id());
    }

    /** Create a participant bound to the metadata persisted in this particular plan. */
    default TxBuildExtension newBuildExtension(ExtensionMetadata metadata) {
        validateMetadata(metadata);
        return newBuildExtension();
    }
}

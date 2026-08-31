package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import lombok.Builder;

import java.util.Set;

/** QuickTx/TxPlan extension for the stable Programmable Token domain. */
public final class ProgrammableTokenExtension implements QuickTxExtension {
    public static final String ID = "programmable-token";
    public static final String DEFAULT_NAMESPACE = "pt";
    public static final String SCHEMA_VERSION = "1";
    private static final Set<String> OPERATIONS = Set.of(
            "transfer", "mint", "burn", "third_party_transfer", "register",
            "update_registry", "unfrack");

    private final ProgrammableTokenProtocol protocol;
    private final ExtensionMetadata metadata;

    @Builder
    public ProgrammableTokenExtension(ProgrammableTokenProtocol protocol,
                                      java.util.Map<String, Object> deployment) {
        if (protocol == null) throw new IllegalArgumentException("protocol is required");
        this.protocol = protocol;
        this.metadata = ExtensionMetadata.builder()
                .extension(ID)
                .schemaVersion(SCHEMA_VERSION)
                .protocol(protocol.descriptor().getId())
                .contractVersion(protocol.descriptor().getContractVersion())
                .deployment(deployment == null ? new java.util.LinkedHashMap<>()
                        : new java.util.LinkedHashMap<>(deployment))
                .build();
    }

    @Override public String id() { return ID; }
    @Override public String schemaVersion() { return SCHEMA_VERSION; }
    @Override public Set<String> operations() { return OPERATIONS; }
    @Override public ExtensionMetadata metadata() { return metadata.toBuilder().build(); }

    @Override
    public TxBuildExtension newBuildExtension() {
        return protocol.newBuildExtension(metadata());
    }

    @Override
    public void validateMetadata(ExtensionMetadata metadata) {
        QuickTxExtension.super.validateMetadata(metadata);
        protocol.validateMetadata(metadata);
    }

    @Override
    public TxBuildExtension newBuildExtension(ExtensionMetadata metadata) {
        validateMetadata(metadata);
        return protocol.newBuildExtension(metadata);
    }

    public TxPlan configure(TxPlan plan) {
        return configure(plan, DEFAULT_NAMESPACE);
    }

    public TxPlan configure(TxPlan plan, String namespace) {
        if (plan == null) throw new IllegalArgumentException("plan is required");
        return plan.withExtension(namespace, metadata());
    }
}

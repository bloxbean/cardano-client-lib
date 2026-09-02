package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCapability;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenProtocol;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenProtocolDescriptor;
import com.bloxbean.cardano.client.programmabletoken.cip113.tx.Cip113BuildExtension;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;

import java.util.EnumSet;
import java.util.Set;

/** CIP-113 protocol adapter tested against reference contract suite 0.5.0-alpha.2. */
public final class Cip113Protocol implements ProgrammableTokenProtocol {
    public static final String ID = "cip-113";

    /**
     * Informational version from the vendored CIP-113 reference implementation blueprint.
     *
     * <p>This is not a version discovered from, or required of, an on-chain deployment. Runtime
     * compatibility is anchored by the explicit {@link Cip113Deployment} until CIP-113 defines a
     * public deployment/version discovery mechanism.</p>
     */
    public static final String CONTRACT_VERSION = "0.5.0-alpha.2";

    private final Cip113ProtocolService service;

    public Cip113Protocol(Cip113ProtocolService service) {
        this.service = java.util.Objects.requireNonNull(service, "service");
    }

    @Override
    public ProgrammableTokenProtocolDescriptor descriptor() {
        return ProgrammableTokenProtocolDescriptor.builder()
                .id(ID).contractVersion(CONTRACT_VERSION).build();
    }

    @Override
    public Set<ProgrammableTokenCapability> capabilities() {
        return java.util.Collections.unmodifiableSet(EnumSet.of(
                ProgrammableTokenCapability.TRANSFER,
                ProgrammableTokenCapability.MINT,
                ProgrammableTokenCapability.BURN,
                ProgrammableTokenCapability.THIRD_PARTY_TRANSFER,
                ProgrammableTokenCapability.REGISTER,
                ProgrammableTokenCapability.UPDATE_REGISTRY,
                ProgrammableTokenCapability.INLINE_DATUM,
                ProgrammableTokenCapability.GLOBAL_STATE));
    }

    @Override
    public void validateMetadata(ExtensionMetadata metadata) {
        ProgrammableTokenProtocol.super.validateMetadata(metadata);
        Object bootstrap = metadata.getDeployment() == null ? null
                : metadata.getDeployment().get("bootstrap_tx");
        String configuredBootstrap = service.deployment().getBootstrapTxHash();
        if (bootstrap != null && configuredBootstrap != null
                && !configuredBootstrap.equalsIgnoreCase(bootstrap.toString()))
            throw new IllegalArgumentException("TxPlan deployment bootstrap_tx " + bootstrap
                    + " does not match configured CIP-113 deployment " + configuredBootstrap);
    }

    @Override
    public TxBuildExtension newBuildExtension(ExtensionMetadata metadata) {
        validateMetadata(metadata);
        return new Cip113BuildExtension(service, capabilities());
    }
}

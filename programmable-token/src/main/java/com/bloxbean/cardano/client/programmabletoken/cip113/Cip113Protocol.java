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
                ProgrammableTokenCapability.UNFRACK,
                ProgrammableTokenCapability.INLINE_DATUM,
                ProgrammableTokenCapability.GLOBAL_STATE));
    }

    /**
     * Pin a persisted plan to the configured deployment before any chain I/O.
     *
     * <p>{@code bootstrap_tx}, when present, must be a transaction hash equal to the configured
     * deployment's; {@code network}, when present, must be the configured network id. Omitting
     * either means "bind to the explicitly configured runtime deployment". Both are compared as
     * text so a plan whose {@code network} resolved to a number behaves the same as one that
     * wrote it as a string.</p>
     */
    @Override
    public void validateMetadata(ExtensionMetadata metadata) {
        ProgrammableTokenProtocol.super.validateMetadata(metadata);
        java.util.Map<String, Object> deployment = metadata.getDeployment();
        if (deployment == null) return;
        Cip113Deployment configured = service.deployment();

        Object bootstrap = deployment.get("bootstrap_tx");
        if (bootstrap != null) {
            String hash = String.valueOf(bootstrap).trim();
            if (!hash.matches("(?i)[0-9a-f]{64}"))
                throw new IllegalArgumentException("TxPlan deployment bootstrap_tx must be a"
                        + " 32-byte transaction hash but was '" + bootstrap + "'");
            if (configured.getBootstrapTxHash() != null
                    && !configured.getBootstrapTxHash().equalsIgnoreCase(hash))
                throw new IllegalArgumentException("TxPlan deployment bootstrap_tx " + hash
                        + " does not match configured CIP-113 deployment "
                        + configured.getBootstrapTxHash());
        }

        Object network = deployment.get("network");
        if (network != null && configured.getNetwork() != null) {
            String expected = String.valueOf(configured.getNetwork().getNetworkId());
            if (!expected.equals(String.valueOf(network).trim()))
                throw new IllegalArgumentException("TxPlan deployment network '" + network
                        + "' does not match configured CIP-113 deployment network id " + expected);
        }
    }

    @Override
    public TxBuildExtension newBuildExtension(ExtensionMetadata metadata) {
        validateMetadata(metadata);
        return new Cip113BuildExtension(service, capabilities());
    }
}

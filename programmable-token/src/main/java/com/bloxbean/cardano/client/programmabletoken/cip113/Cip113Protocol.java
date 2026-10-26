package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCapability;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenProtocol;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenProtocolDescriptor;
import com.bloxbean.cardano.client.programmabletoken.cip113.tx.Cip113BuildExtension;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;

import java.util.EnumSet;
import java.util.Set;

/** CIP-113 protocol adapter tested against CIP-113 core contract suite 0.0.1. */
public final class Cip113Protocol implements ProgrammableTokenProtocol {
    public static final String ID = "cip-113";

    /**
     * Version from the vendored CIP-113 reference implementation blueprint.
     *
     * <p>This is not a version discovered from an on-chain deployment, but persisted plans that
     * name another contract suite are refused: they describe transaction shapes this adapter does
     * not build. Runtime compatibility is otherwise anchored by the explicit
     * {@link Cip113Deployment}.</p>
     */
    public static final String CONTRACT_VERSION = "0.0.1";

    private final Cip113ProtocolService service;

    public Cip113Protocol(Cip113ProtocolService service) {
        this.service = java.util.Objects.requireNonNull(service, "service");
    }

    @Override
    public ProgrammableTokenProtocolDescriptor descriptor() {
        return ProgrammableTokenProtocolDescriptor.builder()
                .id(ID).contractVersion(CONTRACT_VERSION).build();
    }

    /**
     * Unfracking is temporarily unsupported, including on custom deployments whose dispatcher
     * enables it. The deployment flag describes the on-chain scripts, not adapter support.
     */
    @Override
    public Set<ProgrammableTokenCapability> capabilities() {
        EnumSet<ProgrammableTokenCapability> capabilities = EnumSet.of(
                ProgrammableTokenCapability.TRANSFER,
                ProgrammableTokenCapability.MINT,
                ProgrammableTokenCapability.BURN,
                ProgrammableTokenCapability.THIRD_PARTY_TRANSFER,
                ProgrammableTokenCapability.REGISTER,
                ProgrammableTokenCapability.UPDATE_REGISTRY,
                ProgrammableTokenCapability.INLINE_DATUM,
                ProgrammableTokenCapability.GLOBAL_STATE);
        return java.util.Collections.unmodifiableSet(capabilities);
    }

    /**
     * Pin a persisted plan to the configured deployment before any chain I/O.
     *
     * <p>{@code contract_version}, when present, must be {@link #CONTRACT_VERSION}.
     * {@code bootstrap_tx}, {@code params_policy} and {@code smart_wallet_key}, when present, must
     * equal the configured deployment's; {@code network}, when present, must be the configured network id. Omitting
     * any of them means "bind to the explicitly configured runtime deployment". Both are compared as
     * text so a plan whose {@code network} resolved to a number behaves the same as one that
     * wrote it as a string.</p>
     */
    @Override
    public void validateMetadata(ExtensionMetadata metadata) {
        ProgrammableTokenProtocol.super.validateMetadata(metadata);
        String contractVersion = metadata.getContractVersion();
        if (contractVersion != null && !CONTRACT_VERSION.equals(contractVersion.trim()))
            throw new IllegalArgumentException("TxPlan contract_version '" + contractVersion
                    + "' is not the CIP-113 contract suite this adapter builds for ("
                    + CONTRACT_VERSION + "). Plans written for another suite describe other"
                    + " transaction shapes; re-author the plan for " + CONTRACT_VERSION + ".");
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

        Object paramsPolicy = deployment.get("params_policy");
        if (paramsPolicy != null) {
            String policy = String.valueOf(paramsPolicy).trim();
            if (!policy.matches("(?i)[0-9a-f]{56}"))
                throw new IllegalArgumentException("TxPlan deployment params_policy must be a"
                        + " 28-byte policy id but was '" + paramsPolicy + "'");
            if (configured.getParamsPolicy() != null
                    && !configured.getParamsPolicy().equalsIgnoreCase(policy))
                throw new IllegalArgumentException("TxPlan deployment params_policy " + policy
                        + " does not match configured CIP-113 deployment "
                        + configured.getParamsPolicy());
        }

        // The wallet a plan's sender spends from depends on it: a plan written for one key and
        // replayed under the other would silently act on a different wallet.
        Object walletKey = deployment.get("smart_wallet_key");
        if (walletKey != null && (configured.getSmartWalletKey() == null || !configured.getSmartWalletKey().name()
                .equalsIgnoreCase(String.valueOf(walletKey).trim())))
            throw new IllegalArgumentException("TxPlan deployment smart_wallet_key '" + walletKey
                    + "' does not match configured CIP-113 deployment " + configured.getSmartWalletKey());

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

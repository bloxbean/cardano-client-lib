package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenCapability;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenProtocolDescriptor;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenService;
import com.bloxbean.cardano.client.programmabletoken.cip113.tx.DefaultCip113ProtocolService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Recommended composition-based service for the default CIP-113 protocol. */
public final class Cip113ProgrammableTokenService implements ProgrammableTokenService {
    private final Cip113ProtocolService advanced;
    private final Cip113Protocol protocol;
    private final ProgrammableTokenExtension extension;

    public static Cip113ProgrammableTokenService create(BackendService backend,
                                                        Cip113Deployment deployment) {
        return new Cip113ProgrammableTokenService(
                new DefaultCip113ProtocolService(backend, deployment));
    }

    public Cip113ProgrammableTokenService(Cip113ProtocolService advanced) {
        this.advanced = java.util.Objects.requireNonNull(advanced, "advanced");
        this.protocol = new Cip113Protocol(advanced);
        Cip113Deployment deployment = advanced.deployment();
        Map<String, Object> deploymentMetadata = new LinkedHashMap<>();
        if (deployment.getNetwork() != null)
            deploymentMetadata.put("network", deployment.getNetwork().getNetworkId());
        if (deployment.getBootstrapTxHash() != null)
            deploymentMetadata.put("bootstrap_tx", deployment.getBootstrapTxHash());
        this.extension = ProgrammableTokenExtension.builder()
                .protocol(protocol)
                .deployment(deploymentMetadata)
                .build();
    }

    @Override public ProgrammableTokenProtocolDescriptor protocol() { return protocol.descriptor(); }
    @Override public Set<ProgrammableTokenCapability> capabilities() { return protocol.capabilities(); }
    @Override public ProgrammableTokenExtension extension() { return extension; }
    public Cip113ProtocolService advanced() { return advanced; }
}

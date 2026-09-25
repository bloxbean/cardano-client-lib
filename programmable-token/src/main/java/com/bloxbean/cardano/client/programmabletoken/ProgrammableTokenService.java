package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;

import java.util.Set;

/** Recommended protocol-neutral application entry point. */
public interface ProgrammableTokenService {
    ProgrammableTokenProtocolDescriptor protocol();
    Set<ProgrammableTokenCapability> capabilities();
    ProgrammableTokenExtension extension();

    /**
     * Create a plan codec using the conventional {@code pt} document namespace.
     *
     * @return a codec bound to this service's configured extension
     */
    default TxPlanCodec txPlanCodec() {
        return txPlanCodec(ProgrammableTokenExtension.DEFAULT_NAMESPACE);
    }

    /**
     * Create a plan codec using a caller-selected document namespace.
     *
     * @param namespace document-local namespace for programmable-token intents
     * @return a codec bound to this service's configured extension
     */
    default TxPlanCodec txPlanCodec(String namespace) {
        return TxPlanCodec.builder().withExtension(namespace, extension()).build();
    }
}

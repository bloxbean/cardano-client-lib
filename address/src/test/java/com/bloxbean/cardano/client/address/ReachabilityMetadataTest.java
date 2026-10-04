package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.test.graalvm.ReachabilityMetadataGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GraalVM (Native Image / Web Image) reachability metadata shipped in this module's jar.
 * {@link Credential} is reached by Jackson through transaction-spec certificates and governance types.
 * See {@link ReachabilityMetadataGuard} for the rules.
 */
class ReachabilityMetadataTest {

    @Test
    void metadataIsUpToDate() {
        ReachabilityMetadataGuard guard = ReachabilityMetadataGuard.of(Credential.class, "cardano-client-address")
                .types(Credential.class.getName(), CredentialType.class.getName());
        assertThat(guard.problems()).as("reachability metadata %s is out of date", guard.metadataPath()).isEmpty();
    }
}

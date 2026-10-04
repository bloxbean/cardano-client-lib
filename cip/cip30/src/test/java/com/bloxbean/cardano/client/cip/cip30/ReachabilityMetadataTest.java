package com.bloxbean.cardano.client.cip.cip30;

import com.bloxbean.cardano.client.test.graalvm.ReachabilityMetadataGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GraalVM (Native Image / Web Image) reachability metadata shipped in this module's jar.
 * {@link DataSignature} is (de)serialized by Jackson through its private fields.
 * See {@link ReachabilityMetadataGuard} for the rules.
 */
class ReachabilityMetadataTest {

    @Test
    void metadataIsUpToDate() {
        ReachabilityMetadataGuard guard = ReachabilityMetadataGuard.of(DataSignature.class, "cardano-client-cip30")
                .types(DataSignature.class.getName());
        assertThat(guard.problems()).as("reachability metadata %s is out of date", guard.metadataPath()).isEmpty();
    }
}

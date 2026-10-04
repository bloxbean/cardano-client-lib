package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.test.graalvm.ReachabilityMetadataGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GraalVM (Native Image / Web Image) reachability metadata shipped in this module's jar.
 * {@code TxPlan} is never handed to Jackson: it is built from, and written as, a {@code TransactionDocument}.
 * See {@link ReachabilityMetadataGuard} for the rules.
 */
class ReachabilityMetadataTest {

    @Test
    void metadataIsUpToDate() {
        ReachabilityMetadataGuard guard = ReachabilityMetadataGuard.of(QuickTxBuilder.class, "cardano-client-quicktx")
                .packages("com.bloxbean.cardano.client.quicktx.intent", "com.bloxbean.cardano.client.quicktx.serialization")
                .exclude("com.bloxbean.cardano.client.quicktx.serialization.TxPlan");
        assertThat(guard.problems()).as("reachability metadata %s is out of date", guard.metadataPath()).isEmpty();
    }
}

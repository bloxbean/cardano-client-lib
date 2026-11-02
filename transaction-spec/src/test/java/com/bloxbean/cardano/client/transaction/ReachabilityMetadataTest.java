package com.bloxbean.cardano.client.transaction;

import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.test.graalvm.ReachabilityMetadataGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GraalVM (Native Image / Web Image) reachability metadata shipped in this module's jar.
 * The native script codec and evaluator are internal and never bound by Jackson.
 * See {@link ReachabilityMetadataGuard} for the rules.
 */
class ReachabilityMetadataTest {

    @Test
    void metadataIsUpToDate() {
        ReachabilityMetadataGuard guard = ReachabilityMetadataGuard.of(Transaction.class, "cardano-client-transaction-spec")
                .recursivePackages("com.bloxbean.cardano.client.transaction.spec")
                .types("com.bloxbean.cardano.client.transaction.Nonce")
                .exclude("com.bloxbean.cardano.client.transaction.spec.script.NativeScriptCodec",
                        "com.bloxbean.cardano.client.transaction.spec.script.NativeScriptEvaluator");
        assertThat(guard.problems()).as("reachability metadata %s is out of date", guard.metadataPath()).isEmpty();
    }
}

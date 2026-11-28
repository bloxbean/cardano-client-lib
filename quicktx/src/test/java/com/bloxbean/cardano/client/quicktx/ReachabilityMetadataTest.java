package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.test.graalvm.ReachabilityMetadataGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GraalVM (Native Image / Web Image) reachability metadata shipped in this module's jar.
 * {@code TxPlan} is never handed to Jackson: it is built from, and written as, a {@code TransactionDocument}.
 * {@code TxPlanCodec} and {@code StrictReading} only configure mappers, and {@code PlutusDataValue} is read and written by its owning intents
 * ({@code @JsonIgnore} fields). {@link ExtensionMetadata} is bound through its Lombok builder as
 * {@code TransactionDocument.extensions}. See {@link ReachabilityMetadataGuard} for the rules.
 */
class ReachabilityMetadataTest {

    @Test
    void metadataIsUpToDate() {
        ReachabilityMetadataGuard guard = ReachabilityMetadataGuard.of(QuickTxBuilder.class, "cardano-client-quicktx")
                .packages("com.bloxbean.cardano.client.quicktx.intent", "com.bloxbean.cardano.client.quicktx.serialization")
                .types(ExtensionMetadata.class.getName())
                .exclude("com.bloxbean.cardano.client.quicktx.serialization.TxPlan",
                        "com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec",
                        "com.bloxbean.cardano.client.quicktx.serialization.StrictReading",
                        "com.bloxbean.cardano.client.quicktx.serialization.StrictReading$CoreIntentFieldsAreKnown",
                        "com.bloxbean.cardano.client.quicktx.serialization.StrictReading$DiscriminatorIsKnown",
                        "com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer$RoundTripQuotingChecker",
                        "com.bloxbean.cardano.client.quicktx.intent.CborItems",
                        "com.bloxbean.cardano.client.quicktx.intent.PlutusDataValue");
        assertThat(guard.problems()).as("reachability metadata %s is out of date", guard.metadataPath()).isEmpty();
    }
}

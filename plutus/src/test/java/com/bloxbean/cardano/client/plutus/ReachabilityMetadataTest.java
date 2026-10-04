package com.bloxbean.cardano.client.plutus;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.test.graalvm.ReachabilityMetadataGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GraalVM (Native Image / Web Image) reachability metadata shipped in this module's jar.
 * The Plutus data codec and its map are internal and never bound by Jackson ({@code MapPlutusData} has its own
 * serializer and deserializer).
 * See {@link ReachabilityMetadataGuard} for the rules.
 */
class ReachabilityMetadataTest {

    @Test
    void metadataIsUpToDate() {
        ReachabilityMetadataGuard guard = ReachabilityMetadataGuard.of(PlutusData.class, "cardano-client-plutus")
                .recursivePackages("com.bloxbean.cardano.client.plutus.spec")
                .packages("com.bloxbean.cardano.client.plutus.blueprint.model")
                .exclude("com.bloxbean.cardano.client.plutus.spec.PlutusDataCodec",
                        "com.bloxbean.cardano.client.plutus.spec.PlutusDataMap");
        assertThat(guard.problems()).as("reachability metadata %s is out of date", guard.metadataPath()).isEmpty();
    }
}

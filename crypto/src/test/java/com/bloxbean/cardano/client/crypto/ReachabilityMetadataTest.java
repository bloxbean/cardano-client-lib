package com.bloxbean.cardano.client.crypto;

import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.crypto.cip1852.Segment;
import com.bloxbean.cardano.client.test.graalvm.ReachabilityMetadataGuard;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the GraalVM (Native Image / Web Image) reachability metadata shipped in this module's jar: the BIP39 word
 * list that {@link MnemonicCode} loads from the class path, and the key and derivation path types other modules'
 * Jackson-bound models reach ({@code Policy.policyKeys}, {@code WalletUtxo.derivationPath}, key files).
 */
class ReachabilityMetadataTest {

    private final ReachabilityMetadataGuard guard = ReachabilityMetadataGuard.of(MnemonicCode.class, "cardano-client-crypto")
            .types(SecretKey.class.getName(), VerificationKey.class.getName(), DerivationPath.class.getName(),
                    Segment.class.getName());

    @Test
    void includesBip39WordListResource() {
        JsonNode metadata = guard.metadata();

        assertThat(metadata.get("resources")).anySatisfy(resource -> {
            String glob = resource.get("glob").asText();
            assertThat(getClass().getResource("/" + glob)).as(glob).isNotNull();
            assertThat(resource.path("condition").path("typeReached").asText()).isEqualTo(MnemonicCode.class.getName());
        });
    }

    @Test
    void metadataIsUpToDate() {
        assertThat(guard.problems()).as("reachability metadata %s is out of date", guard.metadataPath()).isEmpty();
    }
}

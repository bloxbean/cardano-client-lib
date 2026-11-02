package com.bloxbean.cardano.client.metadata.cbor;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The model keeps one entry per key, where the ledger keeps every pair of a metadatum map (ADR 0001 D6): documented as
 * lossy, and the original bytes keep everything.
 */
class MetadataDuplicateKeysTest {

    @Test
    void repeatedLabelKeepsFirstPositionAndLastValue() {
        // {1: "a", 2: "x", 1: "b"}: Shelley to Babbage take the last value; Conway rejects the duplicate label
        byte[] bytes = decodeHexString("a3" + "016161" + "026178" + "016162");
        CBORMetadata metadata = CBORMetadata.deserialize(bytes);
        assertThat(metadata.keys()).containsExactly(BigInteger.ONE, BigInteger.TWO);
        assertThat(metadata.get(BigInteger.ONE)).isEqualTo("b");
        assertThat(CborSpan.of(bytes).entries()).hasSize(3);
    }

    @Test
    void metadatumMapKeepsOneEntryPerKeyWhileTheBytesKeepEveryPair() {
        // {7: {"k": 1, "k": 2}}: on chain a list of two pairs
        byte[] bytes = decodeHexString("a1" + "07" + "a2" + "616b01" + "616b02");
        CBORMetadata metadata = CBORMetadata.deserialize(bytes);
        CBORMetadataMap inner = (CBORMetadataMap) metadata.get(BigInteger.valueOf(7));
        assertThat(inner.keys()).containsExactly("k");
        assertThat(inner.get("k")).isEqualTo(BigInteger.TWO);
        assertThat(CborSpan.of(bytes).entries().get(0).getValue().entries()).hasSize(2);

        // so the model re-encodes to other bytes, and its hash is not the hash of what was received
        assertThat(encodeHexString(metadata.serialize())).isEqualTo("a1" + "07" + "a1" + "616b02");
        assertThat(metadata.getMetadataHash()).isNotEqualTo(Blake2bUtil.blake2bHash256(bytes));
    }
}

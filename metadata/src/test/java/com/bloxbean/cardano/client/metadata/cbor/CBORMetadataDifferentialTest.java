package com.bloxbean.cardano.client.metadata.cbor;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CBORMetadata#deserialize(byte[])} on the iterative decoder against cbor-java's, on the metadata of every real
 * transaction and block: equal trees, the same serialization, hash and JSON.
 */
class CBORMetadataDifferentialTest {

    @Test
    void realMetadataDecodesAsWithCborJava() throws Exception {
        int compared = 0;
        int canonical = 0;
        for (MetadataCorpus.Item item : MetadataCorpus.all()) {
            CBORMetadata legacy = CBORMetadata.deserialize((Map) CborDecoder.decode(item.cbor()).get(0));
            CBORMetadata decoded = CBORMetadata.deserialize(item.cbor());
            assertThat(decoded.getData().equals(legacy.getData())).as(item.toString()).isTrue();
            assertThat(decoded.serialize()).as(item.toString()).isEqualTo(legacy.serialize());
            assertThat(decoded.getMetadataHash()).as(item.toString()).isEqualTo(legacy.getMetadataHash());
            assertThat(json(decoded::toJson)).as(item.toString()).isEqualTo(json(legacy::toJson));
            if (Arrays.equals(decoded.serialize(), item.cbor())) {
                assertThat(decoded.getMetadataHash()).isEqualTo(Blake2bUtil.blake2bHash256(item.cbor()));
                canonical++;
            }
            compared++;
        }
        assertThat(compared).isGreaterThan(50);
        assertThat(canonical).isPositive();
        System.out.println("metadata compared: " + compared + ", re-encoding to the original bytes: " + canonical);
    }

    // The JSON, or the failure, which must be the same on both paths.
    private static String json(Supplier<String> toJson) {
        try {
            return toJson.get();
        } catch (RuntimeException e) {
            return "failed: " + e.getClass().getName();
        }
    }
}

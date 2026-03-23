package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.MetadataAdapterResolver;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Codegen tests for the generated {@code SampleEncoderDecoderMetadataConverter}.
 * Verifies encoder-only, decoder-only, both, and resolver constructor behavior.
 */
class SampleEncoderDecoderMetadataConverterTest {

    // =========================================================================
    // Encoder-only field
    // =========================================================================

    @Nested
    class EncoderOnly {

        @Test
        void serialization_usesEncoder() {
            var converter = new SampleEncoderDecoderMetadataConverter();
            SampleEncoderDecoder obj = new SampleEncoderDecoder();
            obj.setEncodedOnly(42L);

            MetadataMap map = converter.toMetadataMap(obj);

            // MultiplierEncoder multiplies by 1000
            assertEquals(BigInteger.valueOf(42_000L), map.get("encoded_only"));
        }

        @Test
        void deserialization_fallsBackToBuiltIn() {
            var converter = new SampleEncoderDecoderMetadataConverter();

            // Serialize with encoder (42 → 42000), then deserialize
            SampleEncoderDecoder obj = new SampleEncoderDecoder();
            obj.setEncodedOnly(42L);
            MetadataMap map = converter.toMetadataMap(obj);

            // Deserialization uses built-in long handling → reads 42000 as-is
            SampleEncoderDecoder restored = converter.fromMetadataMap(map);
            assertEquals(42_000L, restored.getEncodedOnly(),
                    "Encoder-only: deserialization should read the raw value (42000) via built-in handling");
        }
    }

    // =========================================================================
    // Decoder-only field
    // =========================================================================

    @Nested
    class DecoderOnly {

        @Test
        void serialization_fallsBackToBuiltIn() {
            var converter = new SampleEncoderDecoderMetadataConverter();
            SampleEncoderDecoder obj = new SampleEncoderDecoder();
            obj.setDecodedOnly(42_000L);

            MetadataMap map = converter.toMetadataMap(obj);

            // Built-in long serialization → BigInteger.valueOf(42000)
            assertEquals(BigInteger.valueOf(42_000L), map.get("decoded_only"));
        }

        @Test
        void deserialization_usesDecoder() {
            var converter = new SampleEncoderDecoderMetadataConverter();

            // Manually set 42000 in map, decoder should divide by 1000
            SampleEncoderDecoder obj = new SampleEncoderDecoder();
            obj.setDecodedOnly(42_000L);
            MetadataMap map = converter.toMetadataMap(obj);

            SampleEncoderDecoder restored = converter.fromMetadataMap(map);
            assertEquals(42L, restored.getDecodedOnly(),
                    "Decoder-only: deserialization should use MultiplierDecoder (÷1000)");
        }
    }

    // =========================================================================
    // Both encoder and decoder
    // =========================================================================

    @Nested
    class BothDirections {

        @Test
        void roundTrip() {
            var converter = new SampleEncoderDecoderMetadataConverter();
            SampleEncoderDecoder obj = new SampleEncoderDecoder();
            obj.setBothDirections(42L);

            MetadataMap map = converter.toMetadataMap(obj);

            // Encoder: 42 × 1000 = 42000
            assertEquals(BigInteger.valueOf(42_000L), map.get("both"));

            // Decoder: 42000 ÷ 1000 = 42
            SampleEncoderDecoder restored = converter.fromMetadataMap(map);
            assertEquals(42L, restored.getBothDirections(),
                    "Both directions should round-trip: encode(×1000) then decode(÷1000)");
        }
    }

    // =========================================================================
    // Plain field (no encoder/decoder)
    // =========================================================================

    @Nested
    class PlainField {

        @Test
        void roundTrip() {
            var converter = new SampleEncoderDecoderMetadataConverter();
            SampleEncoderDecoder obj = new SampleEncoderDecoder();
            obj.setPlainValue(99L);

            SampleEncoderDecoder restored = converter.fromMetadataMap(converter.toMetadataMap(obj));
            assertEquals(99L, restored.getPlainValue());
        }
    }

    // =========================================================================
    // Resolver constructor
    // =========================================================================

    @Nested
    class ResolverConstructor {

        @Test
        void noArgConstructor_works() {
            // Should not throw — uses DefaultAdapterResolver
            var converter = new SampleEncoderDecoderMetadataConverter();
            assertNotNull(converter);
        }

        @Test
        void resolverConstructor_resolvesAdapters() {
            AtomicInteger resolveCount = new AtomicInteger(0);
            MetadataAdapterResolver resolver = new MetadataAdapterResolver() {
                @Override
                @SuppressWarnings("unchecked")
                public <T> T resolve(Class<T> adapterClass) {
                    resolveCount.incrementAndGet();
                    try {
                        return adapterClass.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };

            var converter = new SampleEncoderDecoderMetadataConverter(resolver);
            assertNotNull(converter);
            assertTrue(resolveCount.get() > 0,
                    "Resolver should have been called at least once");
        }

        @Test
        void resolverConstructor_functionallySameAsNoArg() {
            var noArgConverter = new SampleEncoderDecoderMetadataConverter();
            var resolverConverter = new SampleEncoderDecoderMetadataConverter(
                    new com.bloxbean.cardano.client.metadata.annotation.DefaultAdapterResolver());

            SampleEncoderDecoder obj = new SampleEncoderDecoder();
            obj.setName("test");
            obj.setEncodedOnly(10L);
            obj.setDecodedOnly(20_000L);
            obj.setBothDirections(30L);
            obj.setPlainValue(40L);

            MetadataMap map1 = noArgConverter.toMetadataMap(obj);
            MetadataMap map2 = resolverConverter.toMetadataMap(obj);

            // Both should produce identical metadata
            assertEquals(map1.get("name"), map2.get("name"));
            assertEquals(map1.get("encoded_only"), map2.get("encoded_only"));
            assertEquals(map1.get("decoded_only"), map2.get("decoded_only"));
            assertEquals(map1.get("both"), map2.get("both"));
            assertEquals(map1.get("plain"), map2.get("plain"));
        }
    }
}

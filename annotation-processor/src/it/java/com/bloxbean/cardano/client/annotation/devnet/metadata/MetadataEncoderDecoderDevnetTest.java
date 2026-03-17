package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.MetadataAdapterResolver;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import org.cardanofoundation.conversions.CardanoConverters;
import org.cardanofoundation.conversions.ClasspathConversionsFactory;
import org.cardanofoundation.conversions.domain.NetworkType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code @MetadataEncoder}/{@code @MetadataDecoder} with resolver pattern.
 * <p>
 * Uses stateful adapters ({@link ScaleEncoder}, {@link ScaleDecoder}, {@link SlotToEpochEncoder})
 * that have <strong>no public no-arg constructors</strong>, proving the resolver is essential
 * for adapter instantiation.
 * <p>
 * Also demonstrates real-world usage of
 * <a href="https://github.com/cardano-foundation/cf-cardano-conversions-java">cf-cardano-conversions-java</a>
 * for slot-to-epoch conversion.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MetadataEncoderDecoderDevnetTest extends BaseIT {

    private static final long SCALE_FACTOR = 1000L;

    /** PREPROD mainnet slot 109090938 → epoch 530 (known reference point). */
    private static final long KNOWN_PREPROD_SLOT = 109090938L;
    private static final long KNOWN_PREPROD_EPOCH = 530L;

    private BackendService backendService;
    private MetadataEncoderDecoder original;
    private MetadataEncoderDecoder restored;
    private JsonNode jsonMeta;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        original = buildOriginal();

        // Build resolver providing stateful adapters
        CardanoConverters converters = ClasspathConversionsFactory.createConverters(NetworkType.PREPROD);
        MetadataAdapterResolver resolver = new MetadataAdapterResolver() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T resolve(Class<T> adapterClass) {
                if (adapterClass == ScaleEncoder.class) return (T) new ScaleEncoder(SCALE_FACTOR);
                if (adapterClass == ScaleDecoder.class) return (T) new ScaleDecoder(SCALE_FACTOR);
                if (adapterClass == SlotToEpochEncoder.class) return (T) new SlotToEpochEncoder(converters);
                throw new IllegalArgumentException("Unknown adapter: " + adapterClass.getName());
            }
        };

        var converter = new MetadataEncoderDecoderMetadataConverter(resolver);
        Metadata metadata = converter.toMetadata(original);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(address2, Amount.ada(1.5))
                .attachMetadata(metadata)
                .from(address1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        assertTrue(result.isSuccessful(), "Transaction should succeed: " + result);
        String txHash = result.getValue();

        waitForTransaction(result);

        var jsonResult = backendService.getMetadataService().getJSONMetadataByTxnHash(txHash);
        assertTrue(jsonResult.isSuccessful(), "JSON metadata retrieval should succeed");
        assertFalse(jsonResult.getValue().isEmpty(), "JSON metadata should have entries");

        jsonMeta = findJsonMetadataForLabel(jsonResult.getValue(), "1905");
        assertNotNull(jsonMeta, "JSON metadata for label 1905 should exist");
        System.out.println("[DIAG] JSON metadata for label 1905: " + jsonMeta);

        MetadataMap chainMap = extractMetadataMap(jsonResult.getValue(), "1905");
        restored = converter.fromMetadataMap(chainMap);
    }

    // =========================================================================
    // Encoder-only field
    // =========================================================================

    @Test
    void encoderOnly_serializesWithScale() {
        // Original value 42 × 1000 = 42000 on-chain
        assertEquals(42_000L, jsonMeta.get("encoded_val").asLong(),
                "Encoder should multiply value by scale factor");
    }

    @Test
    void encoderOnly_deserializesRawValue() {
        // Built-in deserialization reads raw 42000
        assertEquals(42_000L, restored.getEncodedValue(),
                "Encoder-only: deserialization should read raw value via built-in handling");
    }

    // =========================================================================
    // Decoder-only field
    // =========================================================================

    @Test
    void decoderOnly_serializesWithBuiltIn() {
        // Built-in serialization stores 42000 as-is
        assertEquals(42_000L, jsonMeta.get("decoded_val").asLong(),
                "Decoder-only: serialization should use built-in handling");
    }

    @Test
    void decoderOnly_deserializesWithScale() {
        // Decoder divides 42000 ÷ 1000 = 42
        assertEquals(42L, restored.getDecodedValue(),
                "Decoder should divide value by scale factor");
    }

    // =========================================================================
    // Both encoder and decoder — full round-trip
    // =========================================================================

    @Test
    void bothDirections_roundTrip() {
        // Encoder: 42 × 1000 = 42000 → chain → Decoder: 42000 ÷ 1000 = 42
        assertEquals(original.getRoundTripValue(), restored.getRoundTripValue(),
                "Both encoder+decoder should round-trip the value");
    }

    @Test
    void bothDirections_onChainValueIsScaled() {
        assertEquals(42_000L, jsonMeta.get("round_trip_val").asLong(),
                "On-chain value should be scaled by encoder");
    }

    // =========================================================================
    // Slot-to-epoch encoder (cf-cardano-conversions-java)
    // =========================================================================

    @Test
    void slotToEpoch_encodesCorrectly() {
        long expectedEpoch = KNOWN_PREPROD_EPOCH;
        assertEquals(expectedEpoch, jsonMeta.get("epoch_from_slot").asLong(),
                "SlotToEpochEncoder should convert PREPROD slot " + KNOWN_PREPROD_SLOT
                        + " to epoch " + expectedEpoch);
    }

    @Test
    void slotToEpoch_deserializesRawEpoch() {
        // Built-in deserialization reads the epoch number as a raw long
        assertEquals(KNOWN_PREPROD_EPOCH, restored.getSlotForEpoch(),
                "Encoder-only: deserialization reads raw epoch number");
    }

    // =========================================================================
    // Plain field — baseline
    // =========================================================================

    @Test
    void plainField_roundTrip() {
        assertEquals(original.getPlainValue(), restored.getPlainValue());
    }

    // ── Raw JSON key existence ──────────────────────────────────────────

    @Test
    void jsonRaw_allKeysPresent() {
        assertTrue(jsonMeta.has("test_id"), "JSON should contain 'test_id'");
        assertTrue(jsonMeta.has("encoded_val"), "JSON should contain 'encoded_val'");
        assertTrue(jsonMeta.has("decoded_val"), "JSON should contain 'decoded_val'");
        assertTrue(jsonMeta.has("round_trip_val"), "JSON should contain 'round_trip_val'");
        assertTrue(jsonMeta.has("epoch_from_slot"), "JSON should contain 'epoch_from_slot'");
        assertTrue(jsonMeta.has("plain_val"), "JSON should contain 'plain_val'");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private MetadataEncoderDecoder buildOriginal() {
        MetadataEncoderDecoder obj = new MetadataEncoderDecoder();
        obj.setTestId("enc-dec-001");
        obj.setEncodedValue(42L);
        obj.setDecodedValue(42_000L);
        obj.setRoundTripValue(42L);
        obj.setSlotForEpoch(KNOWN_PREPROD_SLOT);
        obj.setPlainValue(99L);
        return obj;
    }

    private JsonNode findJsonMetadataForLabel(List<MetadataJSONContent> entries, String label) {
        for (MetadataJSONContent entry : entries) {
            if (label.equals(entry.getLabel())) {
                return entry.getJsonMetadata();
            }
        }
        return null;
    }

    private MetadataMap extractMetadataMap(List<MetadataJSONContent> entries, String label) {
        for (MetadataJSONContent entry : entries) {
            if (label.equals(entry.getLabel())) {
                return JsonNoSchemaToMetadataConverter.parseObjectNode(
                        (ObjectNode) entry.getJsonMetadata());
            }
        }
        fail("No metadata found for label " + label);
        return null;
    }
}

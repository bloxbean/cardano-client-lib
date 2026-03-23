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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code @MetadataEncoder}/{@code @MetadataDecoder} with resolver pattern.
 * <p>
 * Demonstrates two adapter styles:
 * <ul>
 *   <li><b>Stateless:</b> {@link UpperCaseEncoder} — has a no-arg constructor, works without resolver</li>
 *   <li><b>Stateful (context-injected):</b> {@link PrefixEncoder}/{@link PrefixDecoder} — require
 *       an injected prefix string (analogous to {@code NetworkType}, a Spring bean, or any
 *       runtime configuration that cannot be known at compile time)</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MetadataEncoderDecoderDevnetTest extends BaseIT {

    /** Simulates injected context — e.g., a network identifier or Spring-managed config bean. */
    private static final String INJECTED_PREFIX = "cardano:";

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

        // Build resolver providing stateful adapters with injected context.
        // In a real Spring/Quarkus app this would be: ctx.getBean(adapterClass)
        MetadataAdapterResolver resolver = new MetadataAdapterResolver() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T resolve(Class<T> adapterClass) {
                if (adapterClass == PrefixEncoder.class) return (T) new PrefixEncoder(INJECTED_PREFIX);
                if (adapterClass == PrefixDecoder.class) return (T) new PrefixDecoder(INJECTED_PREFIX);
                // Stateless adapters — fall back to no-arg constructor
                try {
                    return adapterClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot instantiate: " + adapterClass.getName(), e);
                }
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
    // Stateless encoder: UpperCaseEncoder (no resolver needed)
    // =========================================================================

    @Test
    void upperCase_encodesCorrectly() {
        assertEquals("HELLO WORLD", jsonMeta.get("upper_name").asText(),
                "UpperCaseEncoder should store value in upper case on-chain");
    }

    @Test
    void upperCase_deserializesRawValue() {
        // Built-in String deserialization reads the upper-cased value as-is
        assertEquals("HELLO WORLD", restored.getUpperName(),
                "Encoder-only: deserialization reads raw upper-cased value");
    }

    // =========================================================================
    // Stateful encoder+decoder: PrefixEncoder/PrefixDecoder (requires resolver)
    // =========================================================================

    @Test
    void prefix_roundTrip() {
        // Encoder: "test-tag" → "cardano:test-tag" → chain → Decoder: strips prefix → "test-tag"
        assertEquals(original.getPrefixedTag(), restored.getPrefixedTag(),
                "Both encoder+decoder should round-trip the value");
    }

    @Test
    void prefix_onChainValueHasPrefix() {
        assertEquals("cardano:test-tag", jsonMeta.get("prefixed_tag").asText(),
                "On-chain value should have injected prefix");
    }

    // =========================================================================
    // Encoder-only with injected context
    // =========================================================================

    @Test
    void encoderOnly_serializesWithPrefix() {
        assertEquals("cardano:encode-me", jsonMeta.get("enc_only_tag").asText(),
                "Encoder should prepend prefix");
    }

    @Test
    void encoderOnly_deserializesRawValue() {
        // Built-in deserialization reads the prefixed string as-is
        assertEquals("cardano:encode-me", restored.getEncoderOnlyTag(),
                "Encoder-only: deserialization reads raw prefixed value");
    }

    // =========================================================================
    // Decoder-only with injected context
    // =========================================================================

    @Test
    void decoderOnly_serializesWithBuiltIn() {
        // Built-in serialization stores the value as-is (already prefixed in the POJO)
        assertEquals("cardano:decode-me", jsonMeta.get("dec_only_tag").asText(),
                "Decoder-only: serialization uses built-in handling");
    }

    @Test
    void decoderOnly_deserializesStrippingPrefix() {
        // Decoder strips "cardano:" prefix
        assertEquals("decode-me", restored.getDecoderOnlyTag(),
                "Decoder should strip injected prefix");
    }

    // =========================================================================
    // Plain field — baseline
    // =========================================================================

    @Test
    void plainField_roundTrip() {
        assertEquals(original.getPlainTag(), restored.getPlainTag());
    }

    // ── Raw JSON key existence ──────────────────────────────────────────

    @Test
    void jsonRaw_allKeysPresent() {
        assertTrue(jsonMeta.has("test_id"), "JSON should contain 'test_id'");
        assertTrue(jsonMeta.has("upper_name"), "JSON should contain 'upper_name'");
        assertTrue(jsonMeta.has("prefixed_tag"), "JSON should contain 'prefixed_tag'");
        assertTrue(jsonMeta.has("enc_only_tag"), "JSON should contain 'enc_only_tag'");
        assertTrue(jsonMeta.has("dec_only_tag"), "JSON should contain 'dec_only_tag'");
        assertTrue(jsonMeta.has("plain_tag"), "JSON should contain 'plain_tag'");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private MetadataEncoderDecoder buildOriginal() {
        MetadataEncoderDecoder obj = new MetadataEncoderDecoder();
        obj.setTestId("enc-dec-001");
        obj.setUpperName("hello world");
        obj.setPrefixedTag("test-tag");
        obj.setEncoderOnlyTag("encode-me");
        obj.setDecoderOnlyTag("cardano:decode-me");  // already prefixed — decoder strips it
        obj.setPlainTag("unchanged");
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

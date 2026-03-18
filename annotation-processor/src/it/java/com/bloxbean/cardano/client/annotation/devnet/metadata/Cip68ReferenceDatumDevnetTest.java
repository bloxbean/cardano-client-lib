package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Cip68ReferenceDatumDevnetTest extends BaseIT {

    private BackendService backendService;
    private Cip68ReferenceDatum original;
    private Cip68ReferenceDatum restored;
    private JsonNode jsonMeta;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        original = buildOriginal();

        var converter = new Cip68ReferenceDatumMetadataConverter();
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

        // Diagnostic: show that the CBOR metadata endpoint returns null cbor_metadata on Yaci Store
        var cborResult = backendService.getMetadataService().getCBORMetadataByTxnHash(txHash);
        System.out.println("[DIAG] CBOR metadata endpoint successful=" + cborResult.isSuccessful()
                + ", entries=" + (cborResult.getValue() != null ? cborResult.getValue().size() : "null"));
        if (cborResult.isSuccessful() && cborResult.getValue() != null) {
            for (MetadataCBORContent entry : cborResult.getValue()) {
                System.out.println("[DIAG]   label=" + entry.getLabel()
                        + ", cbor_metadata=" + (entry.getCborMetadata() == null ? "NULL" : entry.getCborMetadata().substring(0, Math.min(40, entry.getCborMetadata().length())) + "..."));
            }
        }

        var jsonResult = backendService.getMetadataService().getJSONMetadataByTxnHash(txHash);
        assertTrue(jsonResult.isSuccessful(), "JSON metadata retrieval should succeed");
        assertFalse(jsonResult.getValue().isEmpty(), "JSON metadata should have entries");

        // Verify raw JSON values match what was submitted
        jsonMeta = findJsonMetadataForLabel(jsonResult.getValue(), "100");
        assertNotNull(jsonMeta, "JSON metadata for label 100 should exist");
        System.out.println("[DIAG] JSON metadata for label 100: " + jsonMeta);
        assertEquals(original.getName(), jsonMeta.get("name").asText(), "JSON 'name' value mismatch");
        assertEquals(original.getMediaType(), jsonMeta.get("media_type").asText(), "JSON 'media_type' value mismatch");
        assertEquals(original.getMetadataVersion(), jsonMeta.get("int_version").asInt(), "JSON 'int_version' value mismatch");
        assertTrue(jsonMeta.has("image"), "JSON should contain 'image'");
        assertTrue(jsonMeta.has("extra_data"), "JSON should contain 'extra_data'");
        assertTrue(jsonMeta.has("royalty"), "JSON should contain 'royalty'");
        assertTrue(jsonMeta.has("traits"), "JSON should contain 'traits'");

        MetadataMap chainMap = extractMetadataMap(jsonResult.getValue(), "100");
        restored = converter.fromMetadataMap(chainMap);
    }

    @Test
    void fullRoundTrip_nameAndImage() {
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getImage(), restored.getImage());
    }

    @Test
    void chunkedString_image() {
        assertEquals(original.getImage(), restored.getImage());
        assertTrue(original.getImage().length() > 64, "Image should be > 64 bytes for chunking test");
    }

    @Test
    void optionalPresent() {
        assertTrue(restored.getDescription().isPresent());
        assertEquals(original.getDescription().get(), restored.getDescription().get());
    }

    @Test
    void polymorphicField() {
        assertNotNull(restored.getDisplayMedia());
        assertInstanceOf(AudioContent.class, restored.getDisplayMedia());
        AudioContent audio = (AudioContent) restored.getDisplayMedia();
        AudioContent origAudio = (AudioContent) original.getDisplayMedia();
        assertEquals(origAudio.getUrl(), audio.getUrl());
        assertEquals(origAudio.getDuration(), audio.getDuration());
        assertEquals(origAudio.getCodec(), audio.getCodec());
    }

    @Test
    void nestedRecord_royalty() {
        assertNotNull(restored.getRoyalty());
        assertEquals(original.getRoyalty().address(), restored.getRoyalty().address());
        assertEquals(original.getRoyalty().rateBps(), restored.getRoyalty().rateBps());
    }

    @Test
    void defaultValue_metadataVersion() {
        assertEquals(original.getMetadataVersion(), restored.getMetadataVersion());
    }

    @Test
    void base64ByteArray() {
        assertArrayEquals(original.getExtraData(), restored.getExtraData());
    }

    @Test
    void compositeMap_traits() {
        assertEquals(original.getTraits(), restored.getTraits());
    }

    @Test
    void enumList_standards() {
        assertEquals(original.getStandards(), restored.getStandards());
    }

    @Test
    void setField_tags() {
        assertNotNull(restored.getTags());
        assertEquals(original.getTags(), restored.getTags());
    }

    @Test
    void ignoredField() {
        assertEquals(0L, restored.getCachedHash());
    }

    @Test
    void mediaType() {
        assertEquals(original.getMediaType(), restored.getMediaType());
    }

    // ── Raw JSON Assertions ────────────────────────────────────────────

    @Test
    void jsonRaw_chunkedString_imageIsArray() {
        assertTrue(jsonMeta.has("image"), "JSON should contain 'image'");
        JsonNode image = jsonMeta.get("image");
        assertTrue(image.isArray(),
                "Long string (> 64 chars) should be chunked into a JSON array");
        assertEquals(2, image.size(), "83-char string should produce 2 chunks");
        assertEquals("ipfs://QmXyZ123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq",
                image.get(0).asText(), "First chunk should be 64 chars");
        assertEquals("rstuvwxyz0123456789end",
                image.get(1).asText(), "Second chunk should be the remaining 19 chars");
    }

    @Test
    void jsonRaw_base64Encoding_extraData() {
        assertTrue(jsonMeta.has("extra_data"), "JSON should contain 'extra_data'");
        assertEquals("RXh0cmFEYXRhRm9yVGVzdA==", jsonMeta.get("extra_data").asText(),
                "extra_data should be a Base64 string");
    }

    @Test
    void jsonRaw_nestedRecord_royalty() {
        assertTrue(jsonMeta.has("royalty"), "JSON should contain 'royalty'");
        JsonNode royalty = jsonMeta.get("royalty");
        assertEquals("addr_test1qz...", royalty.get("address").asText());
        assertEquals(250, royalty.get("rate_bps").asInt());
    }

    @Test
    void jsonRaw_polymorphicDiscriminator_displayMedia() {
        assertTrue(jsonMeta.has("displayMedia"), "JSON should contain 'displayMedia'");
        JsonNode dm = jsonMeta.get("displayMedia");
        assertEquals("audio", dm.get("type").asText(), "discriminator should be 'audio'");
        assertEquals("ipfs://QmAudio1", dm.get("url").asText());
        assertEquals(240, dm.get("duration").asInt());
        assertEquals("mp3", dm.get("codec").asText());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Cip68ReferenceDatum buildOriginal() {
        Cip68ReferenceDatum datum = new Cip68ReferenceDatum();
        datum.setName("CIP68-RefNFT#001");
        datum.setImage("ipfs://QmXyZ123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789end");
        datum.setDescription(Optional.of("A CIP-68 reference datum for integration testing"));
        datum.setMediaType("audio/mpeg");
        datum.setDisplayMedia(new AudioContent("ipfs://QmAudio1", 240, "mp3"));
        datum.setRoyalty(new RoyaltyInfo("addr_test1qz...", BigInteger.valueOf(250)));
        datum.setMetadataVersion(2);
        datum.setExtraData(Base64.getDecoder().decode("RXh0cmFEYXRhRm9yVGVzdA=="));
        datum.setTraits(Map.of(
                "color", List.of("red", "blue"),
                "size", List.of("large")
        ));
        datum.setStandards(List.of(MetadataStandard.CIP25, MetadataStandard.CIP68));
        datum.setTags(new LinkedHashSet<>(List.of("nft", "reference", "cip68")));
        datum.setCachedHash(999999L);
        return datum;
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

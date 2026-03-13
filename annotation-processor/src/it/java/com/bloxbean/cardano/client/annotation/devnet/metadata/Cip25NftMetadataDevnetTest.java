package com.bloxbean.cardano.client.annotation.devnet.metadata;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Cip25NftMetadataDevnetTest extends BaseIT {

    private BackendService backendService;
    private QuickTxBuilder quickTxBuilder;
    private Cip25NftMetadataMetadataConverter converter;

    private Cip25NftMetadata original;
    private Cip25NftMetadata restored;
    private Cip25NftMetadata originalAbsent;
    private Cip25NftMetadata restoredAbsent;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        quickTxBuilder = new QuickTxBuilder(backendService);
        converter = new Cip25NftMetadataMetadataConverter();

        original = buildOriginal();
        restored = submitAndRetrieve(original);

        originalAbsent = buildOriginalWithOptionalAbsent();
        restoredAbsent = submitAndRetrieve(originalAbsent);
    }

    @Test
    void fullRoundTrip_nameAndImage() {
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getImage(), restored.getImage());
    }

    @Test
    void inheritedFields() {
        assertEquals(original.getVersion(), restored.getVersion());
        assertEquals(original.getAuthor(), restored.getAuthor());
    }

    @Test
    void polymorphicDiscriminator() {
        assertNotNull(restored.getDisplayMedia());
        assertInstanceOf(ImageContent.class, restored.getDisplayMedia());
        ImageContent img = (ImageContent) restored.getDisplayMedia();
        ImageContent origImg = (ImageContent) original.getDisplayMedia();
        assertEquals(origImg.getUrl(), img.getUrl());
        assertEquals(origImg.getWidth(), img.getWidth());
        assertEquals(origImg.getHeight(), img.getHeight());
    }

    @Test
    void hexEncoding() {
        assertArrayEquals(original.getPolicyId(), restored.getPolicyId());
    }

    @Test
    void adapterField() {
        assertEquals(original.getMintedAt(), restored.getMintedAt());
    }

    @Test
    void chunkedString() {
        // image field is > 64 bytes, so it gets chunked
        assertEquals(original.getImage(), restored.getImage());
        assertTrue(original.getImage().length() > 64, "Image should be > 64 bytes for chunking test");
    }

    @Test
    void enumField() {
        assertEquals(original.getRarity(), restored.getRarity());
    }

    @Test
    void ignoredField() {
        assertNull(restored.getInternalTrackingId());
    }

    @Test
    void optionalPresent() {
        assertTrue(restored.getDescription().isPresent());
        assertEquals(original.getDescription().get(), restored.getDescription().get());
    }

    @Test
    void optionalAbsent() {
        assertTrue(restoredAbsent.getDescription().isEmpty(),
                "Optional.empty() should survive chain round-trip as empty");
    }

    @Test
    void defaultValue_mediaType() {
        assertEquals(original.getMediaType(), restored.getMediaType());
    }

    @Test
    void nestedRecordList() {
        assertNotNull(restored.getFiles());
        assertEquals(original.getFiles().size(), restored.getFiles().size());
        for (int i = 0; i < original.getFiles().size(); i++) {
            assertEquals(original.getFiles().get(i).name(), restored.getFiles().get(i).name());
            assertEquals(original.getFiles().get(i).src(), restored.getFiles().get(i).src());
            assertEquals(original.getFiles().get(i).mediaType(), restored.getFiles().get(i).mediaType());
        }
    }

    @Test
    void mapStringString() {
        assertEquals(original.getAttributes(), restored.getAttributes());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    @SneakyThrows
    private Cip25NftMetadata submitAndRetrieve(Cip25NftMetadata nft) {
        Metadata metadata = converter.toMetadata(nft);

        Tx tx = new Tx()
                .payToAddress(address2, Amount.ada(1.5))
                .attachMetadata(metadata)
                .from(address1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        assertTrue(result.isSuccessful(), "Transaction should succeed: " + result);

        waitForTransaction(result);

        var cborResult = backendService.getMetadataService().getCBORMetadataByTxnHash(result.getValue());
        assertTrue(cborResult.isSuccessful(), "Metadata retrieval should succeed");

        MetadataMap chainMap = extractMetadataMap(cborResult.getValue(), "721");
        return converter.fromMetadataMap(chainMap);
    }

    private Cip25NftMetadata buildBase() {
        Cip25NftMetadata nft = new Cip25NftMetadata();
        nft.setVersion("1.0");
        nft.setAuthor("integration-test");
        nft.setMediaType("image/png");
        nft.setPolicyId(HexUtil.decodeHexString("aabbccdd11223344aabbccdd11223344aabbccdd11223344aabbccdd"));
        return nft;
    }

    private Cip25NftMetadata buildOriginal() {
        Cip25NftMetadata nft = buildBase();
        nft.setName("DevnetNFT#001");
        nft.setImage("ipfs://QmXyZ123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789end");
        nft.setDescription(Optional.of("A test NFT for devnet integration"));
        nft.setFiles(List.of(
                new NftFileDetail("thumbnail.png", "ipfs://QmThumb1", "image/png"),
                new NftFileDetail("high-res.png", "ipfs://QmHighRes1", "image/png")
        ));
        nft.setDisplayMedia(new ImageContent("ipfs://QmDisplay1", 1920, 1080));
        nft.setRarity(NftRarity.RARE);
        nft.setMintedAt(Instant.ofEpochSecond(1700000000L));
        nft.setAttributes(Map.of("background", "blue", "eyes", "green"));
        nft.setInternalTrackingId("should-be-ignored");
        return nft;
    }

    private Cip25NftMetadata buildOriginalWithOptionalAbsent() {
        Cip25NftMetadata nft = buildBase();
        nft.setName("DevnetNFT#002-absent");
        nft.setImage("ipfs://QmShortImage");
        nft.setDescription(Optional.empty());
        nft.setFiles(List.of());
        nft.setDisplayMedia(new ImageContent("ipfs://QmDisplay2", 800, 600));
        nft.setRarity(NftRarity.COMMON);
        nft.setMintedAt(Instant.ofEpochSecond(1700000100L));
        nft.setAttributes(Map.of());
        return nft;
    }

    @SneakyThrows
    private MetadataMap extractMetadataMap(List<MetadataCBORContent> entries, String label) {
        for (MetadataCBORContent entry : entries) {
            if (label.equals(entry.getLabel())) {
                byte[] cborBytes = HexUtil.decodeHexString(entry.getCborMetadata());
                List<DataItem> items = CborDecoder.decode(cborBytes);
                return new CBORMetadataMap((co.nstant.in.cbor.model.Map) items.get(0));
            }
        }
        fail("No metadata found for label " + label);
        return null;
    }
}

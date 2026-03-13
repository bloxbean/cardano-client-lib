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

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Cip68ReferenceDatumDevnetTest extends BaseIT {

    private BackendService backendService;
    private Cip68ReferenceDatum original;
    private Cip68ReferenceDatum restored;

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

        var cborResult = backendService.getMetadataService().getCBORMetadataByTxnHash(txHash);
        assertTrue(cborResult.isSuccessful(), "Metadata retrieval should succeed");

        MetadataMap chainMap = extractMetadataMap(cborResult.getValue(), "100");
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

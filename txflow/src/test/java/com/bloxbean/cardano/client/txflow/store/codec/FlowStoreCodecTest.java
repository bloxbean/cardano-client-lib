package com.bloxbean.cardano.client.txflow.store.codec;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.InclusionRecord;
import com.bloxbean.cardano.client.txflow.store.PersistedBinding;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowStoreCodecTest {
    private static final Instant NOW = Instant.parse("2026-07-14T01:02:03.456Z");
    private static final FlowStoreCodec CODEC = FlowStoreCodec.standard();

    @Test
    void snapshotRoundTripRestoresEveryCurrentDurableValueType() {
        List<PersistedBinding> bindings = List.of(
                binding("text", "STRING", "hello"),
                binding("enabled", "BOOLEAN", true),
                binding("byte", "INTEGER", (byte) -7),
                binding("short", "INTEGER", (short) 32000),
                binding("integer", "INTEGER", 42),
                binding("long", "INTEGER", Long.MAX_VALUE),
                new PersistedBinding("secret", "STRING", null, "vault://secret/1",
                        "secret-fingerprint", "***"));

        FlowAttemptSnapshot inlineAttempt = new FlowAttemptSnapshot(
                "fund", 1, AttemptState.ROLLED_BACK,
                new SignedPayload.InlineCbor(new byte[]{0, 1, -1, 42}, "inline-sha", "tx-inline"),
                100L, 200L, List.of("input-a#0", "input-b#1"),
                List.of(
                        new InclusionRecord(12, "block-12", 120, NOW, true),
                        new InclusionRecord(14, null, 140, NOW.plusSeconds(4), false)),
                NOW.plusSeconds(5), "TXFLOW_ROLLBACK");
        FlowAttemptSnapshot externalAttempt = new FlowAttemptSnapshot(
                "pay", 2, AttemptState.SUBMITTING,
                new SignedPayload.ExternalCbor("payload://pay/2", "external-sha", "tx-external"),
                null, null, List.of(), List.of(), NOW.plusSeconds(6), null);

        Map<String, FlowAttemptSnapshot> attempts = new LinkedHashMap<>();
        attempts.put("pay#2", externalAttempt);
        attempts.put("fund#1", inlineAttempt);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("list", List.of("value", (short) 9));
        nested.put("flag", false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("spending_resources", new LinkedHashSet<>(List.of("wallet:b", "wallet:a")));
        data.put("concurrent_spending", false);
        data.put("bindings", bindings);
        data.put("attempts", attempts);
        data.put("step_count", 2);
        data.put("closed_nested_values", nested);
        FlowExecutionSnapshot snapshot = new FlowExecutionSnapshot(
                "execution-1", "definition-sha", "request-sha", FlowExecutionState.RUNNING,
                7, 19, 4, NOW, data);

        byte[] encoded = CODEC.encodeSnapshot(snapshot);
        FlowExecutionSnapshot decoded = CODEC.decodeSnapshot(encoded);

        assertEquals(snapshot, decoded);
        String json = new String(encoded, StandardCharsets.UTF_8);
        assertFalse(json.contains("@class"));
        assertFalse(json.contains("com.bloxbean"));
        assertInstanceOf(Set.class, decoded.data().get("spending_resources"));
        assertInstanceOf(List.class, decoded.data().get("bindings"));
        assertInstanceOf(Map.class, decoded.data().get("attempts"));

        List<?> decodedBindings = (List<?>) decoded.data().get("bindings");
        assertInstanceOf(Byte.class, bindingValue(decodedBindings, 2));
        assertInstanceOf(Short.class, bindingValue(decodedBindings, 3));
        assertInstanceOf(Integer.class, bindingValue(decodedBindings, 4));
        assertInstanceOf(Long.class, bindingValue(decodedBindings, 5));

        Map<?, ?> decodedAttempts = (Map<?, ?>) decoded.data().get("attempts");
        FlowAttemptSnapshot decodedInline = assertInstanceOf(
                FlowAttemptSnapshot.class, decodedAttempts.get("fund#1"));
        SignedPayload.InlineCbor inline = assertInstanceOf(
                SignedPayload.InlineCbor.class, decodedInline.signedPayload());
        assertArrayEquals(new byte[]{0, 1, -1, 42}, inline.cbor());
        assertInstanceOf(SignedPayload.ExternalCbor.class,
                ((FlowAttemptSnapshot) decodedAttempts.get("pay#2")).signedPayload());
    }

    @Test
    void eventRoundTripRestoresCurrentDetailShapes() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("message", "waiting");
        details.put("resubmitted", true);
        details.put("depth", 3);
        details.put("block_height", 9_223_372_036L);
        details.put("resources", new LinkedHashSet<>(List.of("resource:b", "resource:a")));
        FlowEvent event = new FlowEvent(11, "execution-1",
                FlowEventType.CONFIRMATION_DEPTH_CHANGED, NOW, "pay", "tx-1", details);

        FlowEvent decoded = CODEC.decodeEvent(CODEC.encodeEvent(event));

        assertEquals(event, decoded);
        assertInstanceOf(Integer.class, decoded.details().get("depth"));
        assertInstanceOf(Long.class, decoded.details().get("block_height"));
        assertInstanceOf(Set.class, decoded.details().get("resources"));
    }

    @Test
    void encodingIsCanonicalForMapKeysAndStringSets() {
        Map<String, Object> firstData = new LinkedHashMap<>();
        firstData.put("z", 1);
        firstData.put("resources", new LinkedHashSet<>(List.of("z", "a")));
        Map<String, Object> secondData = new LinkedHashMap<>();
        secondData.put("resources", new LinkedHashSet<>(List.of("a", "z")));
        secondData.put("z", 1);

        FlowExecutionSnapshot first = snapshot(firstData);
        FlowExecutionSnapshot second = snapshot(secondData);

        assertArrayEquals(CODEC.encodeSnapshot(first), CODEC.encodeSnapshot(second));
        assertEquals("ccl.txflow.store", FlowStoreCodec.FORMAT_ID);
        assertEquals(1, FlowStoreCodec.CURRENT_FORMAT_VERSION);
        assertTrue(FlowStoreCodec.supportsFormatVersion(1));
        assertFalse(FlowStoreCodec.supportsFormatVersion(0));
        assertFalse(FlowStoreCodec.supportsFormatVersion(2));
    }

    @Test
    void v1GoldenDocumentsRemainReadableAndByteStable() throws IOException {
        FlowExecutionSnapshot snapshot = snapshot(Map.of());
        FlowEvent event = new FlowEvent(1, "execution-1", FlowEventType.EXECUTION_CREATED,
                NOW, null, null, Map.of());
        byte[] snapshotFixture = resource("/fixtures/store/v1/execution-snapshot.json");
        byte[] eventFixture = resource("/fixtures/store/v1/flow-event.json");

        assertArrayEquals(snapshotFixture, CODEC.encodeSnapshot(snapshot));
        assertArrayEquals(eventFixture, CODEC.encodeEvent(event));
        assertEquals(snapshot, CODEC.decodeSnapshot(snapshotFixture));
        assertEquals(event, CODEC.decodeEvent(eventFixture));
    }

    @Test
    void richV1GoldenDocumentsCoverEveryDurableValueShape() throws IOException {
        FlowExecutionSnapshot snapshot = richFixtureSnapshot();
        FlowEvent event = richFixtureEvent();
        byte[] snapshotFixture = resource(
                "/fixtures/store/v1/execution-snapshot-rich.json");
        byte[] eventFixture = resource("/fixtures/store/v1/flow-event-rich.json");

        assertArrayEquals(snapshotFixture, CODEC.encodeSnapshot(snapshot));
        assertArrayEquals(eventFixture, CODEC.encodeEvent(event));
        assertEquals(snapshot, CODEC.decodeSnapshot(snapshotFixture));
        assertEquals(event, CODEC.decodeEvent(eventFixture));
    }

    @Test
    void rejectsUnsupportedRuntimeTypesWithoutPolymorphicDeserialization() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("floating_point", 1.5d);

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> CODEC.encodeSnapshot(snapshot(data)));

        assertEquals("TXFLOW_STORE_CODEC_UNSUPPORTED_VALUE", failure.getCode());
        assertTrue(failure.getMessage().contains(Double.class.getName()));
    }

    @Test
    void rejectsBindingValuesOutsideTheirDeclaredPortableScalarType() {
        PersistedBinding nestedValue = binding("invalid", "STRING", Map.of("nested", "value"));
        PersistedBinding mismatchedValue = binding("invalid", "INTEGER", "not-an-integer");

        FlowStoreException nestedFailure = assertThrows(FlowStoreException.class,
                () -> CODEC.encodeSnapshot(snapshot(Map.of("bindings", List.of(nestedValue)))));
        FlowStoreException mismatchFailure = assertThrows(FlowStoreException.class,
                () -> CODEC.encodeSnapshot(snapshot(Map.of("bindings", List.of(mismatchedValue)))));

        assertEquals("TXFLOW_STORE_CODEC_UNSUPPORTED_VALUE", nestedFailure.getCode());
        assertEquals("TXFLOW_STORE_CODEC_UNSUPPORTED_VALUE", mismatchFailure.getCode());
    }

    @Test
    void everySuccessfullyEncodedBoundaryValueCanBeDecodedByTheSameCodec() {
        Object nested = "leaf";
        for (int i = 0; i < 31; i++) {
            nested = List.of(nested);
        }
        String longestAcceptedKey = "k".repeat(4_096);
        FlowExecutionSnapshot snapshot = snapshot(Map.of(longestAcceptedKey, nested));

        byte[] encoded = CODEC.encodeSnapshot(snapshot);

        assertEquals(snapshot, CODEC.decodeSnapshot(encoded));
        FlowStoreException nameLimit = assertThrows(FlowStoreException.class,
                () -> CODEC.encodeSnapshot(snapshot(Map.of("k".repeat(4_097), "value"))));
        assertEquals("TXFLOW_STORE_CODEC_UNSUPPORTED_VALUE", nameLimit.getCode());

        Object tooDeep = "leaf";
        for (int i = 0; i < 32; i++) {
            tooDeep = List.of(tooDeep);
        }
        Object rejectedDepth = tooDeep;
        FlowStoreException depthLimit = assertThrows(FlowStoreException.class,
                () -> CODEC.encodeSnapshot(snapshot(Map.of("nested", rejectedDepth))));
        assertEquals("TXFLOW_STORE_CODEC_DEPTH_LIMIT", depthLimit.getCode());
    }

    @Test
    void rejectsCorruptBase64NumericTimestampAndSetEncodings() {
        FlowExecutionSnapshot rich = snapshot(Map.of(
                "byte", (byte) 1,
                "resources", new LinkedHashSet<>(List.of("a", "b")),
                "attempt", new FlowAttemptSnapshot("step", 1, AttemptState.SIGNED,
                        new SignedPayload.InlineCbor(new byte[]{1}, "sha", "tx"),
                        null, null, List.of(), List.of(), NOW, null)));
        String valid = new String(CODEC.encodeSnapshot(rich), StandardCharsets.UTF_8);

        FlowStoreException base64 = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeSnapshot(bytes(valid.replace("\"cbor\":\"AQ==\"",
                        "\"cbor\":\"%%%\""))));
        FlowStoreException numeric = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeSnapshot(bytes(valid.replace(
                        "\"type\":\"byte\",\"value\":1",
                        "\"type\":\"byte\",\"value\":999"))));
        FlowStoreException timestamp = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeSnapshot(bytes(valid.replace(
                        "\"updated_at\":\"2026-07-14T01:02:03.456Z\"",
                        "\"updated_at\":\"not-an-instant\""))));
        FlowStoreException duplicateSet = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeSnapshot(bytes(valid.replace(
                        "\"value\":[\"a\",\"b\"]", "\"value\":[\"a\",\"a\"]"))));

        assertEquals("TXFLOW_STORE_CODEC_DECODE_FAILED", base64.getCode());
        assertEquals("TXFLOW_STORE_CODEC_DECODE_FAILED", numeric.getCode());
        assertEquals("TXFLOW_STORE_CODEC_DECODE_FAILED", timestamp.getCode());
        assertEquals("TXFLOW_STORE_CODEC_DECODE_FAILED", duplicateSet.getCode());
    }

    @Test
    void rejectsUnknownVersionsKindsFieldsAndValueDiscriminators() {
        String valid = new String(CODEC.encodeSnapshot(snapshot(Map.of("phase", "running"))),
                StandardCharsets.UTF_8);

        FlowStoreException version = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeSnapshot(bytes(valid.replace("\"version\":1", "\"version\":2"))));
        assertEquals("TXFLOW_STORE_CODEC_UNSUPPORTED_VERSION", version.getCode());
        FlowStoreException externalVersionMismatch = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeSnapshot(
                        bytes(valid.replace("\"version\":1", "\"version\":2")), 1));
        assertEquals("TXFLOW_STORE_CODEC_VERSION_MISMATCH",
                externalVersionMismatch.getCode());

        FlowStoreException kind = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeEvent(bytes(valid)));
        assertEquals("TXFLOW_STORE_CODEC_DECODE_FAILED", kind.getCode());

        FlowStoreException unknownField = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeSnapshot(bytes(valid.replace(
                        "\"payload\":{", "\"payload\":{\"future_field\":true,"))));
        assertEquals("TXFLOW_STORE_CODEC_DECODE_FAILED", unknownField.getCode());

        FlowStoreException discriminator = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeSnapshot(bytes(valid.replace(
                        "\"type\":\"string\"", "\"type\":\"java_object\""))));
        assertEquals("TXFLOW_STORE_CODEC_UNSUPPORTED_VALUE", discriminator.getCode());
    }

    @Test
    void rejectsDuplicateKeysTrailingDocumentsAndConfiguredSizeOverflow() {
        String valid = new String(CODEC.encodeSnapshot(snapshot(Map.of())), StandardCharsets.UTF_8);
        String duplicateFormat = valid.replaceFirst("\\{", "{\"format\":\"ccl.txflow.store\",");
        FlowStoreException duplicate = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeSnapshot(bytes(duplicateFormat)));
        assertEquals("TXFLOW_STORE_CODEC_DECODE_FAILED", duplicate.getCode());

        FlowStoreException trailing = assertThrows(FlowStoreException.class,
                () -> CODEC.decodeSnapshot(bytes(valid + "{}")));
        assertEquals("TXFLOW_STORE_CODEC_DECODE_FAILED", trailing.getCode());

        FlowStoreCodec bounded = FlowStoreCodec.withMaxPayloadBytes(256);
        FlowStoreException tooLarge = assertThrows(FlowStoreException.class,
                () -> bounded.encodeSnapshot(snapshot(Map.of("large", "x".repeat(512)))));
        assertEquals("TXFLOW_STORE_CODEC_SIZE_LIMIT", tooLarge.getCode());
    }

    private PersistedBinding binding(String name, String type, Object value) {
        return new PersistedBinding(name, type, value, null, name + "-fingerprint",
                String.valueOf(value));
    }

    private Object bindingValue(List<?> bindings, int index) {
        return ((PersistedBinding) bindings.get(index)).nonSensitiveValue();
    }

    private FlowExecutionSnapshot snapshot(Map<String, Object> data) {
        return new FlowExecutionSnapshot("execution-1", "definition-sha", "request-sha",
                FlowExecutionState.RUNNING, 7, 19, 4, NOW, data);
    }

    private FlowExecutionSnapshot richFixtureSnapshot() {
        List<PersistedBinding> bindings = List.of(
                binding("text", "STRING", "hello"),
                binding("enabled", "BOOLEAN", true),
                binding("byte", "INTEGER", (byte) -7),
                binding("short", "INTEGER", (short) 32000),
                binding("integer", "INTEGER", 42),
                binding("long", "INTEGER", Long.MAX_VALUE),
                new PersistedBinding("secret", "STRING", null, "vault://secret/1",
                        "secret-fingerprint", "***"));
        FlowAttemptSnapshot inline = new FlowAttemptSnapshot(
                "fund", 1, AttemptState.ROLLED_BACK,
                new SignedPayload.InlineCbor(
                        new byte[]{0, 1, -1, 42}, "inline-sha", "tx-inline"),
                100L, 200L, List.of("input-a#0", "input-b#1"),
                List.of(new InclusionRecord(12, "block-12", 120, NOW, true)),
                NOW.plusSeconds(5), "TXFLOW_ROLLBACK");
        FlowAttemptSnapshot external = new FlowAttemptSnapshot(
                "pay", 2, AttemptState.SUBMITTING,
                new SignedPayload.ExternalCbor(
                        "payload://pay/2", "external-sha", "tx-external"),
                null, null, List.of(), List.of(), NOW.plusSeconds(6), null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("spending_resources", new LinkedHashSet<>(List.of("wallet:b", "wallet:a")));
        data.put("concurrent_spending", false);
        data.put("bindings", bindings);
        data.put("attempts", Map.of("pay#2", external, "fund#1", inline));
        data.put("step_count", 2);
        data.put("nested", Map.of("list", List.of("value", (short) 9), "flag", false));
        return snapshot(data);
    }

    private FlowEvent richFixtureEvent() {
        return new FlowEvent(11, "execution-1", FlowEventType.CONFIRMATION_DEPTH_CHANGED,
                NOW, "pay", "tx-1", Map.of(
                "message", "waiting", "resubmitted", true, "depth", 3,
                "block_height", 9_223_372_036L,
                "resources", new LinkedHashSet<>(List.of("resource:b", "resource:a")),
                "history", List.of("submitted", "included")));
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .trim().getBytes(StandardCharsets.UTF_8);
        }
    }
}

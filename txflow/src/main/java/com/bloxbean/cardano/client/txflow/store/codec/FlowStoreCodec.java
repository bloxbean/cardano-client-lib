package com.bloxbean.cardano.client.txflow.store.codec;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.model.ParameterType;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.InclusionRecord;
import com.bloxbean.cardano.client.txflow.store.PersistedBinding;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Versioned JSON codec for durable TxFlow snapshots and journal events.
 *
 * <p>The codec deliberately uses a closed value model instead of Java serialization or Jackson
 * polymorphic default typing. Snapshot data and event details may contain strings, booleans,
 * integral Java wrapper types, string sets, lists, string-keyed maps, {@link PersistedBinding},
 * and {@link FlowAttemptSnapshot}. Attempt records in turn support both signed-payload variants
 * and their inclusion history. Every value carries an explicit stable discriminator so decoding
 * restores records and numeric wrapper types instead of producing untyped maps.</p>
 *
 * <p>Encoded documents carry a format identifier, document kind, and format version. Unknown
 * versions, kinds, fields, or value discriminators are rejected. Input size, nesting depth, name
 * length, number length, and container sizes are bounded before objects are reconstructed.</p>
 *
 * <p>This is a persistence format, not the portable TxFlow authoring format. Applications should
 * treat encoded bytes as opaque and use the same codec when implementing a
 * {@code FlowExecutionStore} adapter.</p>
 */
public final class FlowStoreCodec {
    private static final int FORMAT_VERSION_V1 = 1;

    /** Stable identifier for the durable snapshot and event envelope family. */
    public static final String FORMAT_ID = "ccl.txflow.store";
    /** Current durable JSON envelope version. */
    public static final int CURRENT_FORMAT_VERSION = FORMAT_VERSION_V1;
    /** Default upper bound for one encoded snapshot or event. */
    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;

    private static final String SNAPSHOT_KIND = "execution_snapshot";
    private static final String EVENT_KIND = "flow_event";
    private static final int MAX_NESTING_DEPTH = 32;
    private static final int MAX_JSON_NESTING_DEPTH = 128;
    private static final int MAX_CONTAINER_ENTRIES = 100_000;
    private static final int MAX_NAME_LENGTH = 4_096;

    private static final String STRING = "string";
    private static final String BOOLEAN = "boolean";
    private static final String BYTE = "byte";
    private static final String SHORT = "short";
    private static final String INTEGER = "integer";
    private static final String LONG = "long";
    private static final String STRING_SET = "string_set";
    private static final String LIST = "list";
    private static final String MAP = "map";
    private static final String PERSISTED_BINDING = "persisted_binding";
    private static final String FLOW_ATTEMPT = "flow_attempt";

    private final int maxPayloadBytes;
    private final ObjectMapper json;

    private FlowStoreCodec(int maxPayloadBytes) {
        if (maxPayloadBytes < 1) {
            throw new IllegalArgumentException("maxPayloadBytes must be positive");
        }
        this.maxPayloadBytes = maxPayloadBytes;
        this.json = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxDocumentLength(maxPayloadBytes)
                        // Tagged values add an object and a value container at each semantic
                        // level. Keep the parser bound above that representation overhead while
                        // enforcing the tighter semantic depth in encodeValue/decodeValue.
                        .maxNestingDepth(MAX_JSON_NESTING_DEPTH)
                        .maxStringLength(maxPayloadBytes)
                        .maxNameLength(MAX_NAME_LENGTH)
                        .maxNumberLength(32)
                        .build())
                .build())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /**
     * Creates a reusable codec with the default 16 MiB document limit.
     *
     * @return thread-safe codec instance
     */
    public static FlowStoreCodec standard() {
        return new FlowStoreCodec(DEFAULT_MAX_PAYLOAD_BYTES);
    }

    /**
     * Creates a reusable codec with an application-selected document limit.
     *
     * @param maxPayloadBytes maximum encoded or decoded document size
     * @return thread-safe codec instance
     */
    public static FlowStoreCodec withMaxPayloadBytes(int maxPayloadBytes) {
        return new FlowStoreCodec(maxPayloadBytes);
    }

    /**
     * Reports whether this library retains a reader for a durable envelope version.
     *
     * <p>Relational adapters use this independently of {@link #CURRENT_FORMAT_VERSION} so a
     * future writer-version bump does not make existing rows unreadable while their explicit
     * version reader remains supported.</p>
     *
     * @param version envelope version stored alongside the payload
     * @return whether the payload can be decoded by this library
     */
    public static boolean supportsFormatVersion(int version) {
        return version == FORMAT_VERSION_V1;
    }

    /**
     * Encodes a complete execution snapshot in the current persistence envelope.
     *
     * @param snapshot snapshot to encode
     * @return UTF-8 JSON bytes
     * @throws FlowStoreException when the snapshot contains an unsupported value or exceeds the
     *         configured size limit
     */
    public byte[] encodeSnapshot(FlowExecutionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        ObjectNode payload = json.createObjectNode();
        payload.put("execution_id", snapshot.executionId());
        payload.put("definition_fingerprint", snapshot.definitionFingerprint());
        payload.put("request_fingerprint", snapshot.requestFingerprint());
        payload.put("state", snapshot.state().name());
        payload.put("revision", snapshot.revision());
        payload.put("last_sequence", snapshot.lastSequence());
        payload.put("compacted_through_sequence", snapshot.compactedThroughSequence());
        payload.put("updated_at", snapshot.updatedAt().toString());
        payload.set("data", encodeValueMap(snapshot.data(), 0));
        return writeEnvelope(SNAPSHOT_KIND, payload);
    }

    /**
     * Decodes and validates a complete execution snapshot.
     *
     * @param encoded UTF-8 persistence document
     * @return reconstructed snapshot with typed durable data
     * @throws FlowStoreException when the document is malformed, unsupported, or exceeds the
     *         configured size limit
     */
    public FlowExecutionSnapshot decodeSnapshot(byte[] encoded) {
        return decodeSnapshot(encoded, null);
    }

    /**
     * Decodes a snapshot while cross-checking version metadata stored outside the payload.
     *
     * @param encoded UTF-8 persistence document
     * @param expectedFormatVersion envelope version recorded by the durable adapter
     * @return reconstructed snapshot with typed durable data
     * @throws FlowStoreException when the inner and externally recorded versions differ, or the
     *         document is otherwise invalid
     */
    public FlowExecutionSnapshot decodeSnapshot(byte[] encoded, int expectedFormatVersion) {
        return decodeSnapshot(encoded, Integer.valueOf(expectedFormatVersion));
    }

    private FlowExecutionSnapshot decodeSnapshot(byte[] encoded, Integer expectedFormatVersion) {
        ObjectNode payload = readEnvelope(encoded, SNAPSHOT_KIND, expectedFormatVersion);
        requireExactFields(payload, "snapshot", "execution_id", "definition_fingerprint",
                "request_fingerprint", "state", "revision", "last_sequence",
                "compacted_through_sequence", "updated_at", "data");
        try {
            return new FlowExecutionSnapshot(
                    requiredText(payload, "execution_id", "snapshot"),
                    requiredText(payload, "definition_fingerprint", "snapshot"),
                    requiredText(payload, "request_fingerprint", "snapshot"),
                    enumValue(FlowExecutionState.class,
                            requiredText(payload, "state", "snapshot"), "snapshot.state"),
                    requiredLong(payload, "revision", "snapshot"),
                    requiredLong(payload, "last_sequence", "snapshot"),
                    requiredLong(payload, "compacted_through_sequence", "snapshot"),
                    requiredInstant(payload, "updated_at", "snapshot"),
                    decodeValueMap(requiredObject(payload, "data", "snapshot"), 0));
        } catch (FlowStoreException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw decodeFailure("Invalid execution snapshot", failure);
        }
    }

    /**
     * Encodes one ordered execution event in the current persistence envelope.
     *
     * @param event event to encode
     * @return UTF-8 JSON bytes
     * @throws FlowStoreException when event details contain an unsupported value or the document
     *         exceeds the configured size limit
     */
    public byte[] encodeEvent(FlowEvent event) {
        Objects.requireNonNull(event, "event");
        ObjectNode payload = json.createObjectNode();
        payload.put("sequence", event.sequence());
        payload.put("execution_id", event.executionId());
        payload.put("type", event.type().name());
        payload.put("timestamp", event.timestamp().toString());
        putNullableText(payload, "step_id", event.stepId());
        putNullableText(payload, "transaction_hash", event.transactionHash());
        payload.set("details", encodeValueMap(event.details(), 0));
        return writeEnvelope(EVENT_KIND, payload);
    }

    /**
     * Decodes and validates one ordered execution event.
     *
     * @param encoded UTF-8 persistence document
     * @return reconstructed event with typed details
     * @throws FlowStoreException when the document is malformed, unsupported, or exceeds the
     *         configured size limit
     */
    public FlowEvent decodeEvent(byte[] encoded) {
        return decodeEvent(encoded, null);
    }

    /**
     * Decodes an event while cross-checking version metadata stored outside the payload.
     *
     * @param encoded UTF-8 persistence document
     * @param expectedFormatVersion envelope version recorded by the durable adapter
     * @return reconstructed event with typed details
     * @throws FlowStoreException when the inner and externally recorded versions differ, or the
     *         document is otherwise invalid
     */
    public FlowEvent decodeEvent(byte[] encoded, int expectedFormatVersion) {
        return decodeEvent(encoded, Integer.valueOf(expectedFormatVersion));
    }

    private FlowEvent decodeEvent(byte[] encoded, Integer expectedFormatVersion) {
        ObjectNode payload = readEnvelope(encoded, EVENT_KIND, expectedFormatVersion);
        requireExactFields(payload, "event", "sequence", "execution_id", "type", "timestamp",
                "step_id", "transaction_hash", "details");
        try {
            return new FlowEvent(
                    requiredLong(payload, "sequence", "event"),
                    requiredText(payload, "execution_id", "event"),
                    enumValue(FlowEventType.class,
                            requiredText(payload, "type", "event"), "event.type"),
                    requiredInstant(payload, "timestamp", "event"),
                    nullableText(payload, "step_id", "event"),
                    nullableText(payload, "transaction_hash", "event"),
                    decodeValueMap(requiredObject(payload, "details", "event"), 0));
        } catch (FlowStoreException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw decodeFailure("Invalid flow event", failure);
        }
    }

    private byte[] writeEnvelope(String kind, ObjectNode payload) {
        ObjectNode envelope = json.createObjectNode();
        envelope.put("format", FORMAT_ID);
        envelope.put("version", CURRENT_FORMAT_VERSION);
        envelope.put("kind", kind);
        envelope.set("payload", payload);
        try {
            byte[] encoded = json.writeValueAsBytes(envelope);
            if (encoded.length > maxPayloadBytes) {
                throw new FlowStoreException("TXFLOW_STORE_CODEC_SIZE_LIMIT",
                        "Encoded store document exceeds " + maxPayloadBytes + " bytes");
            }
            return encoded;
        } catch (JsonProcessingException failure) {
            throw new FlowStoreException("TXFLOW_STORE_CODEC_ENCODE_FAILED",
                    "Could not encode store document", failure);
        }
    }

    private ObjectNode readEnvelope(byte[] encoded, String expectedKind,
                                    Integer expectedFormatVersion) {
        if (encoded == null || encoded.length == 0) {
            throw decodeFailure("Store document cannot be empty", null);
        }
        if (encoded.length > maxPayloadBytes) {
            throw new FlowStoreException("TXFLOW_STORE_CODEC_SIZE_LIMIT",
                    "Store document exceeds " + maxPayloadBytes + " bytes");
        }
        try {
            JsonNode parsed = json.readTree(encoded);
            ObjectNode envelope = requireObject(parsed, "envelope");
            requireExactFields(envelope, "envelope", "format", "version", "kind", "payload");
            if (!FORMAT_ID.equals(requiredText(envelope, "format", "envelope"))) {
                throw decodeFailure("Unsupported store format", null);
            }
            int version = requiredInt(envelope, "version", "envelope");
            if (expectedFormatVersion != null && version != expectedFormatVersion) {
                throw new FlowStoreException("TXFLOW_STORE_CODEC_VERSION_MISMATCH",
                        "Store envelope version does not match its external metadata");
            }
            // Keep explicit version readers here when a later writer version is introduced.
            // Incrementing CURRENT_FORMAT_VERSION must never remove the v1 read path.
            if (!supportsFormatVersion(version)) {
                throw new FlowStoreException("TXFLOW_STORE_CODEC_UNSUPPORTED_VERSION",
                        "Unsupported store format version: " + version);
            }
            String kind = requiredText(envelope, "kind", "envelope");
            if (!expectedKind.equals(kind)) {
                throw decodeFailure("Expected document kind " + expectedKind + " but found " + kind,
                        null);
            }
            return requiredObject(envelope, "payload", "envelope");
        } catch (FlowStoreException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw decodeFailure("Could not decode store document", failure);
        }
    }

    private ObjectNode encodeValueMap(Map<?, ?> values, int depth) {
        requireDepth(depth);
        requireContainerSize(values.size());
        ObjectNode encoded = json.createObjectNode();
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw unsupported("Durable maps require string keys");
            }
            if (((String) entry.getKey()).length() > MAX_NAME_LENGTH) {
                throw unsupported("Durable map keys cannot exceed " + MAX_NAME_LENGTH
                        + " characters");
            }
            if (entry.getValue() == null) {
                throw unsupported("Durable maps cannot contain null values");
            }
            sorted.put((String) entry.getKey(), entry.getValue());
        }
        sorted.forEach((key, value) -> encoded.set(key, encodeValue(value, depth + 1)));
        return encoded;
    }

    private Map<String, Object> decodeValueMap(ObjectNode encoded, int depth) {
        requireDepth(depth);
        requireContainerSize(encoded.size());
        Map<String, Object> values = new LinkedHashMap<>();
        encoded.properties().forEach(entry ->
                values.put(entry.getKey(), decodeValue(entry.getValue(), depth + 1)));
        return Collections.unmodifiableMap(values);
    }

    private ObjectNode encodeValue(Object value, int depth) {
        requireDepth(depth);
        ObjectNode encoded = json.createObjectNode();
        if (value instanceof String) {
            encoded.put("type", STRING);
            encoded.put("value", (String) value);
        } else if (value instanceof Boolean) {
            encoded.put("type", BOOLEAN);
            encoded.put("value", (Boolean) value);
        } else if (value instanceof Byte) {
            encoded.put("type", BYTE);
            encoded.put("value", ((Byte) value).intValue());
        } else if (value instanceof Short) {
            encoded.put("type", SHORT);
            encoded.put("value", ((Short) value).intValue());
        } else if (value instanceof Integer) {
            encoded.put("type", INTEGER);
            encoded.put("value", (Integer) value);
        } else if (value instanceof Long) {
            encoded.put("type", LONG);
            encoded.put("value", (Long) value);
        } else if (value instanceof Set) {
            encoded.put("type", STRING_SET);
            encoded.set("value", encodeStringSet((Set<?>) value));
        } else if (value instanceof List) {
            encoded.put("type", LIST);
            encoded.set("value", encodeList((List<?>) value, depth));
        } else if (value instanceof Map) {
            encoded.put("type", MAP);
            encoded.set("value", encodeValueMap((Map<?, ?>) value, depth));
        } else if (value instanceof PersistedBinding) {
            encoded.put("type", PERSISTED_BINDING);
            encoded.set("value", encodeBinding((PersistedBinding) value, depth));
        } else if (value instanceof FlowAttemptSnapshot) {
            encoded.put("type", FLOW_ATTEMPT);
            encoded.set("value", encodeAttempt((FlowAttemptSnapshot) value));
        } else {
            throw unsupported("Unsupported durable value type: " + value.getClass().getName());
        }
        return encoded;
    }

    private Object decodeValue(JsonNode node, int depth) {
        requireDepth(depth);
        ObjectNode encoded = requireObject(node, "value");
        requireExactFields(encoded, "value", "type", "value");
        String type = requiredText(encoded, "type", "value");
        JsonNode value = required(encoded, "value", "value");
        switch (type) {
            case STRING:
                return textValue(value, "value.value");
            case BOOLEAN:
                return booleanValue(value, "value.value");
            case BYTE:
                return byteValue(value, "value.value");
            case SHORT:
                return shortValue(value, "value.value");
            case INTEGER:
                return intValue(value, "value.value");
            case LONG:
                return longValue(value, "value.value");
            case STRING_SET:
                return decodeStringSet(requireArray(value, "value.value"));
            case LIST:
                return decodeList(requireArray(value, "value.value"), depth);
            case MAP:
                return decodeValueMap(requireObject(value, "value.value"), depth);
            case PERSISTED_BINDING:
                return decodeBinding(requireObject(value, "value.value"), depth);
            case FLOW_ATTEMPT:
                return decodeAttempt(requireObject(value, "value.value"));
            default:
                throw unsupported("Unsupported durable value discriminator: " + type);
        }
    }

    private ArrayNode encodeStringSet(Set<?> values) {
        requireContainerSize(values.size());
        List<String> sorted = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String)) {
                throw unsupported("Durable sets may contain only strings");
            }
            sorted.add((String) value);
        }
        Collections.sort(sorted);
        ArrayNode encoded = json.createArrayNode();
        sorted.forEach(encoded::add);
        return encoded;
    }

    private Set<String> decodeStringSet(ArrayNode encoded) {
        requireContainerSize(encoded.size());
        Set<String> values = new LinkedHashSet<>();
        for (int i = 0; i < encoded.size(); i++) {
            String value = textValue(encoded.get(i), "string_set[" + i + "]");
            if (!values.add(value)) {
                throw decodeFailure("String set contains a duplicate value", null);
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private ArrayNode encodeList(List<?> values, int depth) {
        requireDepth(depth);
        requireContainerSize(values.size());
        ArrayNode encoded = json.createArrayNode();
        for (Object value : values) {
            if (value == null) {
                throw unsupported("Durable lists cannot contain null values");
            }
            encoded.add(encodeValue(value, depth + 1));
        }
        return encoded;
    }

    private List<Object> decodeList(ArrayNode encoded, int depth) {
        requireDepth(depth);
        requireContainerSize(encoded.size());
        List<Object> values = new ArrayList<>(encoded.size());
        for (JsonNode value : encoded) {
            values.add(decodeValue(value, depth + 1));
        }
        return List.copyOf(values);
    }

    private ObjectNode encodeBinding(PersistedBinding binding, int depth) {
        requireBindingInvariant(binding);
        ObjectNode encoded = json.createObjectNode();
        encoded.put("parameter_name", binding.parameterName());
        encoded.put("parameter_type", binding.parameterType());
        if (binding.nonSensitiveValue() == null) {
            encoded.putNull("non_sensitive_value");
        } else {
            encoded.set("non_sensitive_value", encodeValue(binding.nonSensitiveValue(), depth + 1));
        }
        putNullableText(encoded, "secure_value_ref", binding.secureValueRef());
        encoded.put("value_fingerprint", binding.valueFingerprint());
        encoded.put("redacted_display", binding.redactedDisplay());
        return encoded;
    }

    private PersistedBinding decodeBinding(ObjectNode encoded, int depth) {
        requireExactFields(encoded, "persisted_binding", "parameter_name", "parameter_type",
                "non_sensitive_value", "secure_value_ref", "value_fingerprint",
                "redacted_display");
        JsonNode value = required(encoded, "non_sensitive_value", "persisted_binding");
        PersistedBinding binding = new PersistedBinding(
                requiredText(encoded, "parameter_name", "persisted_binding"),
                requiredText(encoded, "parameter_type", "persisted_binding"),
                value.isNull() ? null : decodeValue(value, depth + 1),
                nullableText(encoded, "secure_value_ref", "persisted_binding"),
                requiredText(encoded, "value_fingerprint", "persisted_binding"),
                requiredText(encoded, "redacted_display", "persisted_binding"));
        requireDecodedBindingInvariant(binding);
        return binding;
    }

    private ObjectNode encodeAttempt(FlowAttemptSnapshot attempt) {
        if (attempt.stepId() == null || attempt.stepId().isBlank() || attempt.attemptNumber() < 1
                || attempt.state() == null || attempt.updatedAt() == null) {
            throw unsupported("Flow attempts require a step, positive attempt number, state, and update time");
        }
        ObjectNode encoded = json.createObjectNode();
        encoded.put("step_id", attempt.stepId());
        encoded.put("attempt_number", attempt.attemptNumber());
        encoded.put("state", attempt.state().name());
        if (attempt.signedPayload() == null) {
            encoded.putNull("signed_payload");
        } else {
            encoded.set("signed_payload", encodeSignedPayload(attempt.signedPayload()));
        }
        putNullableLong(encoded, "valid_from_slot", attempt.validFromSlot());
        putNullableLong(encoded, "valid_to_slot", attempt.validToSlot());
        encoded.set("spent_inputs", encodeStrings(attempt.spentInputs()));
        ArrayNode inclusions = json.createArrayNode();
        requireContainerSize(attempt.inclusions().size());
        attempt.inclusions().forEach(inclusion -> inclusions.add(encodeInclusion(inclusion)));
        encoded.set("inclusions", inclusions);
        encoded.put("updated_at", attempt.updatedAt().toString());
        putNullableText(encoded, "error_code", attempt.errorCode());
        return encoded;
    }

    private FlowAttemptSnapshot decodeAttempt(ObjectNode encoded) {
        requireExactFields(encoded, "flow_attempt", "step_id", "attempt_number", "state",
                "signed_payload", "valid_from_slot", "valid_to_slot", "spent_inputs",
                "inclusions", "updated_at", "error_code");
        JsonNode signed = required(encoded, "signed_payload", "flow_attempt");
        ArrayNode inclusionNodes = requiredArray(encoded, "inclusions", "flow_attempt");
        requireContainerSize(inclusionNodes.size());
        List<InclusionRecord> inclusions = new ArrayList<>(inclusionNodes.size());
        for (JsonNode inclusion : inclusionNodes) {
            inclusions.add(decodeInclusion(requireObject(inclusion, "flow_attempt.inclusions")));
        }
        String stepId = requiredText(encoded, "step_id", "flow_attempt");
        int attemptNumber = requiredInt(encoded, "attempt_number", "flow_attempt");
        if (stepId.isBlank() || attemptNumber < 1) {
            throw decodeFailure("Flow attempt identity is invalid", null);
        }
        return new FlowAttemptSnapshot(
                stepId,
                attemptNumber,
                enumValue(AttemptState.class,
                        requiredText(encoded, "state", "flow_attempt"), "flow_attempt.state"),
                signed.isNull() ? null : decodeSignedPayload(
                        requireObject(signed, "flow_attempt.signed_payload")),
                nullableLong(encoded, "valid_from_slot", "flow_attempt"),
                nullableLong(encoded, "valid_to_slot", "flow_attempt"),
                decodeStrings(requiredArray(encoded, "spent_inputs", "flow_attempt")),
                inclusions,
                requiredInstant(encoded, "updated_at", "flow_attempt"),
                nullableText(encoded, "error_code", "flow_attempt"));
    }

    private ObjectNode encodeSignedPayload(SignedPayload payload) {
        ObjectNode encoded = json.createObjectNode();
        if (payload instanceof SignedPayload.InlineCbor) {
            SignedPayload.InlineCbor inline = (SignedPayload.InlineCbor) payload;
            encoded.put("kind", "inline_cbor");
            encoded.put("cbor", Base64.getEncoder().encodeToString(inline.cbor()));
        } else if (payload instanceof SignedPayload.ExternalCbor) {
            SignedPayload.ExternalCbor external = (SignedPayload.ExternalCbor) payload;
            encoded.put("kind", "external_cbor");
            encoded.put("reference", external.reference());
        } else {
            throw unsupported("Unsupported signed payload type: " + payload.getClass().getName());
        }
        encoded.put("sha256", payload.sha256());
        encoded.put("transaction_hash", payload.transactionHash());
        return encoded;
    }

    private SignedPayload decodeSignedPayload(ObjectNode encoded) {
        String kind = requiredText(encoded, "kind", "signed_payload");
        if ("inline_cbor".equals(kind)) {
            requireExactFields(encoded, "signed_payload", "kind", "cbor", "sha256",
                    "transaction_hash");
            try {
                byte[] cbor = Base64.getDecoder().decode(
                        requiredText(encoded, "cbor", "signed_payload"));
                return new SignedPayload.InlineCbor(cbor,
                        requiredText(encoded, "sha256", "signed_payload"),
                        requiredText(encoded, "transaction_hash", "signed_payload"));
            } catch (IllegalArgumentException failure) {
                throw decodeFailure("Inline signed payload contains invalid base64", failure);
            }
        }
        if ("external_cbor".equals(kind)) {
            requireExactFields(encoded, "signed_payload", "kind", "reference", "sha256",
                    "transaction_hash");
            return new SignedPayload.ExternalCbor(
                    requiredText(encoded, "reference", "signed_payload"),
                    requiredText(encoded, "sha256", "signed_payload"),
                    requiredText(encoded, "transaction_hash", "signed_payload"));
        }
        throw unsupported("Unsupported signed payload discriminator: " + kind);
    }

    private ObjectNode encodeInclusion(InclusionRecord inclusion) {
        Objects.requireNonNull(inclusion, "inclusion");
        if (inclusion.observedAt() == null) {
            throw unsupported("Inclusion records require an observation time");
        }
        ObjectNode encoded = json.createObjectNode();
        encoded.put("block_height", inclusion.blockHeight());
        putNullableText(encoded, "block_hash", inclusion.blockHash());
        encoded.put("slot", inclusion.slot());
        encoded.put("observed_at", inclusion.observedAt().toString());
        encoded.put("rolled_back", inclusion.rolledBack());
        return encoded;
    }

    private InclusionRecord decodeInclusion(ObjectNode encoded) {
        requireExactFields(encoded, "inclusion", "block_height", "block_hash", "slot",
                "observed_at", "rolled_back");
        return new InclusionRecord(
                requiredLong(encoded, "block_height", "inclusion"),
                nullableText(encoded, "block_hash", "inclusion"),
                requiredLong(encoded, "slot", "inclusion"),
                requiredInstant(encoded, "observed_at", "inclusion"),
                requiredBoolean(encoded, "rolled_back", "inclusion"));
    }

    private void requireBindingInvariant(PersistedBinding binding) {
        if (binding.parameterName() == null || binding.parameterName().isBlank()
                || binding.parameterType() == null || binding.parameterType().isBlank()
                || binding.valueFingerprint() == null || binding.valueFingerprint().isBlank()
                || binding.redactedDisplay() == null
                || (binding.nonSensitiveValue() == null) == (binding.secureValueRef() == null)
                || (binding.secureValueRef() != null && binding.secureValueRef().isBlank())) {
            throw unsupported("Persisted binding storage form is invalid");
        }
        ParameterType parameterType;
        try {
            parameterType = ParameterType.valueOf(binding.parameterType());
        } catch (IllegalArgumentException failure) {
            throw unsupported("Persisted binding has an unsupported parameter type: "
                    + binding.parameterType());
        }
        Object value = binding.nonSensitiveValue();
        if (value == null) return;
        boolean valid;
        switch (parameterType) {
            case INTEGER:
                valid = value instanceof Byte || value instanceof Short
                        || value instanceof Integer || value instanceof Long;
                break;
            case BOOLEAN:
                valid = value instanceof Boolean;
                break;
            default:
                valid = value instanceof String;
                break;
        }
        if (!valid) {
            throw unsupported("Persisted binding value does not match " + parameterType);
        }
    }

    private void requireDecodedBindingInvariant(PersistedBinding binding) {
        try {
            requireBindingInvariant(binding);
        } catch (FlowStoreException failure) {
            throw decodeFailure(failure.getMessage(), failure);
        }
    }

    private ArrayNode encodeStrings(Collection<String> values) {
        requireContainerSize(values.size());
        ArrayNode encoded = json.createArrayNode();
        values.forEach(value -> encoded.add(Objects.requireNonNull(value, "string value")));
        return encoded;
    }

    private List<String> decodeStrings(ArrayNode encoded) {
        requireContainerSize(encoded.size());
        List<String> values = new ArrayList<>(encoded.size());
        for (int i = 0; i < encoded.size(); i++) {
            values.add(textValue(encoded.get(i), "strings[" + i + "]"));
        }
        return List.copyOf(values);
    }

    private void requireDepth(int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new FlowStoreException("TXFLOW_STORE_CODEC_DEPTH_LIMIT",
                    "Durable value nesting exceeds " + MAX_NESTING_DEPTH);
        }
    }

    private void requireContainerSize(int size) {
        if (size > MAX_CONTAINER_ENTRIES) {
            throw new FlowStoreException("TXFLOW_STORE_CODEC_CONTAINER_LIMIT",
                    "Durable container exceeds " + MAX_CONTAINER_ENTRIES + " entries");
        }
    }

    private void requireExactFields(ObjectNode node, String path, String... expected) {
        Set<String> expectedFields = Set.of(expected);
        Set<String> actual = new LinkedHashSet<>();
        node.properties().forEach(entry -> actual.add(entry.getKey()));
        if (!actual.equals(expectedFields)) {
            Set<String> missing = new LinkedHashSet<>(expectedFields);
            missing.removeAll(actual);
            Set<String> unknown = new LinkedHashSet<>(actual);
            unknown.removeAll(expectedFields);
            throw decodeFailure(path + " fields are invalid; missing=" + missing
                    + ", unknown=" + unknown, null);
        }
    }

    private JsonNode required(ObjectNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw decodeFailure(path + "." + field + " is required", null);
        }
        return value;
    }

    private ObjectNode requiredObject(ObjectNode node, String field, String path) {
        return requireObject(required(node, field, path), path + "." + field);
    }

    private ArrayNode requiredArray(ObjectNode node, String field, String path) {
        return requireArray(required(node, field, path), path + "." + field);
    }

    private ObjectNode requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw decodeFailure(path + " must be an object", null);
        }
        return (ObjectNode) node;
    }

    private ArrayNode requireArray(JsonNode node, String path) {
        if (node == null || !node.isArray()) {
            throw decodeFailure(path + " must be an array", null);
        }
        return (ArrayNode) node;
    }

    private String requiredText(ObjectNode node, String field, String path) {
        return textValue(required(node, field, path), path + "." + field);
    }

    private String nullableText(ObjectNode node, String field, String path) {
        JsonNode value = required(node, field, path);
        return value.isNull() ? null : textValue(value, path + "." + field);
    }

    private String textValue(JsonNode value, String path) {
        if (!value.isTextual()) {
            throw decodeFailure(path + " must be text", null);
        }
        return value.textValue();
    }

    private boolean requiredBoolean(ObjectNode node, String field, String path) {
        return booleanValue(required(node, field, path), path + "." + field);
    }

    private boolean booleanValue(JsonNode value, String path) {
        if (!value.isBoolean()) {
            throw decodeFailure(path + " must be boolean", null);
        }
        return value.booleanValue();
    }

    private int requiredInt(ObjectNode node, String field, String path) {
        return intValue(required(node, field, path), path + "." + field);
    }

    private int intValue(JsonNode value, String path) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw decodeFailure(path + " must be a 32-bit integer", null);
        }
        return value.intValue();
    }

    private byte byteValue(JsonNode value, String path) {
        int number = intValue(value, path);
        if (number < Byte.MIN_VALUE || number > Byte.MAX_VALUE) {
            throw decodeFailure(path + " must be an 8-bit integer", null);
        }
        return (byte) number;
    }

    private short shortValue(JsonNode value, String path) {
        int number = intValue(value, path);
        if (number < Short.MIN_VALUE || number > Short.MAX_VALUE) {
            throw decodeFailure(path + " must be a 16-bit integer", null);
        }
        return (short) number;
    }

    private long requiredLong(ObjectNode node, String field, String path) {
        return longValue(required(node, field, path), path + "." + field);
    }

    private Long nullableLong(ObjectNode node, String field, String path) {
        JsonNode value = required(node, field, path);
        return value.isNull() ? null : longValue(value, path + "." + field);
    }

    private long longValue(JsonNode value, String path) {
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw decodeFailure(path + " must be a 64-bit integer", null);
        }
        return value.longValue();
    }

    private Instant requiredInstant(ObjectNode node, String field, String path) {
        return parseInstant(requiredText(node, field, path), path + "." + field);
    }

    private Instant parseInstant(String value, String path) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException failure) {
            throw decodeFailure(path + " must be an ISO-8601 instant", failure);
        }
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String path) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw decodeFailure(path + " has unsupported value: " + value, failure);
        }
    }

    private void putNullableText(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field);
        else node.put(field, value);
    }

    private void putNullableLong(ObjectNode node, String field, Long value) {
        if (value == null) node.putNull(field);
        else node.put(field, value);
    }

    private FlowStoreException unsupported(String message) {
        return new FlowStoreException("TXFLOW_STORE_CODEC_UNSUPPORTED_VALUE", message);
    }

    private FlowStoreException decodeFailure(String message, Throwable cause) {
        return cause == null
                ? new FlowStoreException("TXFLOW_STORE_CODEC_DECODE_FAILED", message)
                : new FlowStoreException("TXFLOW_STORE_CODEC_DECODE_FAILED", message, cause);
    }
}

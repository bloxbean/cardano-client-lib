package com.bloxbean.cardano.client.txflow.stream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned JSON codec for the non-columnar payloads a durable
 * {@link TxStreamStateStore} persists: the planned-record binding/member
 * metadata and the batch member-id lists.
 *
 * <p>The codec mirrors the discipline of the engine store's
 * {@code FlowStoreCodec}: every encoded document carries a stable
 * {@link #FORMAT_ID format identifier} and a monotonically increasing
 * {@link #CURRENT_FORMAT_VERSION version}; unknown formats, newer versions,
 * malformed input, and unsupported value discriminators fail closed with a
 * stable {@link TxStreamException}. Binding scalar values are tagged with an
 * explicit type so an {@code Integer} does not silently round-trip to a
 * {@code Long}. This is a persistence format, not the portable authoring
 * format — the portable flow itself is stored verbatim as its own column.</p>
 *
 * <p>Secret values are never encoded here: the record only ever carries
 * non-sensitive bindings and secure-binding <em>references</em> plus
 * fingerprints (the inline sensitive channel is rejected upstream at bind
 * time). The codec neither scans nor scrubs; it faithfully round-trips exactly
 * what the caller classified as non-sensitive.</p>
 */
public final class TxStreamStoreCodec {
    /** Stable identifier for the stream-store payload envelope family. */
    public static final String FORMAT_ID = "ccl.txflow.stream-store";
    /** Current stream-store JSON envelope version. */
    public static final int CURRENT_FORMAT_VERSION = 1;
    /** Default upper bound for one encoded stream-store payload. */
    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;

    private static final String KIND_PLANNED = "planned_metadata";
    private static final String KIND_STRINGS = "string_list";
    private static final int MAX_CONTAINER_ENTRIES = 100_000;

    private final ObjectMapper json;

    private TxStreamStoreCodec() {
        this.json = new ObjectMapper(JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(DEFAULT_MAX_PAYLOAD_BYTES)
                        .maxNestingDepth(64)
                        .build())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /**
     * Creates the standard stateless codec.
     *
     * @return reusable codec instance
     */
    public static TxStreamStoreCodec standard() {
        return new TxStreamStoreCodec();
    }

    /**
     * Encodes the non-columnar part of a planned record: non-sensitive
     * bindings, secure-binding references, secure-binding fingerprints, and the
     * member list (each member carrying its per-item idempotency key).
     *
     * @param bindings non-sensitive portable scalar bindings
     * @param secureBindingReferences secure-binding references (opaque pointers)
     * @param secureBindingFingerprints secure-binding fingerprints
     * @param members planned member items
     * @param templateId template reference of a parameterized-invocation
     *        execution, or {@code null} for an inline-payload execution
     * @param templateFingerprint template-definition fingerprint at plan time,
     *        or {@code null} for an inline-payload execution
     * @return encoded JSON payload
     */
    public String encodePlannedMetadata(Map<String, Object> bindings,
                                        Map<String, String> secureBindingReferences,
                                        Map<String, String> secureBindingFingerprints,
                                        List<TxStreamPlannedRecord.Member> members,
                                        String templateId,
                                        String templateFingerprint) {
        ObjectNode root = envelope(KIND_PLANNED);
        if (templateId != null) {
            root.put("template_id", templateId);
        }
        if (templateFingerprint != null) {
            root.put("template_fingerprint", templateFingerprint);
        }
        ObjectNode bindingsNode = root.putObject("bindings");
        bindings.forEach((name, value) -> encodeScalar(bindingsNode.putObject(name), value));
        ObjectNode refsNode = root.putObject("secure_refs");
        secureBindingReferences.forEach(refsNode::put);
        ObjectNode fingerprintsNode = root.putObject("secure_fingerprints");
        secureBindingFingerprints.forEach(fingerprintsNode::put);
        ArrayNode membersNode = root.putArray("members");
        for (TxStreamPlannedRecord.Member member : members) {
            ObjectNode memberNode = membersNode.addObject();
            memberNode.put("item_id", member.itemId());
            memberNode.put("idempotency_key", member.idempotencyKey());
            memberNode.put("step_id", member.stepId());
            if (member.fingerprint() != null) {
                memberNode.put("fingerprint", member.fingerprint());
            }
        }
        return write(root);
    }

    /**
     * Decodes a planned-metadata payload.
     *
     * @param payload encoded JSON payload
     * @return decoded planned metadata
     */
    public PlannedMetadata decodePlannedMetadata(String payload) {
        JsonNode root = readEnvelope(payload, KIND_PLANNED);
        String templateId = root.hasNonNull("template_id")
                ? root.get("template_id").asText() : null;
        String templateFingerprint = root.hasNonNull("template_fingerprint")
                ? root.get("template_fingerprint").asText() : null;
        Map<String, Object> bindings = new LinkedHashMap<>();
        JsonNode bindingsNode = requireObject(root, "bindings");
        Iterator<Map.Entry<String, JsonNode>> fields = bindingsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            bindings.put(field.getKey(), decodeScalar(field.getValue()));
        }
        Map<String, String> refs = decodeStringMap(requireObject(root, "secure_refs"));
        Map<String, String> fingerprints =
                decodeStringMap(requireObject(root, "secure_fingerprints"));
        List<TxStreamPlannedRecord.Member> members = new ArrayList<>();
        JsonNode membersNode = root.get("members");
        if (membersNode == null || !membersNode.isArray()) {
            throw corrupt("planned metadata is missing its member list");
        }
        for (JsonNode memberNode : membersNode) {
            String fingerprint = memberNode.hasNonNull("fingerprint")
                    ? memberNode.get("fingerprint").asText() : null;
            members.add(new TxStreamPlannedRecord.Member(
                    requireText(memberNode, "item_id"),
                    requireText(memberNode, "idempotency_key"),
                    requireText(memberNode, "step_id"),
                    fingerprint));
        }
        return new PlannedMetadata(bindings, refs, fingerprints, members, templateId,
                templateFingerprint);
    }

    /**
     * Encodes an ordered list of strings (a batch's item ids or execution ids).
     *
     * @param values string list
     * @return encoded JSON payload
     */
    public String encodeStringList(List<String> values) {
        ObjectNode root = envelope(KIND_STRINGS);
        ArrayNode array = root.putArray("values");
        values.forEach(array::add);
        return write(root);
    }

    /**
     * Decodes a string-list payload.
     *
     * @param payload encoded JSON payload
     * @return decoded string list
     */
    public List<String> decodeStringList(String payload) {
        JsonNode root = readEnvelope(payload, KIND_STRINGS);
        JsonNode array = root.get("values");
        if (array == null || !array.isArray()) {
            throw corrupt("string-list payload is missing its values");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            if (!value.isTextual()) throw corrupt("string-list value is not textual");
            values.add(value.asText());
        }
        return values;
    }

    // ---- internals ----

    private ObjectNode envelope(String kind) {
        ObjectNode root = json.createObjectNode();
        root.put("f", FORMAT_ID);
        root.put("v", CURRENT_FORMAT_VERSION);
        root.put("kind", kind);
        return root;
    }

    private JsonNode readEnvelope(String payload, String expectedKind) {
        JsonNode root;
        try {
            root = json.readTree(Objects.requireNonNull(payload, "payload"));
        } catch (JsonProcessingException failure) {
            throw new TxStreamException("TXSTREAM_STORE_CODEC_DECODE_FAILED",
                    "Stream-store payload is not valid JSON", failure);
        }
        if (root == null || !root.isObject()) {
            throw corrupt("stream-store payload is not a JSON object");
        }
        if (!FORMAT_ID.equals(text(root, "f"))) {
            throw corrupt("stream-store payload has an unexpected format id");
        }
        JsonNode version = root.get("v");
        if (version == null || !version.isInt()) {
            throw corrupt("stream-store payload has no version");
        }
        if (version.asInt() > CURRENT_FORMAT_VERSION) {
            throw new TxStreamException("TXSTREAM_STORE_CODEC_UNSUPPORTED_VERSION",
                    "Stream-store payload version " + version.asInt()
                            + " is newer than this library supports");
        }
        if (!expectedKind.equals(text(root, "kind"))) {
            throw corrupt("stream-store payload has an unexpected kind");
        }
        return root;
    }

    private void encodeScalar(ObjectNode node, Object value) {
        if (value instanceof String stringValue) {
            node.put("t", "string");
            node.put("v", stringValue);
        } else if (value instanceof Boolean booleanValue) {
            node.put("t", "boolean");
            node.put("v", booleanValue);
        } else if (value instanceof Byte byteValue) {
            node.put("t", "byte");
            node.put("v", byteValue.intValue());
        } else if (value instanceof Short shortValue) {
            node.put("t", "short");
            node.put("v", shortValue.intValue());
        } else if (value instanceof Integer integerValue) {
            node.put("t", "integer");
            node.put("v", integerValue);
        } else if (value instanceof Long longValue) {
            node.put("t", "long");
            node.put("v", longValue);
        } else {
            throw new TxStreamException("TXSTREAM_STORE_CODEC_UNSUPPORTED",
                    "Stream-store bindings support only string, boolean, and integral values up to"
                            + " long; got " + (value == null ? "null" : value.getClass().getName()));
        }
    }

    private Object decodeScalar(JsonNode node) {
        if (node == null || !node.isObject()) throw corrupt("binding value is not an object");
        String type = text(node, "t");
        JsonNode value = node.get("v");
        if (type == null || value == null) throw corrupt("binding value is incomplete");
        return switch (type) {
            case "string" -> value.asText();
            case "boolean" -> value.asBoolean();
            case "byte" -> (byte) value.asInt();
            case "short" -> (short) value.asInt();
            case "integer" -> value.asInt();
            case "long" -> value.asLong();
            default -> throw corrupt("binding value has an unsupported type: " + type);
        };
    }

    private Map<String, String> decodeStringMap(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        int count = 0;
        while (fields.hasNext()) {
            if (++count > MAX_CONTAINER_ENTRIES) throw corrupt("payload map is too large");
            Map.Entry<String, JsonNode> field = fields.next();
            if (!field.getValue().isTextual()) throw corrupt("map value is not textual");
            result.put(field.getKey(), field.getValue().asText());
        }
        return result;
    }

    private JsonNode requireObject(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isObject()) {
            throw corrupt("payload is missing the object field '" + field + "'");
        }
        return node;
    }

    private String requireText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual()) {
            throw corrupt("payload is missing the text field '" + field + "'");
        }
        return node.asText();
    }

    private String text(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private String write(ObjectNode root) {
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException failure) {
            throw new TxStreamException("TXSTREAM_STORE_CODEC_ENCODE_FAILED",
                    "Stream-store payload could not be encoded", failure);
        }
    }

    private TxStreamException corrupt(String message) {
        return new TxStreamException("TXSTREAM_STORE_CODEC_CORRUPT", message);
    }

    /**
     * Decoded non-columnar planned-record metadata.
     *
     * @param bindings non-sensitive portable scalar bindings
     * @param secureBindingReferences secure-binding references
     * @param secureBindingFingerprints secure-binding fingerprints
     * @param members planned member items
     * @param templateId template reference, or {@code null} for an inline-payload
     *        execution
     * @param templateFingerprint template-definition fingerprint at plan time,
     *        or {@code null} for an inline-payload execution
     */
    public record PlannedMetadata(Map<String, Object> bindings,
                                  Map<String, String> secureBindingReferences,
                                  Map<String, String> secureBindingFingerprints,
                                  List<TxStreamPlannedRecord.Member> members,
                                  String templateId,
                                  String templateFingerprint) {
    }
}

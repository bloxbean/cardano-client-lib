package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.store.SignedPayloadVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.Map;

/**
 * Deterministic stream identities and the versioned item fingerprint.
 *
 * <p>Execution, flow, and step identities are pure functions of the stream
 * namespace and the item's idempotency claim key — never of counters,
 * timestamps, or window position — so a redelivered item produces the same
 * execution identity on every process and the write-ahead binding always names
 * the execution the engine will create or match.</p>
 */
final class StreamIdentities {
    /** Versioned fingerprint domain, mirroring the engine's fingerprint style. */
    static final String ITEM_FINGERPRINT_DOMAIN = "txstream-item:v1\n";
    /** Deterministic step id used for generated {@code TxPlan}-backed steps. */
    static final String GENERATED_STEP_ID = "item";

    private static final ObjectMapper JSON = new ObjectMapper();
    /**
     * Unambiguous identity separator: {@code FlowStoreTextPolicy} rejects NUL
     * in namespaces and claim keys, so the separator can never occur in either
     * component.
     */
    private static final char SEPARATOR = '\0';

    private StreamIdentities() {
    }

    /**
     * Returns the engine idempotency namespace for a stream.
     *
     * @param streamId stream identity
     * @return namespace {@code stream:<streamId>}
     */
    static String namespace(String streamId) {
        return "stream:" + streamId;
    }

    /**
     * Derives the deterministic execution identity for one claim.
     *
     * @param namespace stream idempotency namespace
     * @param claimKey item claim key
     * @return stable, {@code FlowStoreTextPolicy}-bounded execution id
     */
    static String executionId(String namespace, String claimKey) {
        return "txs-" + hash40(namespace + SEPARATOR + claimKey);
    }

    /**
     * Derives the deterministic generated flow identity for one claim.
     *
     * @param namespace stream idempotency namespace
     * @param claimKey item claim key
     * @return stable generated flow id
     */
    static String flowId(String namespace, String claimKey) {
        return flowId(namespace, java.util.List.of(claimKey));
    }

    /**
     * Derives the deterministic flow identity for a set of member claim keys.
     * <p>
     * The keys are sorted before hashing, so the same member set produces the
     * same flow id in any iteration order; for a single key this derivation
     * is byte-identical to {@link #flowId(String, String)}, keeping per-item
     * flow identities stable across stream versions.
     *
     * @param namespace stream idempotency namespace
     * @param memberClaimKeys member claim keys; sorted internally
     * @return stable generated flow id
     */
    static String flowId(String namespace, Collection<String> memberClaimKeys) {
        return "flow-" + hash40("txstream-flow:v1" + SEPARATOR + namespace
                + SEPARATOR + joinSorted(memberClaimKeys));
    }

    /**
     * Derives the deterministic step identity carrying one member item's
     * transaction inside a multi-item flow.
     *
     * @param memberClaimKey member claim key
     * @return stable step id, unique per member key
     */
    static String stepId(String memberClaimKey) {
        requireKey(memberClaimKey);
        return "s-" + hash40("txstream-step:v1" + SEPARATOR + memberClaimKey);
    }

    /**
     * Derives the deterministic flow-level claim key for a window of member
     * claim keys (the {@code perWindow()} claim; ADR 0004 Decision 3:
     * flow-level dedup only).
     *
     * @param memberClaimKeys member claim keys; sorted internally
     * @return stable, {@code FlowStoreTextPolicy}-bounded window claim key
     */
    static String windowClaimKey(Collection<String> memberClaimKeys) {
        return "w-" + hash40("txstream-window:v1" + SEPARATOR + joinSorted(memberClaimKeys));
    }

    /**
     * Derives the deterministic id of the single merged step carrying every
     * member of a batched flow (the {@code batching()} planner). All members
     * of one batched transaction share this step id, so item status becomes
     * transaction-granular — they literally share one transaction's fate. The
     * id is a pure function of the sorted member claim keys, so the same
     * batched member set produces a byte-identical merged step id across
     * restarts and submit order.
     *
     * @param memberClaimKeys member claim keys of the batched group; sorted internally
     * @return stable, per-member-set merged step id
     */
    static String mergedStepId(Collection<String> memberClaimKeys) {
        return "m-" + hash40("txstream-merged-step:v1" + SEPARATOR + joinSorted(memberClaimKeys));
    }

    private static String joinSorted(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("memberIdempotencyKeys cannot be null or empty");
        }
        java.util.List<String> sorted = new java.util.ArrayList<>(keys.size());
        for (String key : keys) {
            requireKey(key);
            sorted.add(key);
        }
        java.util.Collections.sort(sorted);
        return String.join(String.valueOf(SEPARATOR), sorted);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("member idempotency key cannot be blank");
        }
    }

    /**
     * Creates the {@link StableIdFactory} exposed to planners for one stream
     * namespace — the single supported derivation for custom planner flow and
     * step ids (claim-derived, sorted member keys; see the SPI obligation on
     * {@link StableIdFactory}).
     *
     * @param namespace stream idempotency namespace
     * @return deterministic id factory
     */
    static StableIdFactory idFactory(String namespace) {
        return new StableIdFactory() {
            @Override
            public String flowId(Collection<String> memberIdempotencyKeys) {
                return StreamIdentities.flowId(namespace, memberIdempotencyKeys);
            }

            @Override
            public String stepId(String memberIdempotencyKey) {
                return StreamIdentities.stepId(memberIdempotencyKey);
            }
        };
    }

    /**
     * Computes the versioned item content fingerprint over every
     * planner-visible field.
     *
     * @param itemId item identity
     * @param claimKey effective idempotency claim key
     * @param laneName lane label the item was submitted on
     * @param metadata key-ordered item metadata
     * @param portablePayload portable JSON encoding of the generated flow
     * @param bindings key-ordered non-sensitive bindings
     * @param secureBindingReferences key-ordered secure-binding references
     * @param sensitiveBindings key-ordered inline sensitive bindings
     * @return versioned fingerprint string
     */
    static String itemFingerprint(String itemId, String claimKey, String laneName,
                                  Map<String, String> metadata, String portablePayload,
                                  Map<String, Object> bindings,
                                  Map<String, String> secureBindingReferences,
                                  Map<String, Object> sensitiveBindings) {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("item_id", itemId);
        canonical.put("idempotency_key", claimKey);
        canonical.put("lane", laneName);
        ObjectNode metadataNode = canonical.putObject("metadata");
        metadata.forEach(metadataNode::put);
        canonical.put("payload", portablePayload);
        ObjectNode bindingsNode = canonical.putObject("bindings");
        bindings.forEach((key, value) -> bindingsNode.put(key, String.valueOf(value)));
        ObjectNode secureNode = canonical.putObject("secure_bindings");
        secureBindingReferences.forEach(secureNode::put);
        ObjectNode sensitiveNode = canonical.putObject("sensitive_bindings");
        sensitiveBindings.forEach((key, value) -> sensitiveNode.put(key, String.valueOf(value)));
        return ITEM_FINGERPRINT_DOMAIN + SignedPayloadVerifier.sha256(canonical.toString());
    }

    /**
     * Computes the versioned item content fingerprint for a template item over
     * every planner-visible field, keyed by the TEMPLATE id instead of an inline
     * portable payload (ADR 0004, iteration 3). Same template + same bindings +
     * same lane/metadata redelivery attaches; different bindings conflict. The
     * {@code template} field is structurally distinct from the {@code payload}
     * field of
     * {@link #itemFingerprint(String, String, String, Map, String, Map, Map, Map)},
     * so a template fingerprint can never collide with an inline-payload one.
     * Sensitive values never enter the fingerprint as plaintext beyond what the
     * inline sensitive channel already exposes (mirroring the inline-payload
     * fingerprint); secure references contribute their reference string.
     *
     * @param itemId item identity
     * @param claimKey effective idempotency claim key
     * @param laneName lane label the item was submitted on
     * @param metadata key-ordered item metadata
     * @param templateId the pre-registered template id this item invokes
     * @param bindings key-ordered non-sensitive bindings
     * @param secureBindingReferences key-ordered secure-binding references
     * @param sensitiveBindings key-ordered inline sensitive bindings
     * @return versioned fingerprint string
     */
    static String templateItemFingerprint(String itemId, String claimKey, String laneName,
                                          Map<String, String> metadata, String templateId,
                                          Map<String, Object> bindings,
                                          Map<String, String> secureBindingReferences,
                                          Map<String, Object> sensitiveBindings) {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("item_id", itemId);
        canonical.put("idempotency_key", claimKey);
        canonical.put("lane", laneName);
        ObjectNode metadataNode = canonical.putObject("metadata");
        metadata.forEach(metadataNode::put);
        canonical.put("template", templateId);
        ObjectNode bindingsNode = canonical.putObject("bindings");
        bindings.forEach((key, value) -> bindingsNode.put(key, String.valueOf(value)));
        ObjectNode secureNode = canonical.putObject("secure_bindings");
        secureBindingReferences.forEach(secureNode::put);
        ObjectNode sensitiveNode = canonical.putObject("sensitive_bindings");
        sensitiveBindings.forEach((key, value) -> sensitiveNode.put(key, String.valueOf(value)));
        return ITEM_FINGERPRINT_DOMAIN + SignedPayloadVerifier.sha256(canonical.toString());
    }

    /**
     * Computes the redacted fingerprint of a secure-binding reference for the
     * durable planned record, so the persisted plan can carry a
     * tamper-evident digest of each reference without exposing anything the
     * reference does not already expose.
     *
     * @param reference secure-binding reference
     * @return versioned reference fingerprint
     */
    static String secureRefFingerprint(String reference) {
        return "txstream-secref:v1:" + hash40("txstream-secref:v1" + SEPARATOR + reference);
    }

    /**
     * Derives a stable, well-distributed partition index in {@code [0, n)} for
     * a claim key — the lane assignment of {@link LanePolicy#partitioned}. The
     * index is a pure function of the SHA-256 of the claim key, so a given key
     * lands on the same lane on every process and after any restart; the hash
     * decorrelates it from the key's own byte structure for even lane balance.
     *
     * @param claimKey item claim key
     * @param n lane count (positive)
     * @return partition index in {@code [0, n)}
     */
    static int partitionIndex(String claimKey, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("partition count must be positive");
        }
        requireKey(claimKey);
        // First 15 hex digits of the digest as a non-negative long, mod n.
        long value = Long.parseLong(
                SignedPayloadVerifier.sha256(claimKey).substring(0, 15), 16);
        return (int) Math.floorMod(value, (long) n);
    }

    /**
     * Returns a stable 40-hex-character fingerprint of a value — used for the
     * fan-out bootstrap claim key, where the input covers the funding source,
     * lane addresses, and seed.
     *
     * @param value canonical fingerprint input
     * @return 40-hex-character digest
     */
    static String fingerprint40(String value) {
        return hash40(value);
    }

    private static String hash40(String value) {
        return SignedPayloadVerifier.sha256(value).substring(0, 40);
    }
}

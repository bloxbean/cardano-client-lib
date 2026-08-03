package com.bloxbean.cardano.client.txflow.stream;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Durable planning record for one engine execution, persisted alongside its
 * write-ahead bindings so a crash between {@code bind} and {@code start} can be
 * re-dispatched without depending on source redelivery (ADR 0004 Decision 5).
 *
 * <p>The record captures exactly what restart re-attach needs to rebuild a
 * {@link com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest} for an
 * execution whose engine snapshot is absent: the deterministic execution id,
 * the flow-level idempotency claim, the lane label and canonical spending
 * identity, the <b>portable-encoded flow</b> (a {@code TxFlowCodec} JSON
 * {@code V1ALPHA1} string — never a live Java object), the non-sensitive
 * bindings, and the secure-binding <em>references</em> plus their
 * fingerprints.</p>
 *
 * <p><b>What the no-secrets guarantee actually is.</b> The single mechanical
 * guarantee is that the <em>sanctioned sensitive channel</em> —
 * {@link TxWorkItem.Builder#withSensitiveBinding(String, Object) withSensitiveBinding},
 * an inline literal secret — is rejected at persist time (the item fails typed
 * {@code TXSTREAM_NON_PERSISTABLE_SECRET} at bind before this record is ever
 * built). Everything else is persisted <b>verbatim under caller-classification
 * trust</b>: non-sensitive bindings, secure-reference strings, item metadata,
 * and the portable flow itself are stored exactly as supplied. The stream does
 * not scan, redact, or scrub them. A secret that a caller mis-declares as a
 * non-sensitive {@code withBinding}, embeds inside the {@code TxPlan} the
 * portable flow encodes, or writes into item metadata is <b>not</b> caught and
 * <b>will</b> be persisted. Secure references are treated as opaque pointers,
 * not secrets, and are resolved afresh through the engine's secure-binding
 * mechanism on re-dispatch exactly as on first dispatch; the resolved value is
 * never present here. Classifying which parameters are sensitive is the
 * caller's responsibility — use {@code withSecureBindingReference} for anything
 * secret.</p>
 *
 * @param streamId owning stream id
 * @param executionId deterministic engine execution identity
 * @param idempotencyKey flow-level idempotency claim key
 * @param laneName user-facing lane label
 * @param canonicalSpendingIdentity canonical spending identity the execution
 *        serializes on and declares as its spending resource
 * @param portableFlow portable {@code TxFlowCodec} JSON ({@code V1ALPHA1})
 *        encoding of the planned flow — never a Java object
 * @param bindings non-sensitive portable scalar bindings
 * @param secureBindingReferences secure-binding references keyed by parameter
 *        name (opaque pointers, never secret values)
 * @param secureBindingFingerprints redacted fingerprints of the secure-binding
 *        references, keyed by parameter name
 * @param members the execution's member items, each mapping an item id to its
 *        planned step id and content fingerprint
 * @param templateId when the execution is a parameterized invocation of a
 *        pre-registered template (ADR 0004, iteration 3), the template
 *        <em>reference</em> — {@code null} for an ordinary inline-payload
 *        execution. A durable stream persists the ref so re-attach re-dispatch
 *        can re-resolve it against the re-registered template (a template that
 *        is not re-registered on restart fails the item typed
 *        {@code TXSTREAM_TEMPLATE_UNKNOWN} rather than being silently lost); the
 *        {@code portableFlow} is still stored (the registered template's own
 *        encoding) so member reconstruction has a valid step to carry.
 * @param templateFingerprint for a template execution, the fingerprint of the
 *        template definition as registered when the execution was planned
 *        ({@code null} for an inline-payload execution). Re-attach re-dispatch
 *        compares it against the CURRENTLY re-registered template's fingerprint
 *        and fails the item typed {@code TXSTREAM_TEMPLATE_DRIFT} on a mismatch
 *        — an operator who re-registers a <em>different</em> definition under
 *        the same id must not silently run the wrong flow under the original
 *        claim (fail-fast, mirroring the fan-out bootstrap config-drift guard).
 */
public record TxStreamPlannedRecord(String streamId, String executionId, String idempotencyKey,
                                    String laneName, String canonicalSpendingIdentity,
                                    String portableFlow, Map<String, Object> bindings,
                                    Map<String, String> secureBindingReferences,
                                    Map<String, String> secureBindingFingerprints,
                                    List<Member> members, String templateId,
                                    String templateFingerprint) {
    /**
     * Validates and defensively copies the record.
     *
     * @param streamId non-blank owning stream id
     * @param executionId non-blank deterministic execution identity
     * @param idempotencyKey non-blank flow-level idempotency claim key
     * @param laneName non-blank lane label
     * @param canonicalSpendingIdentity non-blank canonical spending identity
     * @param portableFlow non-blank portable flow encoding
     * @param bindings non-sensitive bindings; copied
     * @param secureBindingReferences secure references; copied
     * @param secureBindingFingerprints secure-reference fingerprints; copied
     * @param members non-empty member list; copied
     * @param templateId template reference, or {@code null} for an inline-payload
     *        execution
     * @param templateFingerprint template-definition fingerprint at plan time,
     *        or {@code null} for an inline-payload execution
     */
    public TxStreamPlannedRecord {
        requireNonBlank(streamId, "streamId");
        requireNonBlank(executionId, "executionId");
        requireNonBlank(idempotencyKey, "idempotencyKey");
        requireNonBlank(laneName, "laneName");
        requireNonBlank(canonicalSpendingIdentity, "canonicalSpendingIdentity");
        requireNonBlank(portableFlow, "portableFlow");
        bindings = Map.copyOf(bindings != null ? bindings : Map.of());
        secureBindingReferences = Map.copyOf(
                secureBindingReferences != null ? secureBindingReferences : Map.of());
        secureBindingFingerprints = Map.copyOf(
                secureBindingFingerprints != null ? secureBindingFingerprints : Map.of());
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (members.isEmpty()) {
            throw new IllegalArgumentException("members cannot be empty");
        }
    }

    /**
     * Convenience constructor for an ordinary inline-payload execution (no
     * template reference). Delegates to the canonical constructor with a
     * {@code null} {@code templateId} and {@code null} {@code templateFingerprint}.
     *
     * @param streamId non-blank owning stream id
     * @param executionId non-blank deterministic execution identity
     * @param idempotencyKey non-blank flow-level idempotency claim key
     * @param laneName non-blank lane label
     * @param canonicalSpendingIdentity non-blank canonical spending identity
     * @param portableFlow non-blank portable flow encoding
     * @param bindings non-sensitive bindings; copied
     * @param secureBindingReferences secure references; copied
     * @param secureBindingFingerprints secure-reference fingerprints; copied
     * @param members non-empty member list; copied
     */
    public TxStreamPlannedRecord(String streamId, String executionId, String idempotencyKey,
                                 String laneName, String canonicalSpendingIdentity,
                                 String portableFlow, Map<String, Object> bindings,
                                 Map<String, String> secureBindingReferences,
                                 Map<String, String> secureBindingFingerprints,
                                 List<Member> members) {
        this(streamId, executionId, idempotencyKey, laneName, canonicalSpendingIdentity,
                portableFlow, bindings, secureBindingReferences, secureBindingFingerprints,
                members, null, null);
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
    }

    /**
     * Renders the record without dumping secure-binding reference <em>values</em>
     * (opaque pointers, not secrets, but kept out of logs for tidiness) — only
     * their parameter names appear. Non-sensitive bindings, metadata, and the
     * portable flow are shown as stored (see the class-level trust note).
     *
     * @return redacted string form
     */
    @Override
    public String toString() {
        return "TxStreamPlannedRecord{streamId=" + streamId
                + ", executionId=" + executionId
                + ", idempotencyKey=" + idempotencyKey
                + ", laneName=" + laneName
                + ", canonicalSpendingIdentity=" + canonicalSpendingIdentity
                + ", portableFlow=" + portableFlow
                + ", bindings=" + bindings
                + ", secureBindingReferences=" + secureBindingReferences.keySet() + "=<redacted>"
                + ", secureBindingFingerprints=" + secureBindingFingerprints
                + ", members=" + members
                + ", templateId=" + templateId
                + ", templateFingerprint=" + templateFingerprint + '}';
    }

    /**
     * One member item of a persisted planned execution.
     *
     * @param itemId caller-visible item identity
     * @param idempotencyKey the item's own per-item claim key (the flow-level
     *        {@link TxStreamPlannedRecord#idempotencyKey()} may differ under a
     *        window planner), so restart re-attach can rebuild the
     *        idempotency-key-reuse guard for every member
     * @param stepId the item's planned flow step id
     * @param fingerprint the item's versioned content fingerprint, so a
     *        redelivery after re-attach attaches instead of conflicting
     */
    public record Member(String itemId, String idempotencyKey, String stepId, String fingerprint) {
        /**
         * Validates the member.
         *
         * @param itemId non-blank item id
         * @param idempotencyKey non-blank per-item claim key
         * @param stepId non-blank step id
         * @param fingerprint content fingerprint (nullable)
         */
        public Member {
            requireNonBlank(itemId, "itemId");
            requireNonBlank(idempotencyKey, "idempotencyKey");
            requireNonBlank(stepId, "stepId");
        }
    }
}

package com.bloxbean.cardano.client.txflow.stream;

/**
 * Stable error-code catalog for TxStream behavior emitted by the core
 * {@code txflow} module.
 *
 * <p>Callers may compare {@link TxStreamException#getCode()} with these
 * constants without duplicating string literals. Extension modules own their
 * own catalogs; this class intentionally contains only core codes.</p>
 */
public final class TxStreamCodes {
    /** Accepted work was recovered without an execution binding. */
    public static final String ABANDONED = "TXSTREAM_ABANDONED";
    /** Stream abort cancelled work before its outcome was terminal. */
    public static final String ABORTED = "TXSTREAM_ABORTED";
    /** Reject-mode batching received an item it cannot safely merge. */
    public static final String BATCH_INELIGIBLE_ITEM = "TXSTREAM_BATCH_INELIGIBLE_ITEM";
    /** Durable item-to-execution binding failed. */
    public static final String BINDING_FAILED = "TXSTREAM_BINDING_FAILED";
    /** A required durable item-to-execution binding is absent. */
    public static final String BINDING_MISSING = "TXSTREAM_BINDING_MISSING";
    /** Partitioned-lane bootstrap configuration differs from stored state. */
    public static final String BOOTSTRAP_CONFIG_DRIFT = "TXSTREAM_BOOTSTRAP_CONFIG_DRIFT";
    /** Partitioned-lane bootstrap failed. */
    public static final String BOOTSTRAP_FAILED = "TXSTREAM_BOOTSTRAP_FAILED";
    /** Stream is not accepting because it is new, draining, or closed. */
    public static final String CLOSED = "TXSTREAM_CLOSED";
    /** Dispatch failed before a safely observed engine execution existed. */
    public static final String DISPATCH_FAILED = "TXSTREAM_DISPATCH_FAILED";
    /** Drain failed for a reason other than its caller timeout. */
    public static final String DRAIN_FAILED = "TXSTREAM_DRAIN_FAILED";
    /** Item id was reused with different registered content. */
    public static final String DUPLICATE_ITEM = "TXSTREAM_DUPLICATE_ITEM";
    /** An engine execution was cancelled. */
    public static final String EXECUTION_CANCELLED = "TXSTREAM_EXECUTION_CANCELLED";
    /** Engine execution observation or completion failed. */
    public static final String EXECUTION_FAILED = "TXSTREAM_EXECUTION_FAILED";
    /** Engine start succeeded but no observer could be attached safely. */
    public static final String EXECUTION_UNOBSERVABLE = "TXSTREAM_EXECUTION_UNOBSERVABLE";
    /** Idempotency key is already associated with another item id. */
    public static final String IDEMPOTENCY_KEY_REUSE = "TXSTREAM_IDEMPOTENCY_KEY_REUSE";
    /** A blocking stream operation was interrupted. */
    public static final String INTERRUPTED = "TXSTREAM_INTERRUPTED";
    /** Item identity or content violates the stream contract. */
    public static final String INVALID_ITEM = "TXSTREAM_INVALID_ITEM";
    /** Item reached the cancelled outcome. */
    public static final String ITEM_CANCELLED = "TXSTREAM_ITEM_CANCELLED";
    /** Item failed and no more specific failure code is available. */
    public static final String ITEM_FAILED = "TXSTREAM_ITEM_FAILED";
    /** No item with the requested id is known. */
    public static final String ITEM_UNKNOWN = "TXSTREAM_ITEM_UNKNOWN";
    /** A transaction declares both address and resource-reference funding. */
    public static final String LANE_AMBIGUOUS = "TXSTREAM_LANE_AMBIGUOUS";
    /** Supplied lane does not match the configured or derived lane. */
    public static final String LANE_MISMATCH = "TXSTREAM_LANE_MISMATCH";
    /** The selected lane policy requires an explicit lane name. */
    public static final String LANE_REQUIRED = "TXSTREAM_LANE_REQUIRED";
    /** Two configured lanes claim overlapping funding scopes. */
    public static final String LANE_SCOPE_OVERLAP = "TXSTREAM_LANE_SCOPE_OVERLAP";
    /** Transaction content draws funds outside its resolved lane. */
    public static final String LANE_SCOPE_VIOLATION = "TXSTREAM_LANE_SCOPE_VIOLATION";
    /** No single funding source can be derived from the item. */
    public static final String LANE_UNDERIVABLE = "TXSTREAM_LANE_UNDERIVABLE";
    /** The lane resolver failed or returned no resolved lane. */
    public static final String LANE_UNRESOLVED = "TXSTREAM_LANE_UNRESOLVED";
    /** Durable work contains a secret that cannot be persisted safely. */
    public static final String NON_PERSISTABLE_SECRET = "TXSTREAM_NON_PERSISTABLE_SECRET";
    /** Item payload is not portable to the engine contract. */
    public static final String NON_PORTABLE_ITEM = "TXSTREAM_NON_PORTABLE_ITEM";
    /** Stream is temporarily not active, normally because it is standby. */
    public static final String NOT_ACTIVE = "TXSTREAM_NOT_ACTIVE";
    /** Ownership mutation was rejected by an epoch fence. */
    public static final String OWNERSHIP_FENCED = "TXSTREAM_OWNERSHIP_FENCED";
    /** This instance lost active ownership of the stream. */
    public static final String OWNERSHIP_LOST = "TXSTREAM_OWNERSHIP_LOST";
    /** Durable planned-execution encoding failed. */
    public static final String PLANNED_ENCODE_FAILED = "TXSTREAM_PLANNED_ENCODE_FAILED";
    /** Durable planned-execution persistence failed. */
    public static final String PLANNED_WRITE_FAILED = "TXSTREAM_PLANNED_WRITE_FAILED";
    /** Planner threw, returned null, or produced no usable plan. */
    public static final String PLANNER_FAILED = "TXSTREAM_PLANNER_FAILED";
    /** One planned engine flow contains members from multiple lanes. */
    public static final String PLAN_CROSS_LANE = "TXSTREAM_PLAN_CROSS_LANE";
    /** Planner output violates mapping or identity invariants. */
    public static final String PLAN_INVALID = "TXSTREAM_PLAN_INVALID";
    /** Planner omitted an accepted item from its output. */
    public static final String PLAN_OMITTED = "TXSTREAM_PLAN_OMITTED";
    /** Stream could not project an engine outcome to an item. */
    public static final String PROJECTION_FAILED = "TXSTREAM_PROJECTION_FAILED";
    /** Durable reattachment found a cancelled execution. */
    public static final String REATTACH_CANCELLED = "TXSTREAM_REATTACH_CANCELLED";
    /** Durable reattachment failed without a more specific code. */
    public static final String REATTACH_FAILED = "TXSTREAM_REATTACH_FAILED";
    /** Durable reattachment cannot find the item's mapped step. */
    public static final String REATTACH_STEP_MISSING = "TXSTREAM_REATTACH_STEP_MISSING";
    /** Durable reattachment cannot prove the transaction confirmed. */
    public static final String REATTACH_UNCONFIRMED = "TXSTREAM_REATTACH_UNCONFIRMED";
    /** Submitted transaction has an uncertain on-chain disposition. */
    public static final String RECOVERY_REQUIRED = "TXSTREAM_RECOVERY_REQUIRED";
    /** Authoritative item registration failed. */
    public static final String REGISTRATION_FAILED = "TXSTREAM_REGISTRATION_FAILED";
    /** Attached work source terminated with an error. */
    public static final String SOURCE_FAILED = "TXSTREAM_SOURCE_FAILED";
    /** Persisted stream value is structurally corrupt. */
    public static final String STORE_CODEC_CORRUPT = "TXSTREAM_STORE_CODEC_CORRUPT";
    /** Persisted stream value could not be decoded. */
    public static final String STORE_CODEC_DECODE_FAILED = "TXSTREAM_STORE_CODEC_DECODE_FAILED";
    /** Stream value could not be encoded for persistence. */
    public static final String STORE_CODEC_ENCODE_FAILED = "TXSTREAM_STORE_CODEC_ENCODE_FAILED";
    /** Persisted stream value uses an unsupported codec shape. */
    public static final String STORE_CODEC_UNSUPPORTED = "TXSTREAM_STORE_CODEC_UNSUPPORTED";
    /** Persisted stream value uses an unsupported codec version. */
    public static final String STORE_CODEC_UNSUPPORTED_VERSION =
            "TXSTREAM_STORE_CODEC_UNSUPPORTED_VERSION";
    /** A Flow subscriber could not keep up with bounded delivery. */
    public static final String SUBSCRIBER_OVERFLOW = "TXSTREAM_SUBSCRIBER_OVERFLOW";
    /** Registered template differs from the durable definition. */
    public static final String TEMPLATE_DRIFT = "TXSTREAM_TEMPLATE_DRIFT";
    /** Item references a template that is not registered. */
    public static final String TEMPLATE_UNKNOWN = "TXSTREAM_TEMPLATE_UNKNOWN";
    /** A caller-provided wait or drain budget expired. */
    public static final String TIMEOUT = "TXSTREAM_TIMEOUT";
    /** Stream dispatcher is unhealthy and cannot safely run work. */
    public static final String UNHEALTHY = "TXSTREAM_UNHEALTHY";

    private TxStreamCodes() {
    }
}

package com.bloxbean.cardano.client.txflow.stream;

import java.util.Objects;

/**
 * Lane configuration for a {@link TxFlowStream}.
 * <p>
 * A lane is a funding scope — a set of UTXOs (a funding address or a designated
 * funding chain) that at most one in-flight execution may spend at a time.
 * Items on different lanes execute concurrently; items on the same lane
 * serialize FIFO. Four policies decide how an item's lane is chosen:
 * <ul>
 *   <li>{@link #single(ResolvedLane)} — the whole stream runs on one
 *       statically resolved lane, validated when the stream is built. Items
 *       need no lane name; an item that does name a lane must name this lane,
 *       and a different name fails the item typed
 *       ({@code TXSTREAM_LANE_MISMATCH}) — a lane name is never a silent
 *       scheduling-only label. No {@link LaneIdentityResolver} is required
 *       (one configured on the builder is ignored).</li>
 *   <li>{@link #explicit()} — every item
 *       {@link TxWorkItem.Builder#withLane(String) names its lane}, resolved
 *       dynamically through a required {@link LaneIdentityResolver} at first
 *       use and cached. An item without a lane fails typed
 *       ({@code TXSTREAM_LANE_REQUIRED}); an unresolvable name fails the item
 *       typed ({@code TXSTREAM_LANE_UNRESOLVED}) at planning time. Items on
 *       different canonical identities execute concurrently, bounded by the
 *       builder's {@code maxInFlight}; alias names resolving to one identity
 *       share a single FIFO.</li>
 *   <li>{@link #byFundingAddress()} — the stream <em>derives</em> each item's
 *       lane from its transaction's own funding source (its {@code from}
 *       address or {@code from_ref}). No resolver, no per-item lane name, and
 *       no bootstrap: items from different senders lane concurrently while
 *       items from the same sender serialize (the canonical-identity scheduler
 *       already does this). An item whose transaction names no funding source
 *       fails typed ({@code TXSTREAM_LANE_UNDERIVABLE}); an item that also
 *       names a lane must name the derived one or fails
 *       ({@code TXSTREAM_LANE_MISMATCH}). Because the lane <em>is</em> the
 *       funding source, its lane-scoped coin selection is trivially
 *       satisfied.</li>
 *   <li>{@link #partitioned(PartitionedLanes)} — the full UTXO throughput
 *       story: the caller supplies one funding source and N application-owned
 *       lane addresses, items are assigned to a lane by
 *       {@code hash(idempotencyKey) % N} (stable across restarts), and the N
 *       lanes run concurrently — throughput scales with lanes, not block time.
 *       An optional one-time <em>fan-out bootstrap</em> splits the funding
 *       source into the N lane UTXOs before the stream opens for work (see
 *       {@link PartitionedLanes}). Each item's transaction is pinned to its
 *       assigned lane address by the same lane-scoped coin selection the other
 *       policies use.</li>
 * </ul>
 * The public factories always return a {@code LanePolicy}; the mode is an
 * internal detail.
 */
public final class LanePolicy {
    /** Internal discriminator over the four lane-assignment modes. */
    enum Mode {
        /** One statically configured lane for the whole stream. */
        SINGLE,
        /** Item-named lanes resolved dynamically through a resolver. */
        EXPLICIT,
        /** Lane derived from the item transaction's own funding source. */
        BY_FUNDING_ADDRESS,
        /** Hash-partitioned across N application-provided lane addresses. */
        PARTITIONED
    }

    private final Mode mode;
    private final ResolvedLane singleLane;          // SINGLE only
    private final PartitionedLanes partitioning;    // PARTITIONED only

    private LanePolicy(Mode mode, ResolvedLane singleLane, PartitionedLanes partitioning) {
        this.mode = mode;
        this.singleLane = singleLane;
        this.partitioning = partitioning;
    }

    /**
     * Configures the whole stream to run on one statically resolved lane.
     *
     * @param lane statically resolved lane, validated at stream build time
     * @return single-lane policy
     */
    public static LanePolicy single(ResolvedLane lane) {
        return new LanePolicy(Mode.SINGLE, Objects.requireNonNull(lane, "lane"), null);
    }

    /**
     * Configures dynamically named lanes: each item names its lane and a
     * {@link LaneIdentityResolver} — required on the stream builder — resolves
     * the name to its canonical identity and funding scope once, at first use.
     *
     * @return explicit lane policy
     */
    public static LanePolicy explicit() {
        return new LanePolicy(Mode.EXPLICIT, null, null);
    }

    /**
     * Configures the stream to derive each item's lane from its transaction's
     * own funding source — the item's {@code from} address (an
     * {@link ResolvedLane#ofAddress(String, String) address lane}) or its
     * {@code from_ref} (an {@link ResolvedLane#ofFundingRef(String, String)
     * funding-ref lane}). The lane name is the funding-source string, and its
     * canonical identity is derived from it, so items from the same sender
     * share one lane (serialize) and items from different senders run
     * concurrently. No {@link LaneIdentityResolver} and no bootstrap are
     * required. An item whose transaction names no funding source fails typed
     * {@code TXSTREAM_LANE_UNDERIVABLE}; an item that also
     * {@link TxWorkItem.Builder#withLane(String) names a lane} must name the
     * derived one, or fails {@code TXSTREAM_LANE_MISMATCH}.
     *
     * @return funding-address-derived lane policy
     */
    public static LanePolicy byFundingAddress() {
        return new LanePolicy(Mode.BY_FUNDING_ADDRESS, null, null);
    }

    /**
     * Configures hash-partitioned lanes over N application-provided lane
     * addresses, with an optional one-time fan-out bootstrap that splits the
     * funding source into the N lane UTXOs (ADR 0004 Decision 2; Open Question 3
     * resolved as application-provided addresses + caller-set seed + optional
     * bootstrap). Items are assigned to a lane by
     * {@code hash(idempotencyKey) % N}, deterministically and stably across
     * restarts.
     * <p>
     * <b>The configuration is stability-critical and funds-at-stake:</b> the
     * funding source, seed, lane count N, and the lane-address list <em>including
     * its order</em> must be byte-stable across restarts — changing any of them
     * re-splits the funding wallet, and reordering the lane addresses remaps
     * items to different lanes. A durable stream fails
     * {@link TxFlowStream#start() start} typed
     * ({@code TXSTREAM_BOOTSTRAP_CONFIG_DRIFT}) on such a change before submitting
     * a split; a non-durable stream cannot detect it. See {@link PartitionedLanes}.
     *
     * @param config partitioning configuration (funding source, lane
     *        addresses, seed per lane, and whether to run the bootstrap)
     * @return partitioned lane policy
     */
    public static LanePolicy partitioned(PartitionedLanes config) {
        return new LanePolicy(Mode.PARTITIONED, null,
                Objects.requireNonNull(config, "config"));
    }

    /** The lane-assignment mode. */
    Mode mode() {
        return mode;
    }

    /** Whether items name their lanes dynamically ({@link #explicit()}). */
    boolean isExplicit() {
        return mode == Mode.EXPLICIT;
    }

    /** The statically configured lane, or {@code null} outside {@link #single(ResolvedLane)}. */
    ResolvedLane staticLane() {
        return singleLane;
    }

    /** The partitioning configuration, or {@code null} outside {@link #partitioned(PartitionedLanes)}. */
    PartitionedLanes partitioning() {
        return partitioning;
    }
}

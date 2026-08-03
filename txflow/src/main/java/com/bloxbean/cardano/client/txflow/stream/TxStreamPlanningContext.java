package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.FlowStep;

import java.util.List;
import java.util.Map;

/**
 * Input handed to a {@link TxStreamPlanner} for one closed window.
 * <p>
 * The context carries the window's live items in acceptance order and the
 * {@link StableIdFactory} custom planners are required to derive their flow
 * and step identities from. Planning must be pure: no I/O, no clocks, no
 * randomness — the same items must produce a byte-identical plan (see
 * {@link StableIdFactory} for why).
 */
public final class TxStreamPlanningContext {
    private final String streamId;
    private final List<TxWorkItem> items;
    private final StableIdFactory ids;
    /** Accept-time planning seeds by item id, for the built-in planners. */
    private final Map<String, PlanningSeed> seeds;

    TxStreamPlanningContext(String streamId, List<TxWorkItem> items,
                            StableIdFactory ids, Map<String, PlanningSeed> seeds) {
        this.streamId = streamId;
        this.items = List.copyOf(items);
        this.ids = ids;
        this.seeds = Map.copyOf(seeds);
    }

    /**
     * Returns the id of the stream that owns this window.
     *
     * @return stream id
     */
    public String streamId() {
        return streamId;
    }

    /**
     * Returns the window's items in acceptance order. Every item has already
     * passed submit-time validation (portability, lane resolution, funding
     * scope); every mapping the planner emits must reference these items and
     * no others.
     *
     * @return immutable window item list
     */
    public List<TxWorkItem> items() {
        return items;
    }

    /**
     * Returns the deterministic identity factory planners must use for flow
     * and step ids.
     *
     * @return stable id factory scoped to this stream's claim namespace
     */
    public StableIdFactory ids() {
        return ids;
    }

    /** Returns the accept-time seed for one window item. */
    PlanningSeed seed(String itemId) {
        return seeds.get(itemId);
    }

    /**
     * Accept-time planning material for one item: the effective claim key,
     * the lane the item resolved to, and the item's validated, funding-scope
     * enforced single-transaction step.
     */
    static final class PlanningSeed {
        final String claimKey;
        final ResolvedLane lane;
        final FlowStep enforcedStep;

        PlanningSeed(String claimKey, ResolvedLane lane, FlowStep enforcedStep) {
            this.claimKey = claimKey;
            this.lane = lane;
            this.enforcedStep = enforcedStep;
        }
    }
}

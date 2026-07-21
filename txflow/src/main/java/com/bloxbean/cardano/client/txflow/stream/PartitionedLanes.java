package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for {@link LanePolicy#partitioned(PartitionedLanes)} — the
 * fan-out lane policy (ADR 0004 Decision 2; Open Question 3).
 * <p>
 * Open Question 3 ("who pays / who manages lane addresses") is resolved here as
 * <b>application-provided lane addresses with an optional, caller-funded
 * bootstrap</b>: the stream never manages keys. The caller supplies
 * <ul>
 *   <li>one <b>funding source</b> — a {@code from} address
 *       ({@link #fromAddress(String)}) or a {@code from_ref}
 *       ({@link #fromRef(String)}) — that the bootstrap draws from;</li>
 *   <li>N <b>lane addresses</b>, application-owned, one per lane; and</li>
 *   <li>a <b>seed amount per lane</b> — the value each lane UTXO is funded
 *       with during the bootstrap.</li>
 * </ul>
 * With {@code bootstrap} enabled (the default), the stream runs one idempotent
 * engine execution on {@link TxFlowStream#start() start} — before opening for
 * work — that builds a single transaction spending the funding source and
 * paying {@code seedPerLane} to each of the N lane addresses, so the N lanes
 * become disjoint funded UTXO sets that run concurrently. It runs exactly once:
 * its idempotency claim matches on a restart or a second stream instance, so
 * the wallet is never re-split. With {@code bootstrap} disabled the lanes are
 * assumed pre-funded and no split transaction is submitted.
 * <p>
 * Items are assigned to a lane by {@code hash(idempotencyKey) % N},
 * deterministically and stably across restarts, and each item's transaction is
 * pinned to its assigned lane address by the stream's lane-scoped coin
 * selection.
 *
 * <p><b>Stability is funds-critical (read before changing a running stream).</b>
 * The <em>funding source</em>, the <em>seed per lane</em>, the <em>lane count
 * N</em>, and the <em>lane-address list including its ORDER</em> are all
 * load-bearing across restarts and must be byte-stable:</p>
 * <ul>
 *   <li>They form the fan-out bootstrap's idempotency claim, so changing any of
 *       them mints a <em>new</em> claim — on a durable stream that means a new
 *       split that <b>re-drains the funding wallet</b>. A durable stream guards
 *       against this by persisting the bootstrap fingerprint and failing
 *       {@link TxFlowStream#start() start} typed
 *       ({@code TXSTREAM_BOOTSTRAP_CONFIG_DRIFT}) when the configuration drifts,
 *       before any split is submitted; a <em>non-durable</em> stream cannot
 *       persist and therefore <b>cannot detect the drift</b> — it will silently
 *       re-split.</li>
 *   <li>The lane-address <em>order</em> additionally defines the
 *       {@code partitionIndex → lane} mapping, so reordering the list remaps
 *       every item to a different lane even when N and the addresses are
 *       unchanged.</li>
 * </ul>
 *
 * <p><b>Operability of a mid-flight bootstrap crash.</b> The bootstrap is a
 * normal engine execution. If the process crashes after the split transaction
 * was submitted but before it confirmed, the next {@code start()} sees a
 * non-terminal bootstrap and fails its {@link BootstrapReport} — the operator
 * must reconcile the bootstrap execution (e.g. {@code engine.recover(...)}) to a
 * terminal state before the stream can open. Budget for that operability cost
 * when running partitioned streams.</p>
 *
 * <p>Instances are immutable; build them through {@link #fromAddress(String)}
 * or {@link #fromRef(String)}.</p>
 */
public final class PartitionedLanes {
    private final LaneFundingScope fundingSource;
    private final List<String> laneAddresses;
    private final Amount seedPerLane;
    private final boolean bootstrap;

    private PartitionedLanes(Builder builder) {
        this.fundingSource = builder.fundingSource;
        if (builder.laneAddresses.isEmpty()) {
            throw new IllegalArgumentException(
                    "partitioned() requires at least one lane address");
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String address : builder.laneAddresses) {
            if (address == null || address.isBlank()) {
                throw new IllegalArgumentException("a lane address cannot be null or blank");
            }
            if (!distinct.add(address)) {
                throw new IllegalArgumentException(
                        "lane addresses must be distinct; duplicate '" + address + "'");
            }
        }
        this.laneAddresses = List.copyOf(distinct);
        this.seedPerLane = Objects.requireNonNull(builder.seedPerLane, "seedPerLane");
        BigInteger quantity = seedPerLane.getQuantity();
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("seedPerLane quantity must be positive");
        }
        this.bootstrap = builder.bootstrap;
    }

    /**
     * Starts a partitioning configuration whose bootstrap draws from a funding
     * address.
     *
     * @param fundingAddress funding source address the fan-out spends from
     * @return builder
     */
    public static Builder fromAddress(String fundingAddress) {
        return new Builder(LaneFundingScope.address(fundingAddress));
    }

    /**
     * Starts a partitioning configuration whose bootstrap draws from a funding
     * reference URI ({@code from_ref}, e.g. {@code account://treasury}).
     *
     * @param fundingRef funding source reference the fan-out spends from
     * @return builder
     */
    public static Builder fromRef(String fundingRef) {
        return new Builder(LaneFundingScope.fundingRef(fundingRef));
    }

    /**
     * Returns the funding source the bootstrap fan-out draws from.
     *
     * @return funding source scope
     */
    public LaneFundingScope fundingSource() {
        return fundingSource;
    }

    /**
     * Returns the application-provided lane addresses, one per lane.
     *
     * @return immutable, de-duplicated lane addresses (N = size)
     */
    public List<String> laneAddresses() {
        return laneAddresses;
    }

    /**
     * Returns the number of lanes ({@code N}).
     *
     * @return lane count
     */
    public int laneCount() {
        return laneAddresses.size();
    }

    /**
     * Returns the seed amount each lane UTXO is funded with by the bootstrap.
     *
     * @return per-lane seed amount
     */
    public Amount seedPerLane() {
        return seedPerLane;
    }

    /**
     * Whether the one-time fan-out bootstrap runs on {@link TxFlowStream#start()}.
     *
     * @return {@code true} to split the funding source into the N lanes;
     *         {@code false} to assume the lanes are already funded
     */
    public boolean bootstrapEnabled() {
        return bootstrap;
    }

    /** Builder for {@link PartitionedLanes}. */
    public static final class Builder {
        private final LaneFundingScope fundingSource;
        private final List<String> laneAddresses = new ArrayList<>();
        private Amount seedPerLane;
        private boolean bootstrap = true;

        private Builder(LaneFundingScope fundingSource) {
            this.fundingSource = fundingSource;
        }

        /**
         * Adds one application-owned lane address.
         * <p>
         * The address and its <em>position</em> in the list are stability-critical
         * across restarts: they feed both the bootstrap fingerprint and the
         * {@code partitionIndex → lane} mapping. See the {@link PartitionedLanes}
         * class javadoc — changing or reordering lanes re-splits the wallet and
         * remaps items.
         *
         * @param address lane address (must be distinct from other lanes)
         * @return this builder
         */
        public Builder lane(String address) {
            this.laneAddresses.add(address);
            return this;
        }

        /**
         * Sets all lane addresses, replacing any previously added.
         * <p>
         * The list contents <em>and order</em> are stability-critical across
         * restarts (see the {@link PartitionedLanes} class javadoc): reordering
         * remaps every item's lane and, on a durable stream, drifts the bootstrap
         * fingerprint.
         *
         * @param addresses application-owned lane addresses; N = size, must be
         *        distinct and non-empty
         * @return this builder
         */
        public Builder laneAddresses(List<String> addresses) {
            this.laneAddresses.clear();
            if (addresses != null) {
                this.laneAddresses.addAll(addresses);
            }
            return this;
        }

        /**
         * Sets the seed amount each lane UTXO is funded with by the bootstrap.
         * <p>
         * The seed is part of the bootstrap fingerprint (see the
         * {@link PartitionedLanes} class javadoc): changing it between runs drifts
         * the fingerprint and, on a durable stream, would mint a new split — so it
         * too must be byte-stable across restarts.
         *
         * @param amount positive per-lane seed amount
         * @return this builder
         */
        public Builder seedPerLane(Amount amount) {
            this.seedPerLane = amount;
            return this;
        }

        /**
         * Opts the fan-out bootstrap in or out. Enabled by default.
         *
         * @param enabled {@code false} to assume the lanes are pre-funded
         * @return this builder
         */
        public Builder bootstrap(boolean enabled) {
            this.bootstrap = enabled;
            return this;
        }

        /**
         * Validates and builds the configuration.
         *
         * @return immutable partitioning configuration
         * @throws IllegalArgumentException when no lane addresses are supplied,
         *         a lane address is blank or duplicated, or the seed is not
         *         positive
         */
        public PartitionedLanes build() {
            return new PartitionedLanes(this);
        }
    }
}

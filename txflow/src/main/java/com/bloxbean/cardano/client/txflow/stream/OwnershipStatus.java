package com.bloxbean.cardano.client.txflow.stream;

/**
 * Point-in-time single-owner ownership state of a {@link TxFlowStream} instance
 * (ADR 0004 iteration 3d — multi-instance active/standby failover).
 *
 * <p>When ownership is opted in ({@link TxFlowStream.Builder#ownership}), exactly
 * one instance of a stream id is the {@link State#ACTIVE ACTIVE} owner that holds
 * a currently-valid epoch-fenced lease and dispatches; the others
 * {@link State#STANDBY STAND BY} and take over on the owner's crash or lease
 * expiry. An instance that loses its lease (a fenced renewal) steps back to
 * {@code STANDBY}; a closed/aborted instance is {@link State#RELEASED RELEASED}.
 * A stream with ownership disabled reports {@link State#DISABLED DISABLED} and
 * always dispatches (today's single-instance behaviour).</p>
 *
 * @param ownershipState current ownership state of this instance
 * @param ownerToken this instance's opaque owner token, or {@code null} when
 *        ownership is disabled
 * @param epoch the fencing epoch of the currently-held lease, or {@code 0} when
 *        this instance holds no lease (standby, released, or disabled)
 */
public record OwnershipStatus(State ownershipState, String ownerToken, long epoch) {
    /** Ownership state of a stream instance. */
    public enum State {
        /** This instance holds a currently-valid lease and dispatches. */
        ACTIVE,
        /** This instance is a hot standby waiting to take over; it does not dispatch. */
        STANDBY,
        /** This instance released ownership (closed/aborted). */
        RELEASED,
        /** Ownership is not configured; the instance always dispatches. */
        DISABLED
    }

    /**
     * @return {@code true} iff this instance is the ACTIVE owner
     */
    public boolean isActive() {
        return ownershipState == State.ACTIVE;
    }
}

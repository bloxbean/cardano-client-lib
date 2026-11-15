package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.UnitInterval;

import java.util.Arrays;

/**
 * The pool or the DRep voting thresholds of a parameter update as received: an array of unit intervals, each named by
 * its position in {@link Pool} or {@link DRep}.
 */
public final class RawVotingThresholds {
    /** The items of {@code pool_voting_thresholds}, in order. */
    public enum Pool {
        MOTION_NO_CONFIDENCE, COMMITTEE_NORMAL, COMMITTEE_NO_CONFIDENCE, HARD_FORK_INITIATION, SECURITY_GROUP
    }

    /** The items of {@code drep_voting_thresholds}, in order. */
    public enum DRep {
        MOTION_NO_CONFIDENCE, COMMITTEE_NORMAL, COMMITTEE_NO_CONFIDENCE, UPDATE_CONSTITUTION, HARD_FORK_INITIATION,
        NETWORK_GROUP, ECONOMIC_GROUP, TECHNICAL_GROUP, GOVERNANCE_GROUP, TREASURY_WITHDRAWAL
    }

    /** The {@code pool_voting_thresholds} rule. */
    static final RecordSpec POOL = thresholds("pool_voting_thresholds", Pool.values().length);
    /** The {@code drep_voting_thresholds} rule. */
    static final RecordSpec DREP = thresholds("drep_voting_thresholds", DRep.values().length);

    private static RecordSpec thresholds(String name, int count) {
        FieldType[] items = new FieldType[count];
        Arrays.fill(items, FieldType.UNIT_INTERVAL);
        return RecordSpec.positional(name, items);
    }

    private final RecordSpec spec;
    private final PositionalRecord record;
    private final boolean lenient;

    private RawVotingThresholds(RecordSpec spec, PositionalRecord record, boolean lenient) {
        this.spec = spec;
        this.record = record;
        this.lenient = lenient;
    }

    /**
     * @param span    pool voting thresholds as encoded
     * @param lenient whether to read the numbers of a rational as CCL's models always have: tags looked through, and
     *                null for an item that is no number
     * @return the view
     * @throws CborRuntimeException if they are not an array of five items
     */
    public static RawVotingThresholds pool(CborSpan span, boolean lenient) {
        return new RawVotingThresholds(POOL, PositionalRecord.of(POOL, span, true), lenient);
    }

    /**
     * @param span    DRep voting thresholds as encoded
     * @param lenient as for {@link #pool(CborSpan, boolean)}
     * @return the view
     * @throws CborRuntimeException if they are not an array of ten items
     */
    public static RawVotingThresholds drep(CborSpan span, boolean lenient) {
        return new RawVotingThresholds(DREP, PositionalRecord.of(DREP, span, true), lenient);
    }

    /**
     * @param threshold a pool threshold
     * @return its value
     * @throws IllegalStateException if these are the DRep thresholds
     */
    public UnitInterval get(Pool threshold) {
        return threshold(POOL, threshold.ordinal());
    }

    /**
     * @param threshold a DRep threshold
     * @return its value
     * @throws IllegalStateException if these are the pool thresholds
     */
    public UnitInterval get(DRep threshold) {
        return threshold(DREP, threshold.ordinal());
    }

    private UnitInterval threshold(RecordSpec expected, int nth) {
        if (spec != expected)
            throw new IllegalStateException("These are the " + spec.name());
        return RawItems.rational(record.value(FieldType.UNIT_INTERVAL, nth), lenient);
    }
}

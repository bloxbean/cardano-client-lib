package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The voting procedures of a transaction as received, {@code {+ voter => {+ gov_action_id => voting_procedure}}}.
 * {@link #of(CborSpan)} keeps every entry in encoded order, a repeated key included, for the policy to judge (ADR 0003
 * D5). {@link #lenient(CborSpan)} reads what CCL's models have always read (D7): one entry per key, keys equal as a
 * decoded map's are, with the first position and the last value, and the ids and procedures definite.
 */
public final class RawVotingProcedures {
    /** The votes of a voter, {@code {+ gov_action_id => voting_procedure}}. */
    static final RecordSpec VOTES = RecordSpec.entries("votes", FieldType.GOV_ACTION_ID,
            FieldSpec.of(0, FieldType.VOTING_PROCEDURE, false, Era.Conway, null));
    /** The {@code voting_procedures} rule. */
    static final RecordSpec RECORD = RecordSpec.entries("voting_procedures", FieldType.VOTER,
            FieldSpec.of(0, FieldType.NONEMPTY_MAP, false, Era.Conway, null, VOTES));

    /**
     * The votes of one voter.
     *
     * @param voter the voter
     * @param votes the actions voted on and the votes, in encoded order
     */
    public record Votes(RawVoter voter, List<Map.Entry<RawGovActionId, RawVotingProcedure>> votes) {
    }

    private final CborSpan span;
    private final boolean lenient;
    private final Era era;

    private RawVotingProcedures(CborSpan span, boolean lenient, Era era) {
        this.span = span;
        this.lenient = lenient;
        this.era = era;
    }

    /**
     * @param span voting procedures as encoded
     * @return the view; its items are read by {@link #voters()}
     */
    public static RawVotingProcedures of(CborSpan span) {
        return new RawVotingProcedures(span, false, null);
    }

    /**
     * @param span voting procedures as encoded
     * @return the view, read as CCL's models read it
     */
    public static RawVotingProcedures lenient(CborSpan span) {
        return lenient(span, null);
    }

    // As lenient(span), in an era: from Dijkstra the records in it may be indefinite.
    static RawVotingProcedures lenient(CborSpan span, Era era) {
        return new RawVotingProcedures(span, true, era);
    }

    /**
     * @return the voting procedures as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * @return each voter with its votes
     * @throws CborRuntimeException if a map is not a map, or a voter, an id or a procedure is not what its table says
     */
    public List<Votes> voters() {
        List<Map.Entry<CborSpan, CborSpan>> entries = entries(span, RECORD.name());
        List<Votes> voters = new ArrayList<>(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries) {
            RawVoter voter = RawVoter.of(entry.getKey());
            List<Map.Entry<CborSpan, CborSpan>> votes = entries(entry.getValue(), VOTES.name());
            List<Map.Entry<RawGovActionId, RawVotingProcedure>> read = new ArrayList<>(votes.size());
            for (Map.Entry<CborSpan, CborSpan> vote : votes) {
                CborSpan id = vote.getKey();
                CborSpan procedure = vote.getValue();
                read.add(new AbstractMap.SimpleImmutableEntry<>(
                        lenient ? RawGovActionId.lenient(id, era) : RawGovActionId.of(id),
                        lenient ? RawVotingProcedure.lenient(procedure, era) : RawVotingProcedure.of(procedure)));
            }
            voters.add(new Votes(voter, read));
        }
        return voters;
    }

    private List<Map.Entry<CborSpan, CborSpan>> entries(CborSpan map, String what) {
        return lenient ? RawItems.distinctKeys(map, what) : RawItems.map(map, what);
    }
}

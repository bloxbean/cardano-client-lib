package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

/**
 * A governance action id as received, {@code [transaction_id, gov_action_index : uint .size 2]}.
 *
 * @param span          the id, as encoded
 * @param transactionId the id of the transaction that proposed the action, of any length
 * @param index         the action's index in that transaction; its range is not checked
 */
public record RawGovActionId(CborSpan span, byte[] transactionId, int index) {
    /** The {@code gov_action_id} rule. */
    static final RecordSpec RECORD = RecordSpec.positional("gov_action_id", FieldType.HASH32, FieldType.UINT16);

    /**
     * @param span an id as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of a byte string and an unsigned integer that fits an int
     */
    public static RawGovActionId of(CborSpan span) {
        PositionalRecord record = PositionalRecord.of(RECORD, span, true);
        return new RawGovActionId(span, RawItems.bytes(record.value(FieldType.HASH32)),
                RawItems.smallUnsigned(record.value(FieldType.UINT16)));
    }

    /**
     * Reads an id as CCL's models always have: an array that a decoded tree marks as indefinite is rejected (ADR 0003
     * D7).
     *
     * @param span an id as encoded
     * @return the view
     * @throws CborRuntimeException if {@link #of(CborSpan)} rejects it, or it is indefinite
     */
    public static RawGovActionId lenient(CborSpan span) {
        return lenient(span, null);
    }

    // As lenient(span), in an era: from Dijkstra it may be indefinite.
    static RawGovActionId lenient(CborSpan span, Era era) {
        return of(RawItems.definite(span, RECORD.name(), era));
    }
}

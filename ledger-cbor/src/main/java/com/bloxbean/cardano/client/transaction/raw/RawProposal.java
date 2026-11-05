package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;

/**
 * A proposal procedure as received, {@code [deposit : coin, reward_account, gov_action, anchor]}.
 */
public final class RawProposal {
    /** The {@code proposal_procedure} rule. */
    static final RecordSpec RECORD = RecordSpec.positional("proposal_procedure", FieldType.COIN,
            FieldType.REWARD_ACCOUNT, FieldType.GOV_ACTION, FieldType.ANCHOR);

    private final CborSpan span;
    private final PositionalRecord record;
    private final Era era;
    private final boolean lenient;

    private RawProposal(CborSpan span, PositionalRecord record, Era era, boolean lenient) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        this.span = span;
        this.record = record;
        this.era = era;
        this.lenient = lenient;
    }

    /**
     * @param span a proposal as encoded
     * @param era  the era to read it in
     * @return the view
     * @throws CborRuntimeException     if it is not an array of four items
     * @throws IllegalArgumentException if the era is null
     */
    public static RawProposal of(CborSpan span, Era era) {
        return new RawProposal(span, PositionalRecord.of(RECORD, span, true), era, false);
    }

    /**
     * Reads a proposal as CCL's models always have (ADR 0003 D7): definite, items after the anchor ignored, a deposit
     * that is no number read as null, and the action read with {@link RawGovAction#lenient(CborSpan, Era)}.
     *
     * @param span a proposal as encoded
     * @param era  the era to read it in
     * @return the view
     * @throws CborRuntimeException     if it is not a definite array of at least four items
     * @throws IllegalArgumentException if the era is null
     */
    public static RawProposal lenient(CborSpan span, Era era) {
        return new RawProposal(span, PositionalRecord.of(RECORD, RawItems.definite(span, RECORD.name()), false), era,
                true);
    }

    /**
     * @return the proposal as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * @return the deposit; for a view read as the models read one, null if it is no number
     */
    public BigInteger deposit() {
        return RawItems.coin(record.value(FieldType.COIN), lenient);
    }

    /**
     * @return the reward account's bytes, of any length
     */
    public byte[] rewardAccount() {
        return RawItems.bytes(record.value(FieldType.REWARD_ACCOUNT));
    }

    public RawGovAction govAction() {
        CborSpan action = record.value(FieldType.GOV_ACTION);
        return lenient ? RawGovAction.lenient(action, era) : RawGovAction.of(action, era);
    }

    public RawAnchor anchor() {
        return RawAnchor.of(record.value(FieldType.ANCHOR));
    }
}

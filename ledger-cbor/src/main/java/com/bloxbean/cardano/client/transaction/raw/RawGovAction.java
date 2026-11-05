package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.UnitInterval;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A governance action as received, read by the {@link GovActionKind} table. Accessors read an item when they are
 * called, and throw {@link CborRuntimeException} if it is not what the table says or the kind has no such item. Tags on
 * an item are looked through, and any array may be indefinite. Hash lengths, ranges and empty maps are not checked.
 * <p>
 * {@link #of(CborSpan, Era)} keeps every entry of a map, in encoded order. {@link #lenient(CborSpan, Era)} reads what
 * CCL's models have always read (ADR 0003 D7): items after the last position are ignored, a map has one entry per key,
 * as a decoded map has it, numbers are read as {@link RawItems#integer(CborSpan, boolean)} reads them when lenient, and
 * ids, constitutions and parameter updates are read leniently too.
 */
public final class RawGovAction {
    /** The {@code committee_cold_credential => epoch} map of a committee update. */
    static final RecordSpec COMMITTEE_TERMS = RecordSpec.entries("committee_terms", FieldType.CREDENTIAL,
            FieldSpec.of(0, FieldType.UINT, false, Era.Conway, null));

    private final GovActionKind kind;
    private final CborSpan span;
    private final PositionalRecord record;
    private final Era era;
    private final boolean lenient;

    private RawGovAction(GovActionKind kind, CborSpan span, List<CborSpan> items, Era era, boolean lenient) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        this.kind = kind;
        this.span = span;
        this.record = PositionalRecord.of(kind.member().record(), span, items, !lenient);
        this.era = era;
        this.lenient = lenient;
    }

    /**
     * @param span an action as encoded
     * @param era  the era to read it in, which a parameter update is read in
     * @return the view
     * @throws CborRuntimeException     if it is not an array, its tag is unknown, or it does not have the items of its
     *                                  tag
     * @throws IllegalArgumentException if the era is null
     */
    public static RawGovAction of(CborSpan span, Era era) {
        return read(span, era, false);
    }

    /**
     * @param span an action as encoded
     * @param era  the era to read it in, which a parameter update is read in
     * @return the view, read as CCL's models read one
     * @throws CborRuntimeException     if it is not an array, its tag is unknown, or it has too few items
     * @throws IllegalArgumentException if the era is null
     */
    public static RawGovAction lenient(CborSpan span, Era era) {
        return read(span, era, true);
    }

    /**
     * Reads an action by position as {@link #lenient(CborSpan, Era)} does, as the given kind: its tag is not read.
     *
     * @param kind the kind to read it as
     * @param span an action as encoded
     * @param era  the era to read it in, which a parameter update is read in
     * @return the view
     * @throws CborRuntimeException     if it is not an array or has too few items for the kind
     * @throws IllegalArgumentException if the era is null
     */
    public static RawGovAction lenient(GovActionKind kind, CborSpan span, Era era) {
        return new RawGovAction(kind, span, RawItems.array(span, GovActionKind.RECORD.name()), era, true);
    }

    private static RawGovAction read(CborSpan span, Era era, boolean lenient) {
        List<CborSpan> items = RawItems.array(span, GovActionKind.RECORD.name());
        GovActionKind kind = GovActionKind.of(items, span)
                .orElseThrow(() -> RawTx.error("Unknown governance action", span));
        return new RawGovAction(kind, span, items, era, lenient);
    }

    public GovActionKind kind() {
        return kind;
    }

    /**
     * @return the action as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * @return the id of the previous action of the same purpose, or empty if it is null
     */
    public Optional<RawGovActionId> previousActionId() {
        return Optional.ofNullable(RawItems.nullable(record.value(FieldType.OPTIONAL_GOV_ACTION_ID),
                lenient ? RawGovActionId::lenient : RawGovActionId::of));
    }

    /**
     * @return the parameter update of a parameter change, in this view's era
     */
    public RawProtocolParamUpdate protocolParamUpdate() {
        CborSpan update = record.value(FieldType.PROTOCOL_PARAM_UPDATE);
        return lenient ? RawProtocolParamUpdate.lenient(update, era) : RawProtocolParamUpdate.of(update, era);
    }

    /**
     * @return the guardrails script hash, of any length, or empty if it is null
     */
    public Optional<byte[]> policyHash() {
        return Optional.ofNullable(RawItems.nullable(record.value(FieldType.OPTIONAL_HASH28), RawItems::bytes));
    }

    /**
     * @return the protocol version a hard fork initiates
     */
    public RawProtocolVersion protocolVersion() {
        CborSpan version = record.value(FieldType.PROTOCOL_VERSION);
        return lenient ? RawProtocolVersion.lenient(version) : RawProtocolVersion.of(version);
    }

    /**
     * @return the withdrawals of a treasury withdrawal, one per reward account, as
     * {@link RawTxBody#withdrawals()} has them; amounts read as the view reads numbers
     */
    public List<RawWithdrawal> withdrawals() {
        CborSpan withdrawals = record.value(FieldType.WITHDRAWALS);
        return lenient ? RawWithdrawal.tolerant(withdrawals) : RawWithdrawal.read(withdrawals);
    }

    /**
     * @return the cold credentials of the members a committee update removes, in encoded order
     */
    public List<RawCredential> membersToRemove() {
        List<CborSpan> items = RawItems.array(record.value(FieldType.CREDENTIAL_SET), FieldType.CREDENTIAL_SET.name());
        List<RawCredential> members = new ArrayList<>(items.size());
        for (CborSpan item : items)
            members.add(RawCredential.of(item));
        return members;
    }

    /**
     * @return the cold credentials of the members a committee update adds, with the epoch each term ends, its 64 bits
     */
    public List<Map.Entry<RawCredential, Long>> membersToAdd() {
        CborSpan terms = record.value(FieldType.COMMITTEE_TERMS);
        List<Map.Entry<CborSpan, CborSpan>> entries = lenient ? RawItems.distinctKeys(terms, COMMITTEE_TERMS.name())
                : RawItems.map(terms, COMMITTEE_TERMS.name());
        List<Map.Entry<RawCredential, Long>> members = new ArrayList<>(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries) {
            members.add(new AbstractMap.SimpleImmutableEntry<>(RawCredential.of(entry.getKey()),
                    RawItems.unsignedLong(entry.getValue())));
        }
        return members;
    }

    /**
     * @return the quorum of a committee update
     */
    public UnitInterval quorum() {
        return RawItems.rational(record.value(FieldType.UNIT_INTERVAL), lenient);
    }

    public RawConstitution constitution() {
        CborSpan constitution = record.value(FieldType.CONSTITUTION);
        return lenient ? RawConstitution.lenient(constitution) : RawConstitution.of(constitution);
    }
}

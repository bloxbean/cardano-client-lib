package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A protocol parameter update before Conway as received, {@code [proposed_protocol_parameter_updates, epoch]}: each
 * genesis key's proposed update, and the epoch it is for.
 * <p>
 * {@link #of(CborSpan, Era)} keeps every proposal in encoded order. {@link #lenient(CborSpan, Era)} reads what CCL's
 * model has always read (ADR 0003 D7): a definite array, one proposal per genesis hash as a decoded map has it, and
 * each update read leniently.
 */
public final class RawUpdate {
    /** The {@code proposed_protocol_parameter_updates} rule. */
    static final RecordSpec PROPOSALS = RecordSpec.entries("proposed_protocol_parameter_updates", FieldType.HASH28,
            FieldSpec.of(0, FieldType.PROTOCOL_PARAM_UPDATE, false, Era.Shelley, null));
    /** The {@code update} rule. */
    static final RecordSpec RECORD = RecordSpec.positional("update", FieldType.PROPOSED_PARAMETER_UPDATES,
            FieldType.UINT);

    private final CborSpan span;
    private final PositionalRecord record;
    private final Era era;
    private final boolean lenient;

    private RawUpdate(CborSpan span, Era era, boolean lenient) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        this.span = span;
        this.record = PositionalRecord.of(RECORD, span, true);
        this.era = era;
        this.lenient = lenient;
    }

    /**
     * @param span an update as encoded
     * @param era  the era to read its parameter updates in
     * @return the view
     * @throws CborRuntimeException     if it is not an array of two items
     * @throws IllegalArgumentException if the era is null
     */
    public static RawUpdate of(CborSpan span, Era era) {
        return new RawUpdate(span, era, false);
    }

    /**
     * @param span an update as encoded
     * @param era  the era to read its parameter updates in
     * @return the view, read as CCL's model reads one
     * @throws CborRuntimeException     if it is not a definite array of two items
     * @throws IllegalArgumentException if the era is null
     */
    public static RawUpdate lenient(CborSpan span, Era era) {
        return new RawUpdate(RawItems.definite(span, RECORD.name(), era), era, true);
    }

    /**
     * @return the update as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * @return each genesis hash, of any length, with its proposed update
     * @throws CborRuntimeException if the proposals are not a map, or a genesis hash not a byte string
     */
    public List<Map.Entry<byte[], RawProtocolParamUpdate>> proposals() {
        CborSpan proposals = record.value(FieldType.PROPOSED_PARAMETER_UPDATES);
        List<Map.Entry<CborSpan, CborSpan>> entries = lenient ? RawItems.distinctKeys(proposals, PROPOSALS.name())
                : RawItems.map(proposals, PROPOSALS.name());
        List<Map.Entry<byte[], RawProtocolParamUpdate>> read = new ArrayList<>(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries) {
            CborSpan update = entry.getValue();
            read.add(new AbstractMap.SimpleImmutableEntry<>(RawItems.bytes(entry.getKey()),
                    lenient ? RawProtocolParamUpdate.lenient(update, era) : RawProtocolParamUpdate.of(update, era)));
        }
        return read;
    }

    /**
     * @return the epoch, its 64 bits
     */
    public long epoch() {
        return RawItems.unsignedLong(record.value(FieldType.UINT));
    }
}

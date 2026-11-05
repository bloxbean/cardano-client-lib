package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;

import java.util.List;

import static com.bloxbean.cardano.client.transaction.raw.RawTx.error;

/**
 * The items of a positional record, found through its table by their type, so positions are written down only there:
 * the views ask for "the first credential" or "the second coin".
 */
final class PositionalRecord {
    private final CborSpan span;
    private final RecordSpec spec;
    private final List<CborSpan> items;

    private PositionalRecord(CborSpan span, RecordSpec spec, List<CborSpan> items, boolean exact) {
        this.span = span;
        this.spec = spec;
        this.items = items;
        checkArity(exact);
    }

    /**
     * @param exact false to ignore items after the last position; too few items are always rejected
     */
    static PositionalRecord of(RecordSpec spec, CborSpan span, boolean exact) {
        return new PositionalRecord(span, spec, RawItems.array(span, spec.name()), exact);
    }

    /** @return the member of the union that the first item names, or an error for an unknown tag */
    static PositionalRecord member(RecordSpec union, CborSpan span, boolean exact) {
        List<CborSpan> items = RawItems.array(span, union.name());
        if (items.isEmpty())
            throw error("A " + union.name() + " has a tag", span);
        return new PositionalRecord(span, memberSpec(union, RawItems.smallUnsigned(items.get(0)), span), items, exact);
    }

    /** @return the member with this tag; the first item is not read */
    static PositionalRecord member(RecordSpec union, long tag, CborSpan span, boolean exact) {
        return new PositionalRecord(span, memberSpec(union, tag, span), RawItems.array(span, union.name()), exact);
    }

    private static RecordSpec memberSpec(RecordSpec union, long tag, CborSpan span) {
        FieldSpec member = union.member(tag);
        if (member == null)
            throw error("Unknown " + union.name() + " tag " + tag, span);
        return member.record();
    }

    /** @return the tag of a member of a union */
    int tag() {
        return RawItems.smallUnsigned(items.get(0));
    }

    void checkArity(boolean exact) {
        int arity = spec.arity();
        if (items.size() < arity || (exact && items.size() > arity))
            throw error("A " + spec.name() + " has " + arity + " items, found " + items.size(), span);
    }

    /** @return the item of the record's first field of this type */
    CborSpan value(FieldType type) {
        return value(type, 0);
    }

    /** @return the item of the record's {@code nth} field of this type, from 0 */
    CborSpan value(FieldType type, int nth) {
        int seen = 0;
        for (FieldSpec field : spec.fields()) {
            if (field.type() == type && seen++ == nth)
                return items.get((int) field.key());
        }
        throw error("A " + spec.name() + " has no " + type, span);
    }
}

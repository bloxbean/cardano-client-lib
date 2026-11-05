package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;

import java.util.List;
import java.util.Optional;

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

    /** For a caller that has listed the record's items. */
    static PositionalRecord of(RecordSpec spec, CborSpan span, List<CborSpan> items, boolean exact) {
        return new PositionalRecord(span, spec, items, exact);
    }

    /** @return the member of the union that the first item names, or an error for an unknown tag */
    static PositionalRecord member(RecordSpec union, CborSpan span, boolean exact) {
        List<CborSpan> items = RawItems.array(span, union.name());
        if (items.isEmpty())
            throw error("A " + union.name() + " has a tag", span);
        return new PositionalRecord(span, memberSpec(union, RawItems.smallUnsigned(items.get(0)), span), items, exact);
    }

    private static RecordSpec memberSpec(RecordSpec union, long tag, CborSpan span) {
        FieldSpec member = union.member(tag);
        if (member == null)
            throw error("Unknown " + union.name() + " tag " + tag, span);
        return member.record();
    }

    List<CborSpan> items() {
        return items;
    }

    /** @return the tag of a member of a union */
    int tag() {
        return RawItems.smallUnsigned(items.get(0));
    }

    void checkArity(boolean exact) {
        int arity = spec.arity();
        if (items.size() < spec.requiredArity() || (exact && items.size() > arity))
            throw error("A " + spec.name() + " has " + arity + " items, found " + items.size(), span);
    }

    /**
     * @return the record holding a group's items: the array of this record's field of the group's type, or this record
     * itself if it has no such field, as a group may be written in line
     */
    PositionalRecord group(FieldType type) {
        for (FieldSpec field : spec.fields()) {
            if (field.type() == type)
                return of(field.record(), items.get((int) field.key()), true);
        }
        return this;
    }

    /** @return the item of the record's first field of this type, or empty if the record stops before it */
    Optional<CborSpan> optional(FieldType type) {
        for (FieldSpec field : spec.fields()) {
            if (field.type() == type)
                return field.key() < items.size() ? Optional.of(items.get((int) field.key())) : Optional.empty();
        }
        return Optional.empty();
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

package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The one walk of a ledger record, driven by its table. See {@link RecordSpec#walk} and {@link FormatPolicy}. */
final class RecordWalk {
    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_TAG = 6;

    private RecordWalk() {
    }

    static void walk(RecordSpec record, CborSpan container, Era era, FormatPolicy policy) {
        if (record.shape() == RecordSpec.Shape.BY_MAJOR_TYPE) {
            byMajorType(record, container, era, policy);
            return;
        }
        policy.enter(record, container, era);
        switch (record.shape()) {
            case KEYED:
                keyed(record, container, era, policy);
                break;
            case TAGGED:
                tagged(record, container, RawItems.array(container, record.name()), era, policy);
                break;
            default:
                positional(record, container, RawItems.array(container, record.name()), era, policy);
        }
    }

    private static void byMajorType(RecordSpec record, CborSpan item, Era era, FormatPolicy policy) {
        FieldSpec row = byMajorTypeRow(record, item, era);
        if (row == null)
            throw RawTx.error("A " + record.name() + " cannot be of major type " + item.majorType(), item);
        if (row.type().container())
            value(row, item, era, policy);
        else if (row.record() != null)
            walk(row.record(), item, era, policy);
        else
            policy.check(row.type(), item, era);
    }

    private static FieldSpec byMajorTypeRow(RecordSpec record, CborSpan item, Era era) {
        FieldSpec tagged = item.isTagged() ? record.field(MAJOR_TAG, era) : null;
        return tagged != null && tagged.record().tag() == item.tag() ? tagged : record.field(item.majorType(), era);
    }

    private static void keyed(RecordSpec record, CborSpan container, Era era, FormatPolicy policy) {
        Map<Object, Integer> seen = new HashMap<>();
        for (Map.Entry<CborSpan, CborSpan> entry : RawItems.map(container, record.name())) {
            CborSpan key = entry.getKey();
            CborSpan value = entry.getValue();
            BigInteger integer = integerKey(key);
            int occurrence = seen.merge(decodedKey(key, integer), 1, Integer::sum) - 1;
            // every entry of a map of like entries has the same field
            FieldSpec field = record.keyType() != null ? record.field(0, era) : field(record, integer, era);
            policy.entry(record, field, key, value, occurrence, era);
            if (record.keyType() != null)
                key(record.keyType(), key, era, policy);
            if (field != null)
                value(field, value, era, policy);
        }
        for (FieldSpec field : record.fields()) {
            if (field.requiredIn(era) && !seen.containsKey(BigInteger.valueOf(field.key())))
                policy.missing(record, field, container, era);
        }
    }

    private static void key(FieldType type, CborSpan key, Era era, FormatPolicy policy) {
        policy.check(type, key, era);
        if (type.record() != null)
            walk(type.record(), key, era, policy);
    }

    // Keys that decode alike are one key, however they are encoded.
    private static Object decodedKey(CborSpan key, BigInteger integer) {
        if (integer != null)
            return integer;
        if (key.majorType() == MAJOR_ARRAY && !key.isTagged())
            return arrayKey(key);
        return key.majorType() == 2 ? RawItems.identity(key) : ByteBuffer.wrap(key.bytes());
    }

    // An array of scalars, such as a redeemer's [tag, index]; an item that is no integer is compared by its bytes.
    private static List<Object> arrayKey(CborSpan key) {
        List<Object> items = new ArrayList<>();
        for (CborSpan item : key.items()) {
            BigInteger integer = integerKey(item);
            items.add(integer != null ? integer : ByteBuffer.wrap(item.bytes()));
        }
        return items;
    }

    /** For a caller that has listed the items of the union's value, which has told the policy it entered. */
    static void tagged(RecordSpec record, CborSpan container, List<CborSpan> items, Era era, FormatPolicy policy) {
        if (items.isEmpty())
            throw RawTx.error("A " + record.name() + " has a tag", container);
        CborSpan tag = items.get(0);
        FieldSpec field = field(record, integerKey(tag), era);
        policy.entry(record, field, tag, container, 0, era);
        if (field != null) {
            // the member's items are the union's, and are not listed again
            policy.check(field.type(), container, era);
            policy.enter(field.record(), container, era);
            positional(field.record(), container, items, era, policy);
        }
    }

    private static void positional(RecordSpec record, CborSpan container, List<CborSpan> items, Era era,
                                   FormatPolicy policy) {
        // a member of a tagged union starts after the tag, which the union has reported
        for (int position = record.firstPosition(); position < items.size(); position++) {
            CborSpan value = items.get(position);
            FieldSpec field = record.field(position, era);
            policy.entry(record, field, null, value, 0, era);
            if (field != null)
                value(field, value, era, policy);
        }
        for (FieldSpec field : record.fields()) {
            if (field.requiredIn(era) && field.key() >= items.size())
                policy.missing(record, field, container, era);
        }
    }

    private static void value(FieldSpec field, CborSpan value, Era era, FormatPolicy policy) {
        policy.check(field.type(), value, era);
        RecordSpec nested = field.record();
        if (nested == null || (field.type().nullable() && value.isNull()))
            return;
        if (field.type().collection()) {
            for (CborSpan item : RawItems.array(value, field.type().name()))
                walk(nested, item, era, policy);
        } else {
            walk(nested, value, era, policy);
        }
    }

    // The value of an untagged integer key, or null.
    private static BigInteger integerKey(CborSpan key) {
        return !key.isTagged() && key.majorType() <= 1 ? key.asBigInteger() : null;
    }

    private static FieldSpec field(RecordSpec record, BigInteger key, Era era) {
        boolean fits = key != null && key.signum() >= 0 && key.bitLength() < Long.SIZE;
        return fits ? record.field(key.longValue(), era) : null;
    }
}

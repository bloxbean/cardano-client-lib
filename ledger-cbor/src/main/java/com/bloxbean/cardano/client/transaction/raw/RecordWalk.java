package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;
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
        List<Map.Entry<CborSpan, CborSpan>> entries = RawItems.map(container, record.name());
        // The usual keys are counted without a map: unsigned integers below 64 in a mask, and a run of definite byte
        // strings from the first entry, each above the one before, which are distinct. Any other key, and a repeat,
        // is counted in a map, which then takes the run.
        long small = 0;
        Map<Object, Integer> seen = null;
        int ordered = 0;
        for (int i = 0; i < entries.size(); i++) {
            CborSpan key = entries.get(i).getKey();
            CborSpan value = entries.get(i).getValue();
            int smallKey = smallKey(key);
            BigInteger integer = smallKey >= 0 ? null : integerKey(key);
            int occurrence = 0;
            CborSpan previous = i == 0 ? null : entries.get(i - 1).getKey();
            if (smallKey < 0 && ordered == i && RawItems.byteStringAbove(key, previous)) {
                ordered++;
            } else if (smallKey < 0 || (small & 1L << smallKey) != 0) {
                if (seen == null) {
                    seen = new HashMap<>();
                    for (int j = 0; j < ordered; j++)
                        seen.put(decodedKey(entries.get(j).getKey(), null), 1);
                    ordered = -1;
                }
                Object decoded = smallKey >= 0 ? BigInteger.valueOf(smallKey) : decodedKey(key, integer);
                // the first entry of a small key is in the mask
                occurrence = seen.merge(decoded, 1, Integer::sum) - (smallKey >= 0 ? 0 : 1);
            }
            if (smallKey >= 0)
                small |= 1L << smallKey;
            // every entry of a map of like entries has the same field
            FieldSpec field = record.keyType() != null ? record.field(0, era)
                    : smallKey >= 0 ? record.field(smallKey, era) : field(record, integer, era);
            policy.entry(record, field, key, value, occurrence, era);
            if (record.keyType() != null)
                key(record.keyType(), key, era, policy);
            if (field != null)
                value(field, value, era, policy);
        }
        List<FieldSpec> fields = record.fields();
        for (int i = 0; i < fields.size(); i++) {
            FieldSpec field = fields.get(i);
            if (field.requiredIn(era) && !has(field.key(), small, seen))
                policy.missing(record, field, container, era);
        }
    }

    private static boolean has(long key, long small, Map<Object, Integer> seen) {
        if (key >= 0 && key < Long.SIZE)
            return (small & 1L << key) != 0;
        return seen != null && seen.containsKey(BigInteger.valueOf(key));
    }

    // An untagged unsigned integer below 64, however long its head, or -1.
    private static int smallKey(CborSpan key) {
        byte[] buffer = key.buffer();
        int initial = buffer[key.offset()] & 0xff;
        if (initial < 24)
            return initial;
        if (initial > 27)
            return -1;
        long value = 0;
        for (int i = 1; i <= 1 << (initial - 24); i++)
            value = value << 8 | buffer[key.offset() + i] & 0xff;
        return Long.compareUnsigned(value, Long.SIZE) < 0 ? (int) value : -1;
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
        return RawItems.identity(key);
    }

    // An array of scalars, such as a redeemer's [tag, index] or a voter's [tag, hash]; each item as a key of its own.
    private static List<Object> arrayKey(CborSpan key) {
        List<Object> items = new ArrayList<>();
        for (CborSpan item : key.items()) {
            BigInteger integer = integerKey(item);
            items.add(integer != null ? integer : RawItems.identity(item));
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
        List<FieldSpec> fields = record.fields();
        for (int i = 0; i < fields.size(); i++) {
            FieldSpec field = fields.get(i);
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
            // by index: an iterator of each list would be the walk's most frequent allocation
            List<CborSpan> items = RawItems.array(value, field.type().name());
            for (int i = 0; i < items.size(); i++)
                walk(nested, items.get(i), era, policy);
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

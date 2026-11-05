package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The one walk of a ledger record, driven by its table. See {@link RecordSpec#walk} and {@link FormatPolicy}. */
final class RecordWalk {

    private RecordWalk() {
    }

    static void walk(RecordSpec record, CborSpan container, Era era, FormatPolicy policy) {
        policy.enter(record, container, era);
        switch (record.shape()) {
            case KEYED:
                keyed(record, container, era, policy);
                break;
            case TAGGED:
                tagged(record, container, era, policy);
                break;
            default:
                positional(record, container, era, policy);
        }
    }

    private static void keyed(RecordSpec record, CborSpan container, Era era, FormatPolicy policy) {
        Map<Object, Integer> seen = new HashMap<>();
        for (Map.Entry<CborSpan, CborSpan> entry : RawItems.map(container, record.name())) {
            CborSpan key = entry.getKey();
            CborSpan value = entry.getValue();
            BigInteger integer = integerKey(key);
            int occurrence = seen.merge(integer != null ? integer : ByteBuffer.wrap(key.bytes()), 1, Integer::sum) - 1;
            FieldSpec field = field(record, integer, era);
            policy.entry(record, field, key, value, occurrence, era);
            if (field != null)
                value(field, value, era, policy);
        }
        for (FieldSpec field : record.fields()) {
            if (field.requiredIn(era) && !seen.containsKey(BigInteger.valueOf(field.key())))
                policy.missing(record, field, container, era);
        }
    }

    private static void tagged(RecordSpec record, CborSpan container, Era era, FormatPolicy policy) {
        List<CborSpan> items = RawItems.array(container, record.name());
        if (items.isEmpty())
            throw RawTx.error("A " + record.name() + " has a tag", container);
        CborSpan tag = items.get(0);
        FieldSpec field = field(record, integerKey(tag), era);
        policy.entry(record, field, tag, container, 0, era);
        if (field != null)
            value(field, container, era, policy);
    }

    private static void positional(RecordSpec record, CborSpan container, Era era, FormatPolicy policy) {
        List<CborSpan> items = RawItems.array(container, record.name());
        // a member of a tagged union starts after the tag, which the union has reported
        int first = record.fields().isEmpty() ? 1 : (int) record.fields().get(0).key();
        for (int position = first; position < items.size(); position++) {
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
        return key.tag() == -1 && key.majorType() <= 1 ? key.asBigInteger() : null;
    }

    private static FieldSpec field(RecordSpec record, BigInteger key, Era era) {
        boolean fits = key != null && key.signum() >= 0 && key.bitLength() < Long.SIZE;
        return fits ? record.field(key.longValue(), era) : null;
    }
}

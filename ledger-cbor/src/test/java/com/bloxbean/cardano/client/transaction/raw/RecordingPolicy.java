package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.ArrayList;
import java.util.List;

/** Writes down what a walk reports, one line per call, with spans in hex. */
final class RecordingPolicy implements FormatPolicy {
    final List<String> events = new ArrayList<>();

    @Override
    public void enter(RecordSpec record, CborSpan container, Era era) {
        events.add("enter " + record + " " + hex(container));
    }

    @Override
    public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence, Era era) {
        events.add("entry " + record + " " + (field != null ? field.key() + ":" + field.type() : "null") + " key="
                + (key != null ? hex(key) : "null") + " value=" + hex(value) + " occurrence=" + occurrence);
    }

    @Override
    public void missing(RecordSpec record, FieldSpec field, CborSpan container, Era era) {
        events.add("missing " + record + " " + field.key() + ":" + field.type());
    }

    @Override
    public void check(FieldType type, CborSpan value, Era era) {
        events.add("check " + type + " " + hex(value));
    }

    static String hex(CborSpan span) {
        return HexUtil.encodeHexString(span.bytes());
    }
}

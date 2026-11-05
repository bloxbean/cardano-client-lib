package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;

/**
 * A DRep as received, {@code [0, addr_keyhash // 1, script_hash // 2 // 3]}.
 */
public final class RawDRep {
    /** The members of the {@code drep} rule. */
    public enum Kind {
        KEY_HASH(0, FieldType.HASH28),
        SCRIPT_HASH(1, FieldType.HASH28),
        ABSTAIN(2),
        NO_CONFIDENCE(3);

        private final FieldSpec member;

        Kind(int tag, FieldType... items) {
            this.member = FieldSpec.member(tag, Era.Shelley, null, items);
        }

        private static Kind of(int tag) {
            for (Kind kind : values()) {
                if (kind.member.key() == tag)
                    return kind;
            }
            return null;
        }
    }

    static final RecordSpec RECORD;

    static {
        List<FieldSpec> members = new ArrayList<>();
        for (Kind kind : Kind.values())
            members.add(kind.member);
        RECORD = RecordSpec.tagged("drep", members);
    }

    private final Kind kind;
    private final PositionalRecord record;

    private RawDRep(Kind kind, PositionalRecord record) {
        this.kind = kind;
        this.record = record;
    }

    /**
     * @param span a DRep as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of a known tag and the
     *                              items of that tag
     */
    public static RawDRep of(CborSpan span) {
        return read(span, true);
    }

    /**
     * Reads a DRep as CCL's models always have: items after the last position are ignored (ADR 0003 D7).
     *
     * @param span a DRep as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of a known tag and at
     *                              least the items of that tag
     */
    public static RawDRep lenient(CborSpan span) {
        return read(span, false);
    }

    private static RawDRep read(CborSpan span, boolean exact) {
        PositionalRecord record = PositionalRecord.member(RECORD, span, exact);
        return new RawDRep(Kind.of(record.tag()), record);
    }

    public Kind kind() {
        return kind;
    }

    /**
     * @return the hash of a {@link Kind#KEY_HASH} or {@link Kind#SCRIPT_HASH}, of any length
     * @throws CborRuntimeException for the other kinds
     */
    public byte[] hash() {
        return RawItems.bytes(record.value(FieldType.HASH28));
    }
}

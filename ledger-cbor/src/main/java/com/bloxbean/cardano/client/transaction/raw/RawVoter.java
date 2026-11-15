package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;

/**
 * A voter as received: a committee member's hot key or script hash, a DRep's key or script hash, or a pool's key hash
 * ({@code [0, addr_keyhash // 1, script_hash // 2, addr_keyhash // 3, script_hash // 4, addr_keyhash]}).
 */
public final class RawVoter {
    /** The members of the {@code voter} rule. */
    public enum Kind {
        COMMITTEE_HOT_KEY_HASH(0, FieldType.HASH28),
        COMMITTEE_HOT_SCRIPT_HASH(1, FieldType.HASH28),
        DREP_KEY_HASH(2, FieldType.HASH28),
        DREP_SCRIPT_HASH(3, FieldType.HASH28),
        STAKING_POOL_KEY_HASH(4, FieldType.HASH28);

        private final FieldSpec member;

        Kind(int tag, FieldType... items) {
            this.member = FieldSpec.member(tag, Era.Conway, null, items);
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
        RECORD = RecordSpec.tagged("voter", members);
    }

    private final Kind kind;
    private final PositionalRecord record;

    private RawVoter(Kind kind, PositionalRecord record) {
        this.kind = kind;
        this.record = record;
    }

    /**
     * @param span a voter as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of a known tag and its hash
     */
    public static RawVoter of(CborSpan span) {
        PositionalRecord record = PositionalRecord.member(RECORD, span, true);
        return new RawVoter(Kind.of(record.tag()), record);
    }

    public Kind kind() {
        return kind;
    }

    /**
     * @return the key or script hash, of any length
     */
    public byte[] hash() {
        return RawItems.bytes(record.value(FieldType.HASH28));
    }
}

package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.List;
import java.util.Optional;

/**
 * A nonce as received, {@code [0 // 1, hash32]}: the neutral nonce, or a hash.
 */
public final class RawNonce {
    private static final FieldSpec NEUTRAL = FieldSpec.member(0, Era.Shelley, null);
    private static final FieldSpec HASH = FieldSpec.member(1, Era.Shelley, null, FieldType.HASH32);
    /** The {@code nonce} rule. */
    static final RecordSpec RECORD = RecordSpec.tagged("nonce", List.of(NEUTRAL, HASH));

    private final PositionalRecord record;

    private RawNonce(PositionalRecord record) {
        this.record = record;
    }

    /**
     * @param span a nonce as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of a known tag and the items of that tag
     */
    public static RawNonce of(CborSpan span) {
        return new RawNonce(PositionalRecord.member(RECORD, span, true));
    }

    /**
     * Reads a nonce as CCL's models always have: items after the last position are ignored (ADR 0003 D7).
     *
     * @param span a nonce as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of a known tag and at least the items of that tag
     */
    public static RawNonce lenient(CborSpan span) {
        return new RawNonce(PositionalRecord.member(RECORD, span, false));
    }

    /**
     * @return the hash, of any length, or empty for the neutral nonce
     */
    public Optional<byte[]> hash() {
        return record.tag() == NEUTRAL.key() ? Optional.empty()
                : Optional.of(RawItems.bytes(record.value(FieldType.HASH32)));
    }
}

package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.List;

/**
 * A credential as received, {@code [0, addr_keyhash // 1, script_hash]}: a stake, DRep or committee credential.
 */
public final class RawCredential {
    private static final FieldSpec KEY_HASH = FieldSpec.member(0, Era.Shelley, null, FieldType.HASH28);
    private static final FieldSpec SCRIPT_HASH = FieldSpec.member(1, Era.Shelley, null, FieldType.HASH28);
    static final RecordSpec RECORD = RecordSpec.tagged("credential", List.of(KEY_HASH, SCRIPT_HASH));

    private final PositionalRecord record;
    private final boolean script;

    private RawCredential(PositionalRecord record, boolean script) {
        this.record = record;
        this.script = script;
    }

    /**
     * @param span a credential as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of the two items of a
     *                              known tag
     */
    public static RawCredential of(CborSpan span) {
        return new RawCredential(PositionalRecord.member(RECORD, span, true),
                PositionalRecord.tag(RECORD, span) == SCRIPT_HASH.key());
    }

    /**
     * @return whether this is a script hash and not a key hash
     */
    public boolean isScript() {
        return script;
    }

    /**
     * @return the hash, of any length
     */
    public byte[] hash() {
        return RawItems.bytes(record.value(FieldType.HASH28));
    }
}

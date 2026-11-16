package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.List;

/**
 * A credential as received, {@code [0, addr_keyhash // 1, script_hash]}: a stake, DRep or committee credential, or a
 * Dijkstra guard.
 */
public final class RawCredential {
    private static final int MAJOR_BYTES = 2;
    private static final int MAJOR_ARRAY = 4;
    private static final FieldSpec KEY_HASH = FieldSpec.member(0, Era.Shelley, null, FieldType.HASH28);
    private static final FieldSpec SCRIPT_HASH = FieldSpec.member(1, Era.Shelley, null, FieldType.HASH28);
    static final RecordSpec RECORD = RecordSpec.tagged("credential", List.of(KEY_HASH, SCRIPT_HASH));
    /**
     * An item of Dijkstra's {@code guards}, {@code nonempty_set<addr_keyhash> / nonempty_oset<credential>}: a key hash
     * or a credential. That every item has the first one's form is policy.
     */
    static final RecordSpec GUARD = RecordSpec.byMajorType("guard",
            FieldSpec.of(MAJOR_BYTES, FieldType.HASH28, false, Era.Dijkstra, null),
            FieldSpec.of(MAJOR_ARRAY, FieldType.CREDENTIAL, false, Era.Dijkstra, null));

    private final CborSpan hash;
    private final boolean script;
    private final boolean keyHashOnly;

    private RawCredential(CborSpan hash, boolean script, boolean keyHashOnly) {
        this.hash = hash;
        this.script = script;
        this.keyHashOnly = keyHashOnly;
    }

    /**
     * @param span a credential as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of the two items of a
     *                              known tag
     */
    public static RawCredential of(CborSpan span) {
        PositionalRecord record = PositionalRecord.member(RECORD, span, true);
        return new RawCredential(record.value(FieldType.HASH28), record.tag() == SCRIPT_HASH.key(), false);
    }

    // A guard: a key hash is a key credential, as the ledger reads it (Dijkstra TxBody.hs, decodeGuards).
    static RawCredential guard(CborSpan item) {
        return RawItems.content(item).majorType() == MAJOR_BYTES ? new RawCredential(item, false, true) : of(item);
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return whether this is a guard written as a bare key hash, the form of Conway's required signers, and not as a
     * credential
     */
    public boolean isKeyHashOnly() {
        return keyHashOnly;
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
        return RawItems.bytes(hash);
    }
}

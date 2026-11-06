package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

/**
 * The announcement of a Leios endorser block in a 12-item Dijkstra header,
 * {@code [eb_hash : hash32, eb_size : uint .size 4]}: the hash and the size in bytes of its {@link RawEbReferences}.
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 *
 * @param hash the hash of the endorser block's references, of any length
 * @param size the size of the references in bytes, its 64 bits
 */
public record RawEbReferencesAnnouncement(byte[] hash, long size) {
    static final RecordSpec RECORD = RecordSpec.positional("eb_references_announcement", FieldType.HASH32,
            FieldType.UINT32);

    /**
     * @throws CborRuntimeException if it is not an array of a byte string and an unsigned integer
     */
    static RawEbReferencesAnnouncement of(CborSpan span) {
        PositionalRecord record = PositionalRecord.of(RECORD, span, true);
        return new RawEbReferencesAnnouncement(RawItems.bytes(record.value(FieldType.HASH32)),
                RawItems.integer(record.value(FieldType.UINT32), false).longValue());
    }
}

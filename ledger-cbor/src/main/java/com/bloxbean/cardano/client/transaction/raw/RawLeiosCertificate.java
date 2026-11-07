package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

/**
 * The Leios certificate of a Dijkstra block body, {@code [signers : bytes .size (0 .. 8192), signature : bytes .size
 * 48]}: a bit field of the committee members who signed, and their aggregate signature. Lengths are not checked
 * (ADR 0003 D5).
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 *
 * @param signers   the signers' bit field
 * @param signature the signature
 */
public record RawLeiosCertificate(byte[] signers, byte[] signature) {
    static final RecordSpec RECORD = RecordSpec.positional("leios_certificate", FieldType.BYTES, FieldType.BYTES);

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param span a Leios certificate as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of two byte strings
     */
    public static RawLeiosCertificate of(CborSpan span) {
        PositionalRecord record = PositionalRecord.of(RECORD, span, true);
        return new RawLeiosCertificate(RawItems.bytes(record.value(FieldType.BYTES)),
                RawItems.bytes(record.value(FieldType.BYTES, 1)));
    }
}

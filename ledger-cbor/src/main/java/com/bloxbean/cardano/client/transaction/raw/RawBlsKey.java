package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

/**
 * The BLS key of a Dijkstra pool registration, {@code [bls_pubkey : bytes .size 96, bls_possession_proof : bytes .size
 * 48]}. Lengths are not checked (ADR 0003 D5).
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 *
 * @param publicKey         the public key
 * @param proofOfPossession the proof of possession of its private key
 */
public record RawBlsKey(byte[] publicKey, byte[] proofOfPossession) {
    static final RecordSpec RECORD = RecordSpec.positional("bls_key", FieldType.BYTES, FieldType.BYTES);

    /**
     * @throws CborRuntimeException if it is not an array of two byte strings
     */
    static RawBlsKey of(CborSpan span) {
        PositionalRecord record = PositionalRecord.of(RECORD, span, true);
        return new RawBlsKey(RawItems.bytes(record.value(FieldType.BYTES)),
                RawItems.bytes(record.value(FieldType.BYTES, 1)));
    }
}

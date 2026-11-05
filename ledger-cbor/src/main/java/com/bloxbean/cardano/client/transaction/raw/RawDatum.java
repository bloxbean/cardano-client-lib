package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.spec.Era;

import java.util.List;

/**
 * Plutus data as received: a witness datum, or the payload of an inline datum.
 *
 * @param span the data, as encoded
 */
public record RawDatum(CborSpan span) {
    static final FieldSpec HASH_OPTION = FieldSpec.member(0, Era.Babbage, null, FieldType.HASH32);
    static final FieldSpec INLINE_OPTION = FieldSpec.member(1, Era.Babbage, null, FieldType.DATA);
    /** The {@code datum_option} rule, a tagged union. */
    static final RecordSpec OPTION = RecordSpec.tagged("datum_option", List.of(HASH_OPTION, INLINE_OPTION));

    /**
     * @return the datum hash, {@code blake2b256} of the data as encoded
     */
    public byte[] hash() {
        return Blake2bUtil.blake2bHash256(span.bytes());
    }
}

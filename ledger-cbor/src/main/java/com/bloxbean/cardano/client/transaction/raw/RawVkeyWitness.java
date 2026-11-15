package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

/**
 * A vkey witness as received, {@code [vkey, signature]}. Tags on an item are looked through and lengths are not checked
 * (ADR 0003 D5).
 *
 * @param vkey      the verification key's bytes
 * @param signature the signature's bytes
 */
public record RawVkeyWitness(byte[] vkey, byte[] signature) {
    /** The {@code vkeywitness} rule. */
    static final RecordSpec RECORD = RecordSpec.positional("vkeywitness", FieldType.VKEY, FieldType.SIGNATURE);

    /**
     * @param span a witness as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of two byte strings
     */
    public static RawVkeyWitness of(CborSpan span) {
        PositionalRecord record = PositionalRecord.of(RECORD, span, true);
        return new RawVkeyWitness(RawItems.bytes(record.value(FieldType.VKEY)),
                RawItems.bytes(record.value(FieldType.SIGNATURE)));
    }
}

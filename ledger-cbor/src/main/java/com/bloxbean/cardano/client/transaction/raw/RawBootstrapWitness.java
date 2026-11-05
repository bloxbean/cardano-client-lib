package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

/**
 * A bootstrap witness as received, {@code [public_key, signature, chain_code, attributes]}. Tags on an item are looked
 * through and lengths are not checked (ADR 0003 D5).
 *
 * @param publicKey  the verification key's bytes
 * @param signature  the signature's bytes
 * @param chainCode  the chain code's bytes
 * @param attributes the attributes' bytes
 */
public record RawBootstrapWitness(byte[] publicKey, byte[] signature, byte[] chainCode, byte[] attributes) {
    /** The {@code bootstrap_witness} rule. */
    static final RecordSpec RECORD = RecordSpec.positional("bootstrap_witness", FieldType.VKEY, FieldType.SIGNATURE,
            FieldType.BYTES, FieldType.BYTES);

    /**
     * @param span a witness as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of four byte strings
     */
    public static RawBootstrapWitness of(CborSpan span) {
        PositionalRecord record = PositionalRecord.of(RECORD, span, true);
        return new RawBootstrapWitness(RawItems.bytes(record.value(FieldType.VKEY)),
                RawItems.bytes(record.value(FieldType.SIGNATURE)), RawItems.bytes(record.value(FieldType.BYTES, 0)),
                RawItems.bytes(record.value(FieldType.BYTES, 1)));
    }
}

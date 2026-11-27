package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;

/**
 * A VRF result as received, {@code [bytes, bytes .size 80]}: the output and its proof.
 */
public final class RawVrfCert {
    static final RecordSpec RECORD = RecordSpec.positional("vrf_cert", FieldType.BYTES, FieldType.VRF_PROOF);

    private final PositionalRecord record;

    private RawVrfCert(PositionalRecord record) {
        this.record = record;
    }

    static RawVrfCert of(CborSpan span) {
        return new RawVrfCert(PositionalRecord.of(RECORD, span, true));
    }

    public byte[] output() {
        return RawItems.bytes(record.value(FieldType.BYTES));
    }

    /**
     * @return the proof, of any length
     */
    public byte[] proof() {
        return RawItems.bytes(record.value(FieldType.VRF_PROOF));
    }
}

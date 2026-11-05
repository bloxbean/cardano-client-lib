package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.math.BigInteger;

/**
 * A protocol version as received, {@code [major_protocol_version, uint .size 4]}, of a hard fork initiation or of a
 * parameter update before Conway. Ranges are not checked.
 */
public final class RawProtocolVersion {
    private final PositionalRecord record;
    private final boolean lenient;

    private RawProtocolVersion(PositionalRecord record, boolean lenient) {
        this.record = record;
        this.lenient = lenient;
    }

    /**
     * @param span a protocol version as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of two items
     */
    public static RawProtocolVersion of(CborSpan span) {
        return new RawProtocolVersion(PositionalRecord.of(RawHeader.PROTOCOL_VERSION, span, true), false);
    }

    /**
     * Reads a protocol version as CCL's models always have: the minor version is read as
     * {@link RawItems#integer(CborSpan, boolean)} reads a number when lenient (ADR 0003 D7).
     *
     * @param span a protocol version as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of two items
     */
    public static RawProtocolVersion lenient(CborSpan span) {
        return new RawProtocolVersion(PositionalRecord.of(RawHeader.PROTOCOL_VERSION, span, true), true);
    }

    /**
     * @return the major version, which must fit an int
     */
    public int major() {
        return RawItems.smallUnsigned(record.value(FieldType.MAJOR_PROTOCOL_VERSION));
    }

    /**
     * @return the minor version; for a view read as the models read one, any integer or bignum, or null if it is no
     * number
     */
    public BigInteger minor() {
        CborSpan minor = record.value(FieldType.MINOR_PROTOCOL_VERSION);
        return lenient ? RawItems.integer(minor, true) : RawItems.unsigned(minor);
    }
}

package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.raw.RawProtocolVersion;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.*;

import java.math.BigInteger;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class ProtocolVersion {
    private int major;
    // cardano-ledger: a Natural until protocol version 12, then a Word32
    private long minor;

    public Array serialize() {
        Array array = new Array();
        array.add(new UnsignedInteger(major));
        array.add(new UnsignedInteger(minor));

        return array;
    }

    public static ProtocolVersion deserialize(Array array) {
        return from(RawProtocolVersion.lenient(RawModels.span(array)));
    }

    /**
     * @param raw a protocol version as received
     * @return the model
     * @throws CborRuntimeException if the minor version is no number
     * @throws ArithmeticException  if the minor version does not fit a long
     */
    public static ProtocolVersion from(RawProtocolVersion raw) {
        int major = raw.major();
        BigInteger minor = raw.minor();
        if (minor == null)
            throw new CborRuntimeException("Expected a minor protocol version");
        return new ProtocolVersion(major, minor.longValueExact());
    }
}

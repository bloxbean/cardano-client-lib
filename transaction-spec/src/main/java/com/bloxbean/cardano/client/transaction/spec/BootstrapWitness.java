package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.raw.RawBootstrapWitness;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BootstrapWitness {
    private byte[] publicKey;
    private byte[] signature;
    public byte[] chainCode;
    public byte[] attributes;

    public Array serialize() {
        Array array = new Array();
        array.add(new ByteString(publicKey));
        array.add(new ByteString(signature));
        array.add(new ByteString(chainCode));
        array.add(new ByteString(attributes));

        return array;
    }

    public static BootstrapWitness deserialize(Array bootstrapWitnessArrayDI) throws CborDeserializationException {
        try {
            return from(RawBootstrapWitness.of(CborSerializationUtil.span(bootstrapWitnessArrayDI)));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("BootstrapWitness deserialization failed", e);
        }
    }

    static BootstrapWitness from(RawBootstrapWitness raw) {
        return new BootstrapWitness(raw.publicKey(), raw.signature(), raw.chainCode(), raw.attributes());
    }

}

package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.raw.RawVkeyWitness;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VkeyWitness {
    private byte[] vkey;
    private byte[] signature;

    public Array serialize() {
        Array array = new Array();
        array.add(new ByteString(vkey));
        array.add(new ByteString(signature));

        return array;
    }

    public static VkeyWitness deserialize(Array vkWitness) throws CborDeserializationException {
        try {
            return from(RawVkeyWitness.of(RawModels.span(vkWitness)));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("VkeyWitness deserialization failed", e);
        }
    }

    static VkeyWitness from(RawVkeyWitness raw) {
        return new VkeyWitness(raw.vkey(), raw.signature());
    }

    @Override
    public String toString() {
        if (vkey != null && signature != null) {
            return "VkeyWitness{" +
                    "vkey=" + HexUtil.encodeHexString(vkey) +
                    ", signature=" + HexUtil.encodeHexString(signature) +
                    '}';
        } else {
            return super.toString();
        }
    }
}

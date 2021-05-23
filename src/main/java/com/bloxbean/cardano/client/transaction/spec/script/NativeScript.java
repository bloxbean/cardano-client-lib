package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static com.bloxbean.cardano.client.crypto.KeyGenUtil.blake2bHash224;

public interface NativeScript {

    public DataItem serializeAsDataItem() throws CborException;

    default public byte[] serialize() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CborBuilder cborBuilder = new CborBuilder();
        Array array = (Array) serializeAsDataItem();
        cborBuilder.add(array);
        new CborEncoder(baos).nonCanonical().encode(cborBuilder.build());
        byte[] encodedBytes = baos.toByteArray();
        return encodedBytes;
    }

    @JsonIgnore
    default public String getPolicyId() throws CborException {
        byte[] first = new byte[]{00};
        byte[] serializedBytes = this.serialize();
        byte[] finalBytes = ByteBuffer.allocate(first.length + serializedBytes.length)
                .put(first)
                .put(serializedBytes)
                .array();

        return Hex.toHexString(blake2bHash224(finalBytes));
    }
}

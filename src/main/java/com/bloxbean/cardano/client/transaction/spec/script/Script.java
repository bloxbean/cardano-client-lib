package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash224;

public interface Script {

    default byte[] serialize() throws CborSerializationException {
        byte[] first = getScriptTypeBytes();
        byte[] serializedBytes = serializeScriptBody();
        byte[] finalBytes = ByteBuffer.allocate(first.length + serializedBytes.length)
                .put(first)
                .put(serializedBytes)
                .array();

        return finalBytes;
    }

    @JsonIgnore
    default byte[] getScriptHash() throws CborSerializationException {
        return blake2bHash224(serialize());
    }

    @JsonIgnore
    default String getPolicyId() throws CborSerializationException {
        return Hex.toHexString(getScriptHash());
    }

    DataItem serializeAsDataItem() throws CborSerializationException;
    byte[] serializeScriptBody() throws CborSerializationException;
    @JsonIgnore
    byte[] getScriptTypeBytes();
}

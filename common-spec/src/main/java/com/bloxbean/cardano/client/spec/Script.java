package com.bloxbean.cardano.client.spec;

import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.ByteBuffer;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash224;

public interface Script {

    /**
     * Get serialized bytes for the script. This is used in script hash calculation.
     * @return
     * @throws CborSerializationException
     */
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
        return HexUtil.encodeHexString(getScriptHash());
    }

    /**
     * Get serialized bytes for script reference. This is used in TransactionOutput's script_ref
     * @return byte[]
     * @throws CborSerializationException
     */
    byte[] scriptRefBytes() throws CborSerializationException;

    DataItem serializeAsDataItem() throws CborSerializationException;
    byte[] serializeScriptBody() throws CborSerializationException;
    @JsonIgnore
    byte[] getScriptTypeBytes();
    @JsonIgnore
    int getScriptType();
}

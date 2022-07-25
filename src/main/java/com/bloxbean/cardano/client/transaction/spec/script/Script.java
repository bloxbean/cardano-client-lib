package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bouncycastle.util.encoders.Hex;

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

    /**
     * Get serialized bytes for script reference. This is used in TransactionOutput's script_ref
     * @return
     * @throws CborSerializationException
     */
    default byte[] scriptRefBytes() throws CborSerializationException {
        int type = getScriptType();
        byte[] serializedBytes = serializeScriptBody();

        Array array = new Array();
        array.add(new UnsignedInteger(type));
        array.add(new ByteString(serializedBytes));

        try {
            return CborSerializationUtil.serialize(array);
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }
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
    @JsonIgnore
    int getScriptType();
}

package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.TransactionDeserializationException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

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

    static NativeScript deserialize(Array nativeScriptArray) throws TransactionDeserializationException {
        List<DataItem> dataItemList = nativeScriptArray.getDataItems();
        if(dataItemList == null || dataItemList.size() == 0) {
            throw new TransactionDeserializationException("NativeScript deserialization failed. Invalid no of DataItem");
        }

        int type = ((UnsignedInteger)dataItemList.get(0)).getValue().intValue();
        if(type == 0) {
            return ScriptPubkey.deserialize(nativeScriptArray);
        } else if(type == 1) {
            return ScriptAll.deserialize(nativeScriptArray);
        } else if(type == 2) {
            return ScriptAny.deserialize(nativeScriptArray);
        } else if(type == 3) {
            return ScriptAtLeast.deserialize(nativeScriptArray);
        } else if(type == 4) {
            return RequireTimeAfter.deserialize(nativeScriptArray);
        } else if(type ==5) {
            return RequireTimeBefore.deserialize(nativeScriptArray);
        } else {
            return null;
        }
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

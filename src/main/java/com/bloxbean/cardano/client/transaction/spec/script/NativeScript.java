package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.transaction.util.NativeScriptDeserializer;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonDeserialize(using = NativeScriptDeserializer.class)
public interface NativeScript extends Script {

    static NativeScript deserialize(Array nativeScriptArray) throws CborDeserializationException {
        List<DataItem> dataItemList = nativeScriptArray.getDataItems();
        if (dataItemList == null || dataItemList.size() == 0) {
            throw new CborDeserializationException("NativeScript deserialization failed. Invalid no of DataItem");
        }

        int type = ((UnsignedInteger) dataItemList.get(0)).getValue().intValue();
        if (type == 0) {
            return ScriptPubkey.deserialize(nativeScriptArray);
        } else if (type == 1) {
            return ScriptAll.deserialize(nativeScriptArray);
        } else if (type == 2) {
            return ScriptAny.deserialize(nativeScriptArray);
        } else if (type == 3) {
            return ScriptAtLeast.deserialize(nativeScriptArray);
        } else if (type == 4) {
            return RequireTimeAfter.deserialize(nativeScriptArray);
        } else if (type == 5) {
            return RequireTimeBefore.deserialize(nativeScriptArray);
        } else {
            return null;
        }
    }

    default byte[] serializeScriptBody() throws CborSerializationException {
        DataItem di = serializeAsDataItem();

        try {
            return CborSerializationUtil.serialize(di);
        } catch (CborException e) {
            throw new CborSerializationException("Cbor serializaion error", e);
        }
    }

    default byte[] getScriptTypeBytes() {
        return new byte[]{(byte) getScriptType()};
    }

    default int getScriptType() {
        return 0;
    }

    static NativeScript deserializeJson(String jsonContent) throws CborDeserializationException, JsonProcessingException {
        return NativeScript.deserialize(JsonUtil.parseJson(jsonContent));
    }

    static NativeScript deserialize(JsonNode jsonNode) throws CborDeserializationException {
        String type = jsonNode.get("type").asText();
        NativeScript nativeScript;
        switch (ScriptType.valueOf(type)) {
            case sig:
                nativeScript = ScriptPubkey.deserialize(jsonNode);
                break;
            case all:
                nativeScript = ScriptAll.deserialize(jsonNode);
                break;
            case any:
                nativeScript = ScriptAny.deserialize(jsonNode);
                break;
            case atLeast:
                nativeScript = ScriptAtLeast.deserialize(jsonNode);
                break;
            case after:
                nativeScript = RequireTimeAfter.deserialize(jsonNode);
                break;
            case before:
                nativeScript = RequireTimeBefore.deserialize(jsonNode);
                break;
            default:
                throw new RuntimeException("Unknown script type");
        }
        return nativeScript;
    }
}

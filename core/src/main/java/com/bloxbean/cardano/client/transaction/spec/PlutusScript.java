package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.Script;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAll;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public abstract class PlutusScript implements Script {
    @Setter(AccessLevel.NONE)
    @Getter
    protected String type;
    protected String description;
    protected String cborHex;

    public ByteString serializeAsDataItem() throws CborSerializationException {
        byte[] bytes = HexUtil.decodeHexString(cborHex);
        if (bytes.length > 0) {
            try {
                List<DataItem> diList = CborDecoder.decode(bytes);
                if (diList == null || diList.size() == 0)
                    throw new CborSerializationException("Serialization failed");

                DataItem di = diList.get(0);
                return (ByteString)di;
            } catch (CborException e) {
                throw new CborSerializationException("Serialization failed", e);
            }
        } else {
            return null;
        }
    }

    @Override
    public byte[] serializeScriptBody() throws CborSerializationException {
        return serializeAsDataItem().getBytes();
    }

    //TODO -- Don't add
    /**
     * Deserialize to {@link PlutusV1Script} or {@link PlutusV2Script} based on the type in script reference serialized bytes.
     * The serializedPlutusScript parameter contains both type and script body.
     * @param serializedPlutusScript
     * @return PlutusV1Script or PlutusV2Script
     */
    public static PlutusScript deserializeScriptRefBytes(byte[] serializedPlutusScript) {
        Array plutusScriptArray = (Array) CborSerializationUtil.deserialize(serializedPlutusScript);
        List<DataItem> dataItemList = plutusScriptArray.getDataItems();
        if (dataItemList == null || dataItemList.size() == 0) {
            throw new CborRuntimeException("PlutusScript deserialization failed. Invalid no of DataItem");
        }

        int type = ((UnsignedInteger) dataItemList.get(0)).getValue().intValue();
        ByteString scriptBytes = ((ByteString) dataItemList.get(1));
        try {
            if (type == 1) {
                return PlutusV1Script.deserialize(scriptBytes);
            } else if (type == 2) {
                return PlutusV2Script.deserialize(scriptBytes);
            } else {
                throw new CborRuntimeException("Invalid type : " + type);
            }
        } catch (Exception e) {
            throw new CborRuntimeException("PlutusScript deserialization failed.", e);
        }
    }
}

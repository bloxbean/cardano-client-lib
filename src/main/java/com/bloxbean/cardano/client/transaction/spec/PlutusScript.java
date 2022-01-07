package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.script.Script;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.util.List;

import static com.bloxbean.cardano.client.crypto.KeyGenUtil.blake2bHash224;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlutusScript implements Script {

    @Builder.Default
    private String type = "PlutusScriptV1";
    private String description;
    private String cborHex;

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

    //plutus_script = bytes ; New
    public static PlutusScript deserialize(ByteString plutusScriptDI) throws CborDeserializationException {
        if (plutusScriptDI != null) {
            PlutusScript plutusScript = new PlutusScript();
            byte[] bytes;
            try {
                bytes = CborSerializationUtil.serialize(plutusScriptDI);
            } catch (CborException e) {
                throw new CborDeserializationException("CBor deserialization error", e);
            }
            plutusScript.setCborHex(HexUtil.encodeHexString(bytes));
            return plutusScript;
        } else {
            return null;
        }
    }

    @JsonIgnore
    public byte[] getScriptHash() throws CborSerializationException {
        byte[] first = new byte[]{01};
        byte[] serializedBytes = serializeAsDataItem().getBytes();
        byte[] finalBytes = ByteBuffer.allocate(first.length + serializedBytes.length)
                .put(first)
                .put(serializedBytes)
                .array();

        return blake2bHash224(finalBytes);
    }

    @JsonIgnore
    public String getPolicyId() throws CborSerializationException {
        return Hex.toHexString(getScriptHash());
    }
}

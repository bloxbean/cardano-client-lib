package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlutusScript {
    private String type = "PlutusScriptV1";
    private String description;
    private String cborHex;

    public ByteString serialize() throws CborSerializationException {
        byte[] bytes = HexUtil.decodeHexString(cborHex);
        if (bytes != null && bytes.length > 0) {
            return new ByteString(bytes);
        } else {
            return null;
        }
    }

    //plutus_script = bytes ; New
    public static PlutusScript deserialize(ByteString plutusScriptDI) throws CborDeserializationException {
        if (plutusScriptDI != null) {
            PlutusScript plutusScript = new PlutusScript();
            plutusScript.setCborHex(HexUtil.encodeHexString(plutusScriptDI.getBytes()));
            return plutusScript;
        } else {
            return null;
        }
    }

//    public byte[] getScriptHash() throws CborSerializationException {
//        byte[] encodedBytes = serialize().getBytes();
//
//        return KeyGenUtil.blake2bHash256(encodedBytes);
//    }
}

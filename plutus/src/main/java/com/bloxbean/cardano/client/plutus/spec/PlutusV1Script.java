package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class PlutusV1Script extends PlutusScript {

    public PlutusV1Script() {
        this.type = "PlutusScriptV1";
    }

    //plutus_script = bytes ; New
    public static PlutusV1Script deserialize(ByteString plutusScriptDI) throws CborDeserializationException {
        if (plutusScriptDI != null) {
            PlutusV1Script plutusScript = new PlutusV1Script();
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

    @Override
    public byte[] getScriptTypeBytes() {
        return new byte[]{(byte) getScriptType()};
    }

    @Override
    public int getScriptType() {
        return 1;
    }
}

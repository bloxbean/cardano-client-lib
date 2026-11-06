package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.experimental.SuperBuilder;

/**
 * A Plutus V4 script, Dijkstra's script type 4 and language 3. Its hash prefix is 4.
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 */
@SuperBuilder
public class PlutusV4Script extends PlutusScript {
    public PlutusV4Script() {
        this.type = "PlutusScriptV4";
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param plutusScriptDI the script's byte string
     * @return the script, or null for null
     * @throws CborDeserializationException if the byte string cannot be encoded
     */
    public static PlutusV4Script deserialize(ByteString plutusScriptDI) throws CborDeserializationException {
        if (plutusScriptDI == null)
            return null;
        PlutusV4Script plutusScript = new PlutusV4Script();
        try {
            plutusScript.setCborHex(HexUtil.encodeHexString(CborSerializationUtil.serialize(plutusScriptDI)));
        } catch (CborException e) {
            throw new CborDeserializationException("CBor deserialization error", e);
        }
        return plutusScript;
    }

    @Override
    public byte[] getScriptTypeBytes() {
        return new byte[]{(byte) getScriptType()};
    }

    @Override
    public int getScriptType() {
        return 4;
    }

    @Override
    public Language getLanguage() {
        return Language.PLUTUS_V4;
    }
}

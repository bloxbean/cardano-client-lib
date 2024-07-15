package com.bloxbean.cardano.client.plutus.blueprint;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.util.HexUtil;

/**
 * Plutus blueprint utility class
 */
public class PlutusBlueprintUtil {

    /**
     * Convert plutus blueprint's compiled code to PlutusScript
     * @param compiledCode - compiled code from plutus blueprint
     * @param plutusVersion - Plutus version
     * @return PlutusScript
     */
    public static PlutusScript getPlutusScriptFromCompiledCode(String compiledCode, PlutusVersion plutusVersion) {
        //Do double encoding for aiken compileCode
        ByteString bs = new ByteString(HexUtil.decodeHexString(compiledCode));
        try {
            String cborHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(bs));
            if (plutusVersion.equals(PlutusVersion.v1)) {
                return PlutusV1Script.builder()
                        .cborHex(cborHex)
                        .build();
            } else if (plutusVersion.equals(PlutusVersion.v2)) {
                return PlutusV2Script.builder()
                        .cborHex(cborHex)
                        .build();
            } else if (plutusVersion.equals(PlutusVersion.v3)) {
                return PlutusV3Script.builder()
                        .cborHex(cborHex)
                        .build();
            } else
                throw new RuntimeException("Unsupported Plutus version" + plutusVersion);
        } catch (CborException e) {
            throw new RuntimeException(e);
        }
    }
}

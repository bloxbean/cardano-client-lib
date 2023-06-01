package com.bloxbean.cardano.client.plutus.util;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.util.HexUtil;

/**
 * Utility class for Plutus scripts
 */
public class PlutusUtil {

    /**
     * Get PlutusV2Script from compiled code. It double encodes the compiled code and returns a PlutusV2Script instance.
     * This method is useful when compiled code is from smart contract language like Aiken (https://aiken-lang.org/)
     *
     * @param compiledCode
     * @return PlutusV2Script
     */
    public static PlutusV2Script getPlutusV2Script(String compiledCode) {
        ByteString bs = new ByteString(HexUtil.decodeHexString(compiledCode));
        try {
            String cborHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(bs));
            return PlutusV2Script.builder()
                    .cborHex(cborHex)
                    .build();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }
    }
}

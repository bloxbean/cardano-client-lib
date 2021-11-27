package com.bloxbean.cardano.client.plutus;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;

public class PlutusUtil {

    public static String hashPlutusData(String hexValue) {
        return HexUtil.encodeHexString(KeyGenUtil.blake2bHash256(HexUtil.decodeHexString(hexValue)));
    }

    public static String hashPlutusData(PlutusData plutusData) throws CborException, CborSerializationException {
        return plutusData.getDatumHash();
    }
}

package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.PlutusData;
import com.bloxbean.cardano.client.transaction.spec.Redeemer;
import com.bloxbean.cardano.client.util.HexUtil;
import com.google.common.primitives.Bytes;

import java.util.List;

public class ScriptDataHashGenerator {

    public static byte[] generate(List<Redeemer> redeemers, List<PlutusData> datums, String languageViewsHex)
            throws CborException, CborSerializationException {
        return generate(redeemers, datums, HexUtil.decodeHexString(languageViewsHex));
    }

    public static byte[] generate(List<Redeemer> redeemers, List<PlutusData> datums, byte[] langViewsBytes)
            throws CborSerializationException, CborException {

        Array redeemerArray = new Array();
        if (redeemers != null && redeemers.size() > 0) {
            for (Redeemer redeemer : redeemers) {
                redeemerArray.add(redeemer.serialize());
            }
        }

        byte[] redeemerBytes = null;
        if (redeemerArray.getDataItems().size() != 0) {
            redeemerBytes = CborSerializationUtil.serialize(redeemerArray);
        } else {
            redeemerBytes = new byte[0];
        }

        Array datumArray = new Array();
        if (datums != null && datums.size() > 0) {
            for (PlutusData datum : datums) {
                datumArray.add(datum.serialize());
            }
        }

        byte[] plutusDataBytes = null;
        if (datumArray.getDataItems().size() != 0) {
            plutusDataBytes = CborSerializationUtil.serialize(datumArray);
        } else {
            plutusDataBytes = new byte[0];
        }

        byte[] encodedBytes = Bytes.concat(redeemerBytes, plutusDataBytes, langViewsBytes);
        return Blake2bUtil.blake2bHash256(encodedBytes);
    }
}

package com.bloxbean.cardano.client.plutus.util;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.List;

public class ScriptDataHashGenerator {

    public static byte[] generate(List<Redeemer> redeemers, List<PlutusData> datums, String languageViewsHex)
            throws CborException, CborSerializationException {
        return generate(redeemers, datums, HexUtil.decodeHexString(languageViewsHex));
    }

    public static byte[] generate(List<Redeemer> redeemers, List<PlutusData> datums, byte[] langViewsBytes)
            throws CborSerializationException, CborException {
        /**
         ; script data format:
         ; [ redeemers | datums | language views ]
         ; The redeemers are exactly the data present in the transaction witness set.
         ; Similarly for the datums, if present. If no datums are provided, the middle
         ; field is an empty string.
         **/
        Array datumArray = new Array();
        if (datums != null && datums.size() > 0) {
            for (PlutusData datum : datums) {
                datumArray.add(datum.serialize());
            }
        }

        byte[] plutusDataBytes;
        if (datumArray.getDataItems().size() != 0) {
            plutusDataBytes = CborSerializationUtil.serialize(datumArray);
        } else {
            plutusDataBytes = new byte[0];
        }

        byte[] encodedBytes;
        if (redeemers != null && redeemers.size() > 0) {
            Array redeemerArray = new Array();
            for (Redeemer redeemer : redeemers) {
                redeemerArray.add(redeemer.serialize());
            }
            byte[] redeemerBytes = CborSerializationUtil.serialize(redeemerArray);
            encodedBytes = Bytes.concat(redeemerBytes, plutusDataBytes, langViewsBytes);
        } else {
            /**
             ; Finally, note that in the case that a transaction includes datums but does not
             ; include any redeemers, the script data format becomes (in hex):
             ; [ 80 | datums | A0 ]
             ; corresponding to a CBOR empty list and an empty map.
             **/
            encodedBytes = Bytes.concat(HexUtil.decodeHexString("0x80"), plutusDataBytes, HexUtil.decodeHexString("0xA0"));
        }

        return Blake2bUtil.blake2bHash256(encodedBytes);
    }
}

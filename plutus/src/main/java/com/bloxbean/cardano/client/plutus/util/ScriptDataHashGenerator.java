package com.bloxbean.cardano.client.plutus.util;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.List;

public class ScriptDataHashGenerator {

    public static byte[] generate(List<Redeemer> redeemers, List<PlutusData> datums, CostMdls costMdls)
            throws CborSerializationException, CborException {
        return generate(null, redeemers, datums, costMdls);
    }

    public static byte[] generate(Era era, List<Redeemer> redeemers, List<PlutusData> datums, CostMdls costMdls)
            throws CborSerializationException, CborException {

        if (era == null)
            era = EraSerializationConfig.INSTANCE.getEra();


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

            byte[] redeemerBytes;
            if (era.value >= Era.Conway.value) {
                Map redeemerMap = new Map();
                for(Redeemer redeemer: redeemers) {
                    var tuple = redeemer.serialize();
                    redeemerMap.put(tuple._1, tuple._2);
                }
                redeemerBytes = CborSerializationUtil.serialize(redeemerMap);
            } else {
                Array redeemerArray = new Array();
                for (Redeemer redeemer : redeemers) {
                    redeemerArray.add(redeemer.serializePreConway());
                }
                redeemerBytes = CborSerializationUtil.serialize(redeemerArray);
            }

            encodedBytes = Bytes.concat(redeemerBytes, plutusDataBytes, costMdls.getLanguageViewEncoding());
        } else {
            if (era.value >= Era.Conway.value) {
                encodedBytes = Bytes.concat(HexUtil.decodeHexString("0xA0"), plutusDataBytes, costMdls.getLanguageViewEncoding());
            } else { //Pre conway era
                /**
                 ; Finally, note that in the case that a transaction includes datums but does not
                 ; include any redeemers, the script data format becomes (in hex):
                 ; [ 80 | datums | A0 ]
                 ; corresponding to a CBOR empty list and an empty map.
                 **/
                encodedBytes = Bytes.concat(HexUtil.decodeHexString("0x80"), plutusDataBytes, HexUtil.decodeHexString("0xA0"));
            }
        }

        return Blake2bUtil.blake2bHash256(encodedBytes);
    }
}

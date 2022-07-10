package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;

import java.util.HashMap;

public class CostMdls {
    private java.util.Map<Language, CostModel> costMdlsMap;

    public CostMdls() {
        costMdlsMap = new HashMap<>();
    }

    public void add(CostModel costModel) {
        costMdlsMap.put(costModel.getLanguage(), costModel);
    }

    public CostModel get(Language language) {
        return costMdlsMap.get(language);
    }

    public byte[] getLanguageViewEncoding() {
        try {
            Map cborMap = new Map();
            for (java.util.Map.Entry<Language, CostModel> entry : costMdlsMap.entrySet()) {
                Language language = entry.getKey();
                CostModel costModel = entry.getValue();

                if (language == Language.PLUTUS_V1) {
                    serializeV1(cborMap, costModel);
                } else if (language == Language.PLUTUS_V2) {
                    serializeV2(cborMap, costModel);
                } else
                    throw new CborException("Invalid language : " + language);
            }

            return CborSerializationUtil.serialize(cborMap);
        } catch (CborException ex) {
            throw new CborRuntimeException("Language views encoding failed", ex);
        }
    }

    private void serializeV2(Map cborMap, CostModel costModel) {
        UnsignedInteger key = new UnsignedInteger(costModel.getLanguage().getKey());
        Array valueArr = new Array();
        for (long cost : costModel.getCosts()) {
            if (cost >= 0)
                valueArr.add(new UnsignedInteger(cost));
            else
                valueArr.add(new NegativeInteger(cost));
        }

        cborMap.put(key, valueArr);
    }

    private void serializeV1(Map cborMap, CostModel costModel) throws CborException {
        // For PlutusV1 (language id 0), the language view is the following:
        //   * the value of costmdls map at key 0 is encoded as an indefinite length
        //     list and the result is encoded as a bytestring. (our apologies)
        //   * the language ID tag is also encoded twice. first as a uint then as
        //     a bytestring.
        UnsignedInteger key = new UnsignedInteger(costModel.getLanguage().getKey());
        ByteString keyByteStr = new ByteString(CborSerializationUtil.serialize(key));

        Array valueArr = new Array();
        valueArr.setChunked(true);
        for (long cost : costModel.getCosts()) {
            if (cost >= 0)
                valueArr.add(new UnsignedInteger(cost));
            else
                valueArr.add(new NegativeInteger(cost));
        }
        valueArr.add(Special.BREAK);
        ByteString valueBS = new ByteString(CborSerializationUtil.serialize(valueArr));

        cborMap.put(keyByteStr, valueBS);
    }
}

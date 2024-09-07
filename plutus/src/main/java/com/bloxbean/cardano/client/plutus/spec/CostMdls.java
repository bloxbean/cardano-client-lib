package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;

import java.util.TreeMap;

public class CostMdls {
    private java.util.Map<Language, CostModel> costMdlsMap;

    public CostMdls() {
        costMdlsMap = new TreeMap<>();
    }

    public void add(CostModel costModel) {
        costMdlsMap.put(costModel.getLanguage(), costModel);
    }

    public CostModel get(Language language) {
        return costMdlsMap.get(language);
    }

    public byte[] getLanguageViewEncoding() {
        try {
            Map cborMap = serializeLanguageView();

            return CborSerializationUtil.serialize(cborMap);
        } catch (Exception ex) {
            throw new CborRuntimeException("Language views encoding failed", ex);
        }
    }

    public boolean isEmpty() {
        return costMdlsMap.isEmpty();
    }

    public Map serialize() throws CborSerializationException {
        Map cborMap = new Map();
        try {
            for (java.util.Map.Entry<Language, CostModel> entry : costMdlsMap.entrySet()) {
                CostModel costModel = entry.getValue();
                serializeV2(cborMap, costModel);
            }
        } catch (Exception e) {
            throw new CborSerializationException("Costmdls serialization error", e);
        }
        return cborMap;
    }

    //This serialization is used for language view
    private Map serializeLanguageView() throws CborSerializationException {
        Map cborMap = new Map();
        try {
            for (java.util.Map.Entry<Language, CostModel> entry : costMdlsMap.entrySet()) {
                Language language = entry.getKey();
                CostModel costModel = entry.getValue();

                if (language == Language.PLUTUS_V1) {
                    serializeV1(cborMap, costModel);
                } else {
                    serializeV2(cborMap, costModel); //Plutus V2 and V3
                }
            }
        } catch (Exception e) {
            throw new CborSerializationException("Costmdls serialization error", e);
        }
        return cborMap;
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

    //This is an old format of serialization. This is not used anymore. But kept for information only.
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

    public static CostMdls deserialize(DataItem di) throws CborDeserializationException {
        Map map = (Map)di;

        CostMdls costMdls = new CostMdls();
        for (DataItem key: map.getKeys()) {
            Array costModelArr;
            int langKey;
            int size;
            if (key.getMajorType() == MajorType.BYTE_STRING ) { //Looks like Plutus V1 old format. Plz refer to serializationV1 method
                UnsignedInteger intKey = (UnsignedInteger) CborSerializationUtil.deserialize(((ByteString)key).getBytes());
                langKey = intKey.getValue().intValue();
                ByteString valueBS = (ByteString) map.get(key);
                costModelArr = (Array) CborSerializationUtil.deserialize(valueBS.getBytes());
                size = costModelArr.getDataItems().size() - 1; //Remove the BREAK
            } else {
                langKey = ((UnsignedInteger)key).getValue().intValue();
                costModelArr = (Array) map.get(key);
                size = costModelArr.getDataItems().size();
            }

            long[] costs = new long[size];
            int index = 0;
            //iterate to build costmodel arr
            for (DataItem costItem: costModelArr.getDataItems()) {
                if (costItem == Special.BREAK)
                    continue;

                long cost = 0;
                if (costItem instanceof UnsignedInteger) {
                    cost = ((UnsignedInteger)costItem).getValue().longValue();
                } else if (costItem instanceof NegativeInteger) {
                    cost = ((NegativeInteger)costItem).getValue().longValue(); //TODO -- Check for negative value
                }
                costs[index++] = cost;
            }

            Language language;
            if (langKey == 0)
                language = Language.PLUTUS_V1;
            else if (langKey == 1)
                language = Language.PLUTUS_V2;
            else if (langKey == 2)
                language = Language.PLUTUS_V3;
            else
                throw new CborDeserializationException("De-serialization failed. Invalid language key : " + langKey);

            CostModel costModel = new CostModel(language, costs);
            costMdls.add(costModel);
        }

        return costMdls;
    }
}

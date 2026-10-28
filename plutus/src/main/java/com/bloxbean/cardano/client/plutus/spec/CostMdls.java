package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import co.nstant.in.cbor.model.Number;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

public class CostMdls {
    private java.util.Map<Language, CostModel> costMdlsMap;
    // Cost models of language ids that Language does not know. Conway keeps them (cardano-ledger CostModels'
    // costModelsUnknown), so they are read and written back.
    private java.util.Map<Integer, long[]> unknownCostMdlsMap;

    public CostMdls() {
        costMdlsMap = new TreeMap<>();
        unknownCostMdlsMap = new TreeMap<>();
    }

    public void add(CostModel costModel) {
        costMdlsMap.put(costModel.getLanguage(), costModel);
    }

    public CostModel get(Language language) {
        return costMdlsMap.get(language);
    }

    /**
     * @return the cost models of language ids (0 to 255) that {@link Language} does not know, by language id
     */
    public java.util.Map<Integer, long[]> getUnknownCostModels() {
        return Collections.unmodifiableMap(unknownCostMdlsMap);
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
        return costMdlsMap.isEmpty() && unknownCostMdlsMap.isEmpty();
    }

    public Map serialize() throws CborSerializationException {
        Map cborMap = new Map();
        try {
            for (java.util.Map.Entry<Language, CostModel> entry : costMdlsMap.entrySet()) {
                CostModel costModel = entry.getValue();
                serializeV2(cborMap, costModel);
            }
            for (java.util.Map.Entry<Integer, long[]> entry : unknownCostMdlsMap.entrySet())
                cborMap.put(new UnsignedInteger(entry.getKey()), costsArray(entry.getValue()));
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
        cborMap.put(key, costsArray(costModel.getCosts()));
    }

    private static Array costsArray(long[] costs) {
        Array valueArr = new Array();
        for (long cost : costs) {
            if (cost >= 0)
                valueArr.add(new UnsignedInteger(cost));
            else
                valueArr.add(new NegativeInteger(cost));
        }
        return valueArr;
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
            BigInteger langKey;
            if (key.getMajorType() == MajorType.BYTE_STRING ) { //Looks like Plutus V1 old format. Plz refer to serializationV1 method
                UnsignedInteger intKey = (UnsignedInteger) CborSerializationUtil.deserialize(((ByteString)key).getBytes());
                langKey = intKey.getValue();
                ByteString valueBS = (ByteString) map.get(key);
                costModelArr = (Array) CborSerializationUtil.deserialize(valueBS.getBytes());
            } else {
                langKey = ((UnsignedInteger)key).getValue();
                costModelArr = (Array) map.get(key);
            }

            // A language id is a Word8 and a cost an Int64 (cardano-ledger: Map Word8 [Int64])
            if (langKey.bitLength() > 8)
                throw new CborDeserializationException("De-serialization failed. Invalid language key : " + langKey);

            List<DataItem> costItems = costModelArr.getDataItems();
            long[] costs = new long[costItems.size()];
            for (int i = 0; i < costs.length; i++) {
                DataItem costItem = costItems.get(i);
                if (!(costItem instanceof Number))
                    throw new CborDeserializationException("De-serialization failed. Invalid cost : " + costItem);
                BigInteger cost = ((Number) costItem).getValue();
                if (cost.bitLength() > 63)
                    throw new CborDeserializationException("De-serialization failed. Cost out of range : " + cost);
                costs[i] = cost.longValue();
            }

            int languageId = langKey.intValue();
            if (languageId == 0)
                costMdls.add(new CostModel(Language.PLUTUS_V1, costs));
            else if (languageId == 1)
                costMdls.add(new CostModel(Language.PLUTUS_V2, costs));
            else if (languageId == 2)
                costMdls.add(new CostModel(Language.PLUTUS_V3, costs));
            else
                costMdls.unknownCostMdlsMap.put(languageId, costs);
        }

        return costMdls;
    }
}

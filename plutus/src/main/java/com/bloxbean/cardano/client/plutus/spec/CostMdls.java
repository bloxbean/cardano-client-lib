package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.RawCostModels;

import java.util.Collections;
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

    /**
     * Adds a cost model. It replaces a cost model of the same language id read as unknown, as a known model takes
     * precedence in cardano-ledger's {@code flattenCostModels}.
     *
     * @param costModel the cost model
     */
    public void add(CostModel costModel) {
        costMdlsMap.put(costModel.getLanguage(), costModel);
        unknownCostMdlsMap.remove(costModel.getLanguage().getKey());
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
                    serializeV2(cborMap, costModel); //Plutus V2, V3 and V4
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
        try {
            return from(RawCostModels.lenient(CborSerializationUtil.span(di)));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("De-serialization failed. " + e.getMessage(), e);
        }
    }

    /**
     * Reads cost models as Conway does: language 3, Plutus V4, is a language {@link Language} does not know, kept in
     * {@link #getUnknownCostModels()}; read them with {@link #from(RawCostModels, Era)} in Dijkstra to have it as
     * {@link Language#PLUTUS_V4}.
     *
     * @param raw cost models as received
     * @return the model: a language read twice keeps the later cost model
     * @throws CborDeserializationException if a language id is not a Word8 (cardano-ledger: {@code Map Word8 [Int64]})
     */
    public static CostMdls from(RawCostModels raw) throws CborDeserializationException {
        return from(raw, Era.Conway);
    }

    /**
     * Reads cost models in an era: from Dijkstra, language 3 is {@link Language#PLUTUS_V4}; before it, it is a language
     * the era does not know, kept in {@link #getUnknownCostModels()}.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param raw cost models as received
     * @param era the era to read them in, or null to read them as {@link #from(RawCostModels)} does
     * @return the model: a language read twice keeps the later cost model
     * @throws CborDeserializationException if a language id is not a Word8 (cardano-ledger: {@code Map Word8 [Int64]})
     */
    public static CostMdls from(RawCostModels raw, Era era) throws CborDeserializationException {
        CostMdls costMdls = new CostMdls();
        for (RawCostModels.Model model : raw.models()) {
            int languageId = model.language();
            if (languageId > 0xff)
                throw new CborDeserializationException("De-serialization failed. Invalid language key : " + languageId);
            long[] costs = model.costs();
            Language language = language(languageId, era);
            if (language != null)
                costMdls.add(new CostModel(language, costs));
            else
                costMdls.unknownCostMdlsMap.put(languageId, costs);
        }
        return costMdls;
    }

    // The language of an id in the era, or null if the era does not know it: Plutus V4 is Dijkstra's.
    private static Language language(int languageId, Era era) {
        for (Language language : Language.values()) {
            if (language.getKey() == languageId)
                return language != Language.PLUTUS_V4 || era != null && era.value >= Era.Dijkstra.value ? language
                        : null;
        }
        return null;
    }
}

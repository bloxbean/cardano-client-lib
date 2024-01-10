package com.bloxbean.cardano.client.cip.cip68;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.AssetTransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.cip.cip67.CIP67AssetNameUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;
import java.util.Optional;

public class CIP68MetadataUtil {

    private CIP68MetadataUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * According to CIP68 a user token has to have a reference token. This function returns the name of the reference token according to CIP68.
     * @param assetname name of the User token
     * @return reference token name according to the passed user token
     */
    public static String getReferenceTokenName(String assetname) {
        String policyID = "";
        if(assetname.length() > 32) { // contains policyID
            policyID = assetname.substring(0,56);
            assetname = assetname.substring(56);
        }
        String cip67Prefix = assetname.substring(0,8);
        if(CIP67AssetNameUtil.isValidAssetName(HexUtil.decodeHexString(cip67Prefix))) {
            System.out.println("Throw exception");
        }
        String name = assetname.substring(8);
        byte[] referenzTokenPrefixBytes = CIP67AssetNameUtil.labelToPrefix(100);
        return policyID + HexUtil.encodeHexString(referenzTokenPrefixBytes) + name;
    }

    /**
     * The metadata, which describes the asset parameters, is locked in the latest utxo
     * @param referenceTokenName name of the reference token
     * @param backendService Provide a BackendService to be used while processing
     * @return Utxo with correct datum
     * @throws ApiException BackendService could throw an apiexception.
     */
    public static TxContentUtxoOutputs getReferenceTokenUtxo(String referenceTokenName, BackendService backendService) throws ApiException {
        // just get the latest transaction
        AssetTransactionContent assetTransactionContent = backendService.getAssetService().getTransactions(referenceTokenName, 1, 1, OrderEnum.desc).getValue().get(0);
        List<TxContentUtxoOutputs> outputs = backendService.getTransactionService().getTransactionUtxos(assetTransactionContent.getTxHash()).getValue().getOutputs();

        TxContentUtxoOutputs containingInlineDatum = null;
        // find utxo where the reference token is included in the amount as unit
        Optional<TxContentUtxoOutputs> optionalReferenceTokenOutput = outputs.stream()
                .filter(a -> a.getAmount().stream()
                        .anyMatch(b -> b.getUnit().equals(referenceTokenName)))
                .findFirst();
        if(optionalReferenceTokenOutput.isPresent()) {
            containingInlineDatum = optionalReferenceTokenOutput.get();
        }
        return containingInlineDatum;
    }


    /**
     * Deserializing the datum to UTF8 and processing it as JSON.
     * @param outputContainingDatum UTXO
     * @return UTF8 JSON of the datum
     */
    public static String getDatumAsUTF8Json(TxContentUtxoOutputs outputContainingDatum) {
        DataItem dezerialedDatum = CborSerializationUtil.deserialize(HexUtil.decodeHexString(outputContainingDatum.getInlineDatum()));
        return getDatumAsUTF8Json(dezerialedDatum);
    }

    /**
     * Deserializing the datum to UTF8 and processing it as JSON.
     * @param dezerializedDatum Cbor deserialized datum
     * @return UTF8 JSON of the datum
     */
    public static String getDatumAsUTF8Json(DataItem dezerializedDatum) {
        if(!dezerializedDatum.getMajorType().equals(MajorType.ARRAY)) {
            throw new RuntimeException("DataItem must be an Array type according to CIP68.");
        }
        List<DataItem> dataItems = ((Array)dezerializedDatum).getDataItems();
        Map m = (Map)dataItems.get(0);
        MapPlutusData mapPlutusData = null;
        try {
            mapPlutusData = MapPlutusData.deserialize(m);
        } catch (CborDeserializationException e) {
            throw new RuntimeException("Can't deserialze MapPlutusData", e);
        }
        return getDatumAsUTF8Json(mapPlutusData);
    }

    /**
     * Deserializing the datum to UTF8 and processing it as JSON.
     * @param plutusData deserialized PlutusData
     * @return UTF8 JSON of the datum
     */
    public static String getDatumAsUTF8Json(PlutusData plutusData) {
        try {
            return PlutusDataJsonConverter.toUTF8Json(plutusData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing Json from PlutusData", e);
        } catch (CborSerializationException e) {
            throw new RuntimeException("Can't deserialize PlutusData while processing Json", e);
        }
    }


}
package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.AssetTransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.cip.cip67.CIP67AssetNameUtil;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.List;
import java.util.Optional;

public class CIP68MetadataUtil {

    private CIP68MetadataUtil() {
        throw new IllegalStateException("Utility class");
    }

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

    public static TxContentUtxoOutputs getReferenceTokenUtxo(String referenceTokenName, BackendService backendService) throws ApiException {
        // to get the oldest transaction since it can be modified
        AssetTransactionContent assetTransactionContent = backendService.getAssetService().getTransactions(referenceTokenName, 1, 1, OrderEnum.desc).getValue().get(0); // just get the oldest transaction
        List<TxContentUtxoOutputs> outputs = backendService.getTransactionService().getTransactionUtxos(assetTransactionContent.getTxHash()).getValue().getOutputs(); // get output utxos for the transaction

        TxContentUtxoOutputs containingInlineDatum = null;
        Optional<TxContentUtxoOutputs> optionalReferenceTokenOutput = outputs.stream()
                .filter(a -> a.getAmount().stream()
                        .anyMatch(b -> b.getUnit().equals(referenceTokenName))).findFirst();
        if(optionalReferenceTokenOutput.isPresent()) {
            containingInlineDatum = optionalReferenceTokenOutput.get();
        }
        return containingInlineDatum;
    }



}
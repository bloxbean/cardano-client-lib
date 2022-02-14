package com.bloxbean.cardano.client.common;

import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;

public class MinAdaCalculator {

    private final BigInteger adaOnlyMinUtxoValue = ONE_ADA;
    private final int utxoEntrySizeWithoutVal = 27;
    private BigInteger coinsPerUtxoWord; //protocol parameter

    public MinAdaCalculator(ProtocolParams protocolParams) {
        if (protocolParams.getCoinsPerUtxoWord() != null && !protocolParams.getCoinsPerUtxoWord().isEmpty()) {
            this.coinsPerUtxoWord = new BigInteger(protocolParams.getCoinsPerUtxoWord());
        }
    }

    public BigInteger calculateMinAda(TransactionOutput output) {
        if (output.getValue().getMultiAssets() == null || output.getValue().getMultiAssets().size() == 0)
            return adaOnlyMinUtxoValue;

        return calculateMinAda(output.getValue().getMultiAssets(),
                output.getDatumHash() != null && output.getDatumHash().length > 0);
    }

    public BigInteger calculateMinAda(List<MultiAsset> multiAssetList, boolean hasDataHash) {
        if (multiAssetList == null || multiAssetList.size() == 0)
            return adaOnlyMinUtxoValue;

        long sizeB = bundleSize(multiAssetList);
        //According to https://github.com/input-output-hk/cardano-ledger/blob/master/doc/explanations/min-utxo-alonzo.rst
        long dataHashSize = hasDataHash ? 10 : 0;

        long utxoEntrySize = utxoEntrySizeWithoutVal + sizeB + dataHashSize;

        return coinsPerUtxoWord.multiply(BigInteger.valueOf(utxoEntrySize));
    }

    public BigInteger calculateMinAda(List<MultiAsset> multiAssetList) {
        return calculateMinAda(multiAssetList, false);
    }

    private long bundleSize(List<MultiAsset> multiAssetList) {
        int numAssets = 0;
        int numPIDs = 0;
        long sumAssetNameLengths = 0;
        int pidSize = 28; //Policy id size is currently 28

        Set<String> uniqueAssetNames = new HashSet<>();
        //If multi asssets
        for (MultiAsset ma : multiAssetList) {
            numPIDs++;
            for (Asset asset : ma.getAssets()) {
                numAssets++;

                //the sum of the length of the ByteStrings representing distinct asset names
                if (!uniqueAssetNames.contains(asset.getName())) {
                    sumAssetNameLengths += asset.getNameAsBytes().length;
                    if (asset.getName() != null && !asset.getName().isEmpty()) {
                        uniqueAssetNames.add(asset.getName());
                    }
                }
            }
        }

        return calculateSizeB(numAssets, sumAssetNameLengths, numPIDs, pidSize);
    }

    private long calculateSizeB(long numAssets, long sumAssetNameLengths, int numPIDs, int pidSize) {
        long totalBytes = (numAssets * 12) + sumAssetNameLengths + ((long) numPIDs * pidSize);
        return 6 + roundupBytesToWords(totalBytes);
    }

    private long roundupBytesToWords(long noOfBytes) {
        return (noOfBytes + 7) / 8;
    }
}

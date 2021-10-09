package com.bloxbean.cardano.client.common;

import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;

public class MinAdaCalculator {
    private BigInteger adaOnlyMinUtxoValue = ONE_ADA;
    private int utxoEntrySizeWithoutVal = 27;
    private BigInteger coinsPerUtxoWord; //protocol parameter

    public MinAdaCalculator(ProtocolParams protocolParams) {
        if(protocolParams.getCoinsPerUtxoWord() != null && !protocolParams.getCoinsPerUtxoWord().isEmpty()) {
            this.coinsPerUtxoWord = new BigInteger(protocolParams.getCoinsPerUtxoWord());
        }
    }

    public BigInteger calculateMinAda(TransactionOutput output) {
        if(output.getValue().getMultiAssets() == null || output.getValue().getMultiAssets().size() == 0)
            return adaOnlyMinUtxoValue;

        long sizeB = bundleSize(output);

        long utxoEntrySize = utxoEntrySizeWithoutVal + sizeB; //TODO + dataHashSize (dh)
        BigInteger value = coinsPerUtxoWord.multiply(BigInteger.valueOf(utxoEntrySize));

        return value;
    }

    private long bundleSize(TransactionOutput output) {
        int numAssets = 0;
        int numPIDs = 0;
        long sumAssetNameLengths = 0;
        int pidSize = 28; //Policy id size is currently 28

        Set<String> uniqueAssetNames = new HashSet<>();
        //If multi asssets
        for(MultiAsset ma: output.getValue().getMultiAssets()) {
            numPIDs++;
            for(Asset asset: ma.getAssets()) {
                numAssets++;

                //the sum of the length of the ByteStrings representing distinct asset names
                if(!uniqueAssetNames.contains(asset.getName())) {
                    sumAssetNameLengths += asset.getNameAsBytes().length;
                    if(asset.getName() != null && !asset.getName().isEmpty()) {
                        uniqueAssetNames.add(asset.getName());
                    }
                }
            }
        }

        long sizeB = calculateSizeB(numAssets, sumAssetNameLengths, numPIDs, pidSize);
        return sizeB;
    }

    private long calculateSizeB(long numAssets, long sumAssetNameLengths, int numPIDs, int pidSize) {
        long totalBytes = (numAssets * 12) + sumAssetNameLengths + (numPIDs * pidSize);
        return 6 + roundupBytesToWords(totalBytes);
    }

    private long roundupBytesToWords(long noOfBytes) {
        return (noOfBytes + 7) / 8;
    }
}

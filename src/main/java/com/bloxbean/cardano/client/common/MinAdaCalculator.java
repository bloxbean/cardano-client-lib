package com.bloxbean.cardano.client.common;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;

import java.math.BigInteger;

public class MinAdaCalculator {
    private BigInteger minUtxoValue;
    int adaOnlyUtxoSize = 27;
    int utxoEntrySizeWithoutVal = 27;

    public MinAdaCalculator(BigInteger minUtxoValue) {
        this.minUtxoValue = minUtxoValue;
    }

    public BigInteger calculateMinAda(TransactionOutput output) {
        if(output.getValue().getMultiAssets() == null || output.getValue().getMultiAssets().size() == 0)
            return minUtxoValue;

        int numAssets = 0;
        int numPIDs = 0;
        long sumAssetNameLengths = 0;
        int pidSize = 28; //Policy id size is currently 28
        //If multi asssets
        for(MultiAsset ma: output.getValue().getMultiAssets()) {
            numPIDs++;
            for(Asset asset: ma.getAssets()) {
                numAssets++;
                sumAssetNameLengths += asset.getNameAsBytes().length;
            }
        }

        long sizeB = calculateSizeB(numAssets, sumAssetNameLengths, numPIDs, pidSize);
        //minAda (u) = max (minUTxOValue, (quot (minUTxOValue, adaOnlyUTxOSize)) * (utxoEntrySizeWithoutVal + (size B)))
        //adaOnlyUTxOSize = utxoEntrySizeWithoutVal + coinSize = 27
        //quot (minUTxOValue, adaOnlyUTxOSize))
        BigInteger a = (minUtxoValue.divide(BigInteger.valueOf(adaOnlyUtxoSize)));
        long b = utxoEntrySizeWithoutVal + sizeB;

        BigInteger value = a.multiply(BigInteger.valueOf(b));

        if(value.compareTo(minUtxoValue) == 1)
            return value;
        else
            return minUtxoValue;

    }

    private long calculateSizeB(long numAssets, long sumAssetNameLengths, int numPIDs, int pidSize) {
        long totalBytes = (numAssets * 12) + sumAssetNameLengths + (numPIDs * pidSize);
        return 6 + roundupBytesToWords(totalBytes);
    }

    private long roundupBytesToWords(long noOfBytes) {
        return (noOfBytes + 7) / 8;
    }
}

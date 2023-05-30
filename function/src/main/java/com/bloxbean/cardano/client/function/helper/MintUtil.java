package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import lombok.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MintUtil {

    /**
     * Sorts the list of MultiAsset by policyId. The multiAssets list in mint field of transaction is sorted by the policyId.
     * This method is useful to get the final index of a MultiAsset in the list of MultiAsset in the mint field of transaction.
     * @param multiAssets
     * @return sorted list of MultiAsset
     */
    public static List<MultiAsset> getSortedMultiAssets(@NonNull List<MultiAsset> multiAssets) {
        List<MultiAsset> copyMultiAssets = multiAssets
                .stream()
                .collect(Collectors.toList());
        copyMultiAssets.sort(
                Comparator.comparing(MultiAsset::getPolicyId)
        );
        return copyMultiAssets;
    }

    /**
     * Returns the index of the MultiAsset in the list of MultiAsset
     * @param policyId policyId of the MultiAsset
     * @param multiAssets list of MultiAsset
     * @return index of the MultiAsset in the list of MultiAsset
     */
    public static int getIndexByPolicyId(@NonNull List<MultiAsset> multiAssets, @NonNull String policyId) {
        return IntStream.range(0, multiAssets.size())
                .filter(i -> policyId.equals(multiAssets.get(i).getPolicyId()))
                .findFirst()
                .orElse(-1);
    }
}

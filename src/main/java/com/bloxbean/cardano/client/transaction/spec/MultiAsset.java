package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MultiAsset {

    private String policyId;

    @Builder.Default
    private List<Asset> assets = new ArrayList<>();

    public static MultiAsset deserialize(Map multiAssetsMap, DataItem key) {
        MultiAsset multiAsset = new MultiAsset();
        ByteString keyBS = (ByteString) key;
        multiAsset.setPolicyId(HexUtil.encodeHexString(keyBS.getBytes()));

        Map assetsMap = (Map) multiAssetsMap.get(key);
        for (DataItem assetKey : assetsMap.getKeys()) {
            ByteString assetNameBS = (ByteString) assetKey;

            DataItem valueDI = assetsMap.get(assetKey);
            BigInteger value = CborSerializationUtil.getBigInteger(valueDI);

            String name = HexUtil.encodeHexString(assetNameBS.getBytes(), true);
            multiAsset.getAssets().add(new Asset(name, value));
        }
        return multiAsset;
    }

    /**
     * Creates a new list of multi assets from those passed as parameters.
     * Multi Assets with the same policy id will be aggregated together, and matching assets summed.
     *
     * @param multiAssets1 List of MultiAssets as first argument
     * @param multiAssets2 List of MultiAssets as second argument
     * @return List MultiAssets which represents the Sum
     */
    public static List<MultiAsset> mergeMultiAssetLists(List<MultiAsset> multiAssets1, List<MultiAsset> multiAssets2) {
        List<MultiAsset> tempMultiAssets = new ArrayList<>();
        if (multiAssets1 != null) {
            tempMultiAssets.addAll(multiAssets1);
        }
        if (multiAssets2 != null) {
            tempMultiAssets.addAll(multiAssets2);
        }
        return tempMultiAssets
                .stream()
                .collect(Collectors.groupingBy(MultiAsset::getPolicyId))
                .entrySet()
                .stream()
                .map(entry -> entry.getValue().stream().reduce(MultiAsset.builder().policyId(entry.getKey()).assets(Arrays.asList()).build(), MultiAsset::plus))
                .collect(Collectors.toList());
    }

    /**
     * Creates a new list of multi assets from those passed as parameters.
     * Multi Assets with the same policy id will be aggregated together, and matching assets subtracted.
     *
     * @param multiAssets1 List of MultiAssets as first argument
     * @param multiAssets2 List of MultiAssets as second argument
     * @return List MultiAssets which represents the Subtraction
     */
    public static List<MultiAsset> subtractMultiAssetLists(List<MultiAsset> multiAssets1, List<MultiAsset> multiAssets2) {
        List<MultiAsset> tempMultiAssets = new ArrayList<>();
        if (multiAssets1 != null) {
            tempMultiAssets.addAll(multiAssets1);
        }
        List<MultiAsset> multiAssetListResult = new ArrayList<>();
        java.util.Map<String, MultiAsset> thatMultiAssetsMap = convertListToMap(multiAssets2);
        for (MultiAsset multiAsset : tempMultiAssets) {
            if (thatMultiAssetsMap.containsKey(multiAsset.getPolicyId())) {
                multiAssetListResult.add(multiAsset.minus(thatMultiAssetsMap.get(multiAsset.getPolicyId())));
            } else {
                multiAssetListResult.add(multiAsset);
            }
        }
        return multiAssetListResult;
    }

    private static java.util.Map<String, MultiAsset> convertListToMap(List<MultiAsset> multiAssets) {
        return multiAssets.stream().collect(Collectors.toMap(MultiAsset::getPolicyId, Function.identity()));
    }

    public void serialize(Map multiAssetMap) {
        Map assetsMap = new Map();
        for (Asset asset : assets) {
            ByteString assetNameBytes = new ByteString(asset.getNameAsBytes());

            if (asset.getValue().compareTo(BigInteger.ZERO) == 0 || asset.getValue().compareTo(BigInteger.ZERO) == 1) {
                UnsignedInteger value = new UnsignedInteger(asset.getValue());
                assetsMap.put(assetNameBytes, value);
            } else {
                NegativeInteger value = new NegativeInteger(asset.getValue());
                assetsMap.put(assetNameBytes, value);
            }
        }

        ByteString policyIdByte = new ByteString(HexUtil.decodeHexString(policyId));
        multiAssetMap.put(policyIdByte, assetsMap);
    }

    @Override
    public String toString() {
        try {
            return "MultiAsset{" +
                    "policyId=" + policyId +
                    ", assets=" + assets +
                    '}';
        } catch (Exception e) {
            return "MultiAsset { Error : " + e.getMessage() + " }";
        }
    }

    /**
     * Sums a Multi Asset to another. If an Asset is already present, sums the amounts.
     *
     * @param that {@link MultiAsset} to Sum with
     * @return {@link MultiAsset} as Sum result
     */
    public MultiAsset plus(MultiAsset that) {
        if (!getPolicyId().equals(that.getPolicyId())) {
            throw new IllegalArgumentException("Trying to add MultiAssets with different policyId");
        }
        ArrayList<Asset> assetsClone = new ArrayList<>(getAssets());
        assetsClone.addAll(that.getAssets());
        List<Asset> mergedAssets = assetsClone
                .stream()
                .collect(Collectors.groupingBy(Asset::getName))
                .entrySet()
                .stream()
                .map(entry -> entry.getValue().stream().reduce(Asset.builder().name(entry.getKey()).value(BigInteger.ZERO).build(), Asset::plus))
                .collect(Collectors.toList());
        return MultiAsset.builder().policyId(getPolicyId()).assets(mergedAssets).build();
    }

    /**
     * Subtracts a Multi Asset from another. If an Asset is already present, subtract the amounts.
     *
     * @param that {@link MultiAsset} to Subtract by
     * @return {@link MultiAsset} as Difference result
     */
    public MultiAsset minus(MultiAsset that) {
        if (!getPolicyId().equals(that.getPolicyId())) {
            throw new IllegalArgumentException("Trying to add MultiAssets with different policyId");
        }
        List<Asset> assetsResult = new ArrayList<>();
        java.util.Map<String, Asset> thatAssetsMap = convertToMap(that.assets);
        for (Asset asset : getAssets()) {
            if (thatAssetsMap.containsKey(asset.getNameAsHex())) {
                assetsResult.add(asset.minus(thatAssetsMap.get(asset.getNameAsHex())));
            } else {
                assetsResult.add(asset);
            }
        }
        return MultiAsset.builder().policyId(getPolicyId()).assets(assetsResult).build();
    }

    private java.util.Map<String, Asset> convertToMap(List<Asset> assets) {
        return assets.stream().collect(Collectors.toMap(Asset::getNameAsHex, Function.identity()));
    }
}

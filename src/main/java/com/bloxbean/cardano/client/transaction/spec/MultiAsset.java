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
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MultiAsset {

    private String policyId;

    @Builder.Default
    private List<Asset> assets = new ArrayList<>();

    public void serialize(Map multiAssetMap) {
        Map assetsMap = new Map();
        for (Asset asset : assets) {
            ByteString assetNameBytes = new ByteString(asset.getNameAsBytes());

            if(asset.getValue().compareTo(BigInteger.ZERO) == 0 || asset.getValue().compareTo(BigInteger.ZERO) == 1) {
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

    public static MultiAsset deserialize(Map multiAssetsMap, DataItem key) {
        MultiAsset multiAsset = new MultiAsset();
        ByteString keyBS = (ByteString) key;
        multiAsset.setPolicyId(HexUtil.encodeHexString(keyBS.getBytes()));

        Map assetsMap = (Map) multiAssetsMap.get(key);
        for(DataItem assetKey: assetsMap.getKeys()) {
            ByteString assetNameBS = (ByteString)assetKey;

            DataItem valueDI = assetsMap.get(assetKey);
            BigInteger value = CborSerializationUtil.getBigInteger(valueDI);

            String name = HexUtil.encodeHexString(assetNameBS.getBytes(), true);
            multiAsset.getAssets().add(new Asset(name, value));
        }
        return multiAsset;
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
     * @param that
     * @return
     */
    public MultiAsset plus(MultiAsset that) {
        if (!getPolicyId().equals(that.getPolicyId())) {
            throw new IllegalArgumentException("Trying to add MultiAssets with different policyId");
        }
        ArrayList<Asset> assets = new ArrayList<>();
        assets.addAll(getAssets());
        assets.addAll(that.getAssets());
        List<Asset> mergedAssets = assets
                .stream()
                .collect(Collectors.groupingBy(Asset::getName))
                .entrySet()
                .stream()
                .map(entry -> entry.getValue().stream().reduce(Asset.builder().name(entry.getKey()).value(BigInteger.ZERO).build(), Asset::plus))
                .collect(Collectors.toList());
        return MultiAsset.builder().policyId(getPolicyId()).assets(mergedAssets).build();
    }

    /**
     * Creates a new list of multi assets from those passed as parameters.
     * Multi Assets with the same policy id will be aggregated together, and matching assets summed.
     * @param multiAssets1
     * @param multiAssets2
     * @return
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
     * Subtracts a Multi Asset from another. If an Asset is already present, subtract the amounts.
     * @param that
     * @return
     */
    private MultiAsset minus(MultiAsset that) {
        if (!getPolicyId().equals(that.getPolicyId())) {
            throw new IllegalArgumentException("Trying to add MultiAssets with different policyId");
        }
        ArrayList<Asset> assets = new ArrayList<>(getAssets());
        assets.removeAll(that.getAssets());
        List<Asset> mergedAssets = assets
                .stream()
                .collect(Collectors.groupingBy(Asset::getName))
                .entrySet()
                .stream()
                .map(entry -> entry.getValue().stream().reduce(Asset.builder().name(entry.getKey()).value(BigInteger.ZERO).build(), Asset::minus))
                .collect(Collectors.toList());
        return MultiAsset.builder().policyId(getPolicyId()).assets(mergedAssets).build();
    }

    /**
     * Creates a new list of multi assets from those passed as parameters.
     * Multi Assets with the same policy id will be aggregated together, and matching assets subtracted.
     * @param multiAssets1
     * @param multiAssets2
     * @return
     */
    public static List<MultiAsset> subtractMultiAssetLists(List<MultiAsset> multiAssets1, List<MultiAsset> multiAssets2) {
        List<MultiAsset> tempMultiAssets = new ArrayList<>();
        if (multiAssets1 != null) {
            tempMultiAssets.addAll(multiAssets1);
        }
        if (multiAssets2 != null) {
            tempMultiAssets.removeAll(multiAssets2);
        }
        return tempMultiAssets
                .stream()
                .collect(Collectors.groupingBy(MultiAsset::getPolicyId))
                .entrySet()
                .stream()
                .map(entry -> entry.getValue().stream().reduce(MultiAsset.builder().policyId(entry.getKey()).assets(Arrays.asList()).build(), MultiAsset::minus))
                .collect(Collectors.toList());
    }
}

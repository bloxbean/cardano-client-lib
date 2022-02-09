package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Value {

    @Builder.Default
    private BigInteger coin = BigInteger.ZERO;
    //Policy Id -> Asset
    @Builder.Default
    private List<MultiAsset> multiAssets = new ArrayList<>();

    public static Value deserialize(Array valueArray) {
        //There can be two elements
        //First one coin

        Value value = new Value();
        List<DataItem> valueDataItems = valueArray.getDataItems();
        if (valueDataItems != null && valueDataItems.size() == 2) {
            DataItem valueDI = valueDataItems.get(0);
            BigInteger coin = CborSerializationUtil.getBigInteger(valueDI);
            value.setCoin(coin);

            Map multiAssetsMap = (Map) valueDataItems.get(1);
            if (multiAssetsMap != null) {
                for (DataItem key : multiAssetsMap.getKeys()) {
                    MultiAsset multiAsset = MultiAsset.deserialize(multiAssetsMap, key);
                    value.getMultiAssets().add(multiAsset);
                }
            }
        }

        return value;
    }

    public HashMap<String, HashMap<String, BigInteger>> toMap() {
        HashMap<String, HashMap<String, BigInteger>> multiAssetsMap = new HashMap<>();
        for (MultiAsset multiAsset : multiAssets) {
            HashMap<String, BigInteger> assets = new HashMap<>();
            for (Asset asset : multiAsset.getAssets()) {
                assets.put(asset.getName(), asset.getValue());
            }
            multiAssetsMap.put(multiAsset.getPolicyId(), assets);
        }
        return multiAssetsMap;
    }

    public Map serialize() throws CborSerializationException {
        Map map = new Map();
        if (multiAssets != null) {
            for (MultiAsset multiAsset : multiAssets) {
                multiAsset.serialize(map);
                /*Map assetsMap = new Map();
                for (Asset asset : multiAsset.getAssets()) {
                    ByteString assetNameBytes = new ByteString(asset.getNameAsBytes());
                    UnsignedInteger value = new UnsignedInteger(asset.getValue());
                    assetsMap.put(assetNameBytes, value);
                }

                ByteString policyIdByte = new ByteString(HexUtil.decodeHexString(multiAsset.getPolicyId()));
                map.put(policyIdByte, assetsMap);*/
            }
        }
        return map;
    }

    /**
     * Sums arbitrary complex values.
     *
     * @param that parameter to sum with
     * @return {@link Value} of the Sum
     */
    public Value plus(Value that) {
        BigInteger sumCoin = (getCoin() == null ? BigInteger.ZERO.add(that.getCoin()) : getCoin().add(that.getCoin()));
        List<MultiAsset> sumMultiAssets = MultiAsset.mergeMultiAssetLists(getMultiAssets(), that.getMultiAssets());
        return Value.builder().coin(sumCoin).multiAssets(sumMultiAssets).build();
    }

    /**
     * Subtracts arbitrary complex values.
     *
     * @param that parameter to subtract by
     * @return {@link Value} Difference
     */
    public Value minus(Value that) {
        BigInteger sumCoin = (getCoin() == null ? BigInteger.ZERO.subtract(that.getCoin()) : getCoin().subtract(that.getCoin()));
        List<MultiAsset> difMultiAssets = MultiAsset.subtractMultiAssetLists(getMultiAssets(), that.getMultiAssets());
        return Value.builder().coin(sumCoin).multiAssets(difMultiAssets).build();
    }
}

package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.common.cbor.custom.SortedMap;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class Value {

    @Builder.Default
    private BigInteger coin = BigInteger.ZERO;
    //Policy Id -> Asset
    @Builder.Default
    private List<MultiAsset> multiAssets = new ArrayList<>();

    /**
     * Deserialize a cbor DataItem to Value
     *
     * @param valueItem
     * @return {@link Value}
     */
    public static Value deserialize(DataItem valueItem) {
        Value value = null;
        if (MajorType.UNSIGNED_INTEGER.equals(valueItem.getMajorType()) || MajorType.NEGATIVE_INTEGER.equals(valueItem.getMajorType())) {
            value = new Value();
            value.setCoin(((Number) valueItem).getValue());
        } else if (MajorType.BYTE_STRING.equals(valueItem.getMajorType())) { //For BigNum. >  2 pow 64 Tag 2
            if (valueItem.getTag().getValue() == 2) {
                value = new Value();
                value.setCoin(new BigInteger(((ByteString) valueItem).getBytes()));
            } else if (valueItem.getTag().getValue() == 3) {
                value = new Value();
                value.setCoin(new BigInteger(((ByteString) valueItem).getBytes()).multiply(BigInteger.valueOf(-1)));
            }
        } else if (MajorType.ARRAY.equals(valueItem.getMajorType())) {
            Array coinAssetArray = (Array) valueItem;
            value = Value.deserializeValueArray(coinAssetArray);
        }

        return value;
    }

    private static Value deserializeValueArray(Array valueArray) {
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

    /**
     * Serialize a {@link Value} object to cbor DataItem
     *
     * @return DataItem
     */
    public DataItem serialize() {
        if (this.getMultiAssets() != null && this.getMultiAssets().size() > 0) {
            Array coinAssetArray = new Array();

            if (this.getCoin() != null) {
                if (this.getCoin().compareTo(BigInteger.ZERO) == 0 || this.getCoin().compareTo(BigInteger.ZERO) == 1) {
                    coinAssetArray.add(new UnsignedInteger(this.getCoin()));
                } else {
                    coinAssetArray.add(new NegativeInteger(this.getCoin()));
                }
            } else {
                coinAssetArray.add(new UnsignedInteger(BigInteger.ZERO));
            }

            Map valueMap = this.serializeMultiAssets();
            coinAssetArray.add(valueMap);
            return coinAssetArray;
        } else {
            DataItem valueDI;
            if (this.getCoin() != null) {
                if (this.getCoin().compareTo(BigInteger.ZERO) == 0 || this.getCoin().compareTo(BigInteger.ZERO) == 1) {
                    valueDI = new UnsignedInteger(this.getCoin());
                } else {
                    valueDI = new NegativeInteger(this.getCoin());
                }
            } else {
                valueDI = new UnsignedInteger(BigInteger.ZERO);
            }
            return valueDI;
        }
    }

    /**
     * Get a {@link java.util.HashMap} of PolicyId to HashMap of assetName and {@link Asset}
     *
     * @return Map
     */
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

    private Map serializeMultiAssets() {
        Map map = new SortedMap();
        if (multiAssets != null) {
            List<MultiAsset> cloneMultiAssets = new ArrayList<>(multiAssets);
            //sorted based on policy id for canonical cbor
            Collections.sort(cloneMultiAssets, (m1, m2) -> m1.getPolicyId().compareTo(m2.getPolicyId()));
            for (MultiAsset multiAsset : cloneMultiAssets) {
                multiAsset.serialize(map);
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

        //Remove all asset with value == 0
        difMultiAssets.forEach(multiAsset ->
                multiAsset.getAssets().removeIf(asset -> BigInteger.ZERO.equals(asset.getValue())));
        //Remove multiasset if there's no asset
        difMultiAssets.removeIf(multiAsset -> multiAsset.getAssets() == null || multiAsset.getAssets().isEmpty());

        return Value.builder().coin(sumCoin).multiAssets(difMultiAssets).build();
    }

    public List<Amount> toAmountList() {
        List<Amount> amounts = new ArrayList<>();
        amounts.add(new Amount(LOVELACE, getCoin()));
        for (MultiAsset multiAsset : getMultiAssets()) {
            String policyId = multiAsset.getPolicyId();
            for (com.bloxbean.cardano.client.transaction.spec.Asset asset : multiAsset.getAssets()) {
                amounts.add(new Amount(policyId + asset.getNameAsHex().replace("0x", ""), asset.getValue()));
            }
        }
        return amounts;
    }
}

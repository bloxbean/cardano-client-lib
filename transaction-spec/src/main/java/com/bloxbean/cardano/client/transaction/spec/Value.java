package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.custom.SortedMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public Value add(Value that) {
        BigInteger sumCoin = (getCoin() == null ? BigInteger.ZERO.add(that.getCoin()) : getCoin().add(that.getCoin()));
        List<MultiAsset> sumMultiAssets = MultiAsset.mergeMultiAssetLists(getMultiAssets(), that.getMultiAssets());
        return Value.builder().coin(sumCoin).multiAssets(sumMultiAssets).build();
    }

    /**
     * Use add(Value that) instead
     * @deprecated
     * <p>Use {@link #add(Value)} instead</p>
     *
     * @param that
     * @return
     */
    @Deprecated(since = "0.6.3")
    public Value plus(Value that) {
        return this.add(that);
    }

    public Value add(String policyId, String assetName, BigInteger amount) {
        return this.add(from(policyId, assetName, amount));
    }

    /**
     * Adds the specified coin(lovelace) amount to the current {@code Value} instance.
     *
     * @param amount The amount in lovelace to be added
     * @return A new {@code Value} instance with the added coin amount.
     */
    public Value addCoin(BigInteger amount) {
        return this.add(fromCoin(amount));
    }

    /**
     * Creates a new Value instance from provided policy ID, asset name, and amount.
     *
     * @param policyId The policy ID associated with the asset.
     * @param assetName The name of the asset.
     * @param amount The amount of the asset.
     * @return A new Value instance containing the provided asset information.
     */
    public static Value from(String policyId, String assetName, BigInteger amount) {
        Objects.requireNonNull(policyId);
        return Value.builder()
                .multiAssets(List.of(MultiAsset.builder()
                        .policyId(policyId)
                        .assets(List.of(Asset.builder().name(assetName).value(amount).build()))
                        .build()))
                .build();
    }

    /**
     * Creates a {@link Value} instance from the given amount of lovelaces.
     *
     * @param coin The amount of lovelaces to be converted into a {@link Value} instance.
     * @return A new {@link Value} instance containing the specified amount of lovelaces.
     */
    public static Value fromCoin(BigInteger coin) {
        return Value.builder().coin(coin).build();
    }

    /**
     * Subtracts arbitrary complex values.
     *
     * @param that parameter to subtract by
     * @return {@link Value} Difference
     */
    public Value subtract(Value that) {
        BigInteger sumCoin = (getCoin() == null ? BigInteger.ZERO.subtract(that.getCoin()) : getCoin().subtract(that.getCoin()));
        List<MultiAsset> difMultiAssets = MultiAsset.subtractMultiAssetLists(getMultiAssets(), that.getMultiAssets());

        List<MultiAsset> filteredMultiAssets = difMultiAssets
                .stream()
                .flatMap(multiAsset -> {
                    List<Asset> assets = multiAsset
                            .getAssets()
                            .stream()
                            .filter(asset -> !asset.getValue().equals(BigInteger.ZERO))
                            .collect(Collectors.toList());
                    if (!assets.isEmpty()) {
                        multiAsset.setAssets(assets);
                        return Stream.of(multiAsset);
                    } else {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());

        return Value.builder().coin(sumCoin).multiAssets(filteredMultiAssets).build();
    }

    /**
     * Use minus(Value that) instead
     * @deprecated
     * <p>Use {@link #subtract(Value)} instead</p>
     *
     * @param that
     * @return
     */
    @Deprecated(since = "0.6.3")
    public Value minus(Value that) {
        return this.subtract(that);
    }


    /**
     * Subtracts the specified coin (lovelace) amount from the current {@code Value} instance.
     *
     * @param amount The amount in lovelace to be subtracted.
     * @return A new {@code Value} instance with the subtracted coin amount.
     */
    public Value substractCoin(BigInteger amount) {
        return this.subtract(fromCoin(amount));
    }

    /**
     * Subtracts a specified amount of an asset from the current {@code Value} instance.
     *
     * @param policyId The policy ID associated with the asset.
     * @param assetName The name of the asset.
     * @param amount The amount of the asset to be subtracted.
     * @return A new {@code Value} instance with the subtracted asset amount.
     */
    public Value subtract(String policyId, String assetName, BigInteger amount) {
        return this.subtract(from(policyId, assetName, amount));
    }

    /**
     * Returns the amount of a specific asset identified by the given policy ID and asset name.
     * If the asset is not found, the method returns BigInteger.ZERO.
     *
     * @param policyId The policy ID corresponding to the asset.
     * @param assetName The name of the asset.
     * @return The amount of the specified asset as a BigInteger, or BigInteger.ZERO if the asset is not found.
     */
    public BigInteger amountOf(String policyId, String assetName) {
        return getMultiAssets()
                .stream()
                .filter(multiAsset -> multiAsset.getPolicyId().equals(policyId))
                .findAny()
                .stream()
                .flatMap(multiAsset -> multiAsset.getAssets().stream().filter(asset -> asset.hasName(assetName)))
                .map(Asset::getValue)
                .findAny()
                .orElse(BigInteger.ZERO);
    }

    /**
     * Determines if the value represented by this instance is zero.
     *
     * @return true if both `multiAssets` is null or empty and `coin` equals to zero, otherwise false.
     */
    public boolean isZero() {
        return (multiAssets == null || multiAssets.isEmpty()) && BigInteger.ZERO.equals(coin);
    }

    /**
     * If the amount for all assets is non-negative
     *
     * @return true if amount for each asset is non negative
     */
    public boolean isPositive() {
        boolean isCoinPositive = coin.signum() >= 0;
        boolean allAssetsPositive = multiAssets == null || multiAssets.isEmpty() ||
                multiAssets.stream().allMatch(multiAsset -> multiAsset.getAssets().stream().allMatch(asset -> asset.getValue().longValue() >= 0));
        return isCoinPositive && allAssetsPositive;
    }

}

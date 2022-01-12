package com.bloxbean.cardano.client.backend.model;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.*;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Utxo {

    private String txHash;
    private int outputIndex;
    private List<Amount> amount;
    private String dataHash;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Utxo utxo = (Utxo) o;
        return outputIndex == utxo.outputIndex && txHash.equals(utxo.txHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txHash, outputIndex);
    }

    public Value toValue() {
        Value value = new Value();
        value.setCoin(getCoin(amount));
        HashMap<String, HashMap<String, BigInteger>> multiAssetsMap = new HashMap<>();
        for (Amount am : amount) {
            Tuple<String, String> tuple = AssetUtil.getPolicyIdAndAssetName(am.getUnit());
            HashMap<String, BigInteger> assets = multiAssetsMap.computeIfAbsent(tuple._1, k -> new HashMap<>());
            assets.put(tuple._2, am.getQuantity());
        }
        List<MultiAsset> multiAssetList = new ArrayList<>();
        for (Map.Entry<String,HashMap<String,BigInteger>> entry : multiAssetsMap.entrySet()) {
            List<Asset> assetList = new ArrayList<>();
            for (Map.Entry<String,BigInteger> asset : entry.getValue().entrySet()) {
                assetList.add(new Asset(asset.getKey(),asset.getValue()));
            }
            multiAssetList.add(new MultiAsset(entry.getKey(),assetList));
        }
        value.setMultiAssets(multiAssetList);
        return value;
    }

    private BigInteger getCoin(List<Amount> amount) {
        BigInteger coin = BigInteger.ZERO;
        for (Amount am : amount) {
            Tuple<String, String> tuple = AssetUtil.getPolicyIdAndAssetName(am.getUnit());
            if (tuple._2.equals(LOVELACE)) {
                coin = coin.add(am.getQuantity());
            }
        }
        return coin;
    }
}

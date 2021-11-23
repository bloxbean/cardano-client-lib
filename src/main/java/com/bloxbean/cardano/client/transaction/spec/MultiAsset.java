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
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MultiAsset {
    private String policyId;
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
}

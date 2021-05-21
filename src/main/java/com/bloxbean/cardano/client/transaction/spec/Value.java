package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
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
public class Value {
    private BigInteger coin;
    //Policy Id -> Asset
    private List<MultiAsset> multiAssets = new ArrayList<>();

    public Map serialize() throws CborException {
        Map map = new Map();
        if(multiAssets != null) {
            for (MultiAsset multiAsset : multiAssets) {
                Map assetsMap = new Map();
                for (Asset asset : multiAsset.getAssets()) {
                    ByteString assetNameBytes = new ByteString(asset.getName() == null? new byte[0] : HexUtil.decodeHexString(asset.getName()));
                    UnsignedInteger value = new UnsignedInteger(asset.getValue());
                    assetsMap.put(assetNameBytes, value);
                }

                ByteString policyIdByte = new ByteString(HexUtil.decodeHexString(multiAsset.getPolicyId()));
                map.put(policyIdByte, assetsMap);
            }
        }
        return map;
    }
}

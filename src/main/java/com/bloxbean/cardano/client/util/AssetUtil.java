package com.bloxbean.cardano.client.util;

import java.nio.ByteBuffer;

public class AssetUtil {

    public static Tuple<String, String> getPolicyIdAndAssetName(String asssetId) {
        byte[] bytes = HexUtil.decodeHexString(asssetId);
        ByteBuffer bb = ByteBuffer.wrap(bytes);

        byte[] policyId = new byte[28];
        byte[] assetName = new byte[bytes.length - 28];

        bb.get(policyId, 0, policyId.length);
        bb.get(assetName, 0, assetName.length);

        return new Tuple<>(HexUtil.encodeHexString(policyId), HexUtil.encodeHexString(assetName));
    }
}

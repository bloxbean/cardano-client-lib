package com.bloxbean.cardano.client.util;

import com.bloxbean.cardano.client.common.Bech32;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash160;

public class AssetUtil {

    /**
     * Get policy id and asset name in hex from asset id
     * @param asssetId
     * @return
     */
    public static Tuple<String, String> getPolicyIdAndAssetName(String asssetId) {
        byte[] bytes = HexUtil.decodeHexString(asssetId);
        ByteBuffer bb = ByteBuffer.wrap(bytes);

        byte[] policyId = new byte[28];
        byte[] assetName = new byte[bytes.length - 28];

        bb.get(policyId, 0, policyId.length);
        bb.get(assetName, 0, assetName.length);

        return new Tuple<>(HexUtil.encodeHexString(policyId), HexUtil.encodeHexString(assetName));
    }

    /**
     * Calculate fingerprint from policy id and asset name (CIP-0014)
     * @param policyIdHex  Policy id
     * @param assetNameHex Asset name in hex
     * @return
     */
    public static String calculateFingerPrint(String policyIdHex, String assetNameHex) {
        String assetId = policyIdHex + assetNameHex;
        byte[] hashBytes = blake2bHash160(HexUtil.decodeHexString(assetId));

        List<Integer> words = convertBits(hashBytes, 8, 5, false);
        byte[] bytes = new byte[words.size()];

        for(int i=0; i < words.size(); i++) {
            bytes[i] = words.get(i).byteValue();
        }

        String hrp = "asset";

       return Bech32.encode(hrp, bytes);
    }

    private static List<Integer> convertBits(byte[] data, int fromWidth, int toWidth, boolean pad) {
        int acc = 0;
        int bits = 0;
        int maxv = (1 << toWidth) - 1;
        List<Integer> ret = new ArrayList<>();

        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xff;
            if (value < 0 || value >> fromWidth != 0) {
                return null;
            }
            acc = (acc << fromWidth) | value;
            bits += fromWidth;
            while (bits >= toWidth) {
                bits -= toWidth;
                ret.add((acc >> bits) & maxv);
            }
        }

        if (pad) {
            if (bits > 0) {
                ret.add((acc << (toWidth - bits)) & maxv);
            } else if (bits >= fromWidth || ((acc << (toWidth - bits)) & maxv) != 0) {
                return null;
            }
        }

        return ret;
    }
}

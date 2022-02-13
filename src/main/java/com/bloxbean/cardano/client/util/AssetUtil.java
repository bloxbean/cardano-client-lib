package com.bloxbean.cardano.client.util;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash160;

public class AssetUtil {

    /**
     * Get policy id and asset name in hex from asset id
     * Policy id is returned without hex prefix (0x)
     * Asset name is returned with hex prefix (0x)
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

        //Add hex prefix to asset name as it's required by Asset class
        return new Tuple<>(HexUtil.encodeHexString(policyId), HexUtil.encodeHexString(assetName, true));
    }

    /**
     * Calculate fingerprint from policy id and asset name (CIP-0014)
     * @param policyIdHex  Policy id
     * @param assetNameHex Asset name in hex
     * @return
     */
    public static String calculateFingerPrint(String policyIdHex, String assetNameHex) {
        if(assetNameHex.startsWith("0x"))
            assetNameHex = assetNameHex.substring(2);
        if(policyIdHex.startsWith("0x"))
            policyIdHex = policyIdHex.substring(2);

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


    /**
     * Get unit name from policy id and asset name
     * @param policyId
     * @param asset
     * @return unit name
     */
    public static String getUnit(String policyId, Asset asset) {
        return policyId + HexUtil.encodeHexString(asset.getNameAsBytes());
    }

    /**
     * Create a <code>{@link MultiAsset}</code> from unit and qty
     * @param unit unit of the asset (policy id + asset name)
     * @param qty value
     * @return <code>MultiAsset</code>
     */
    public static MultiAsset getMultiAssetFromUnitAndAmount(String unit, BigInteger qty) {
        Objects.requireNonNull(unit);

        Tuple<String, String> tuple = getPolicyIdAndAssetName(unit);

        return MultiAsset.builder()
                .policyId(tuple._1)
                .assets(List.of(
                        Asset.builder()
                                .name(tuple._2)
                                .value(qty)
                                .build()
                )).build();
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

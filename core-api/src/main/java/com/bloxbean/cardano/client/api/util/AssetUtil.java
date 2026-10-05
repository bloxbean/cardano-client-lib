package com.bloxbean.cardano.client.api.util;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash160;

public class AssetUtil {
    /** A policy id is 28 bytes: 56 hex characters. */
    private static final int POLICY_ID_HEX_LENGTH = 56;

    /** The ledger's upper bound on an asset name: 32 bytes, 64 hex characters. */
    private static final int MAX_ASSET_NAME_HEX_LENGTH = 64;

    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]*");

    /**
     * Get policy id and asset name in hex from asset id
     * Policy id is returned without hex prefix (0x)
     * Asset name is returned with hex prefix (0x)
     *
     * @param assetId assetId
     * @return Tuple Of PolicyId and Asset Name
     */
    public static Tuple<String, String> getPolicyIdAndAssetName(String assetId) {
        byte[] bytes = HexUtil.decodeHexString(assetId);
        ByteBuffer bb = ByteBuffer.wrap(bytes);

        byte[] policyId = new byte[28];
        byte[] assetName = new byte[bytes.length - 28];

        bb.get(policyId, 0, policyId.length);
        bb.get(assetName, 0, assetName.length);

        //Add hex prefix to asset name as it's required by Asset class
        return new Tuple<>(HexUtil.encodeHexString(policyId), HexUtil.encodeHexString(assetName, true));
    }

    /**
     * Validate a unit and return its canonical form, for code that compares or looks up units as
     * strings.
     *
     * <p>A unit is either {@code "lovelace"} or a native-asset unit: the 28-byte policy id followed
     * by the asset name's bytes (0 to 32 bytes), in hexadecimal with an optional lowercase
     * {@code 0x} prefix. The canonical form of a native-asset unit is lowercase hex without a
     * prefix, which is the form backends report, so every spelling of one asset normalizes to the
     * same string. {@code "lovelace"} is returned unchanged; only that exact spelling is lovelace.</p>
     *
     * <p>This is stricter than {@link #getPolicyIdAndAssetName(String)}, which decodes any
     * even-length hex string without checking the asset-name length, and fails with a low-level
     * exception on a unit too short to hold a policy id. Use this method to validate a unit that
     * comes from user input. For every unit it accepts, both methods agree on the policy id and the
     * asset name.</p>
     *
     * <pre>{@code
     * // policyId is a 56-character lowercase hex policy id
     * normalizeUnit("lovelace")                  -> "lovelace"
     * normalizeUnit(policyId)                    -> policyId              // empty asset name
     * normalizeUnit(policyId.toUpperCase())      -> policyId
     * normalizeUnit("0x" + policyId + "546F6B")  -> policyId + "546f6b"
     * normalizeUnit(policyId + "zz")             -> IllegalArgumentException
     * }</pre>
     *
     * @param unit {@code "lovelace"}, or a policy id followed by an asset name in hexadecimal,
     *             optionally prefixed with {@code 0x}
     * @return {@code "lovelace"}, or the unit in lowercase hex without a prefix
     * @throws IllegalArgumentException if {@code unit} is null, is not hexadecimal, has an odd
     *                                  number of hex digits, is shorter than a policy id, or has
     *                                  an asset name longer than 32 bytes
     */
    public static String normalizeUnit(String unit) {
        if (LOVELACE.equals(unit)) return LOVELACE;

        String hex = unit != null && unit.startsWith("0x") ? unit.substring(2) : unit;
        if (hex == null || hex.length() < POLICY_ID_HEX_LENGTH
                || hex.length() > POLICY_ID_HEX_LENGTH + MAX_ASSET_NAME_HEX_LENGTH
                || hex.length() % 2 != 0 || !HEX.matcher(hex).matches())
            throw new IllegalArgumentException("Invalid unit '" + unit + "': expected \"lovelace\""
                    + " or a 56-character hex policy id followed by the asset name's bytes in hex"
                    + " (at most 32 bytes)");
        return hex.toLowerCase(Locale.ROOT);
    }

    /**
     * The policy id of a unit, or null for {@code "lovelace"}.
     *
     * <p>The unit is validated as {@link #normalizeUnit(String)} validates it, and the policy id is
     * returned in lowercase hex without a prefix. A unit exactly as long as a policy id names the
     * policy's empty asset name, so its policy id is the whole unit.</p>
     *
     * @param unit {@code "lovelace"}, or a policy id followed by an asset name in hexadecimal
     * @return the policy id, or null for {@code "lovelace"}
     * @throws IllegalArgumentException if {@code unit} is not a valid unit; see
     *                                  {@link #normalizeUnit(String)}
     */
    public static String getPolicyId(String unit) {
        String normalized = normalizeUnit(unit);
        return LOVELACE.equals(normalized) ? null : normalized.substring(0, POLICY_ID_HEX_LENGTH);
    }

    /**
     * Calculate fingerprint from policy id and asset name (CIP-0014)
     *
     * @param policyIdHex  Policy id
     * @param assetNameHex Asset name in hex
     * @return
     */
    public static String calculateFingerPrint(String policyIdHex, String assetNameHex) {
        if (assetNameHex.startsWith("0x"))
            assetNameHex = assetNameHex.substring(2);
        if (policyIdHex.startsWith("0x"))
            policyIdHex = policyIdHex.substring(2);

        String assetId = policyIdHex + assetNameHex;
        byte[] hashBytes = blake2bHash160(HexUtil.decodeHexString(assetId));

        List<Integer> words = convertBits(hashBytes, 8, 5, false);
        byte[] bytes = new byte[words.size()];

        for (int i = 0; i < words.size(); i++) {
            bytes[i] = words.get(i).byteValue();
        }

        String hrp = "asset";

        return Bech32.encode(hrp, bytes);
    }


    /**
     * Get unit name from policy id and asset name
     *
     * @param policyId
     * @param asset
     * @return unit name
     */
    public static String getUnit(String policyId, Asset asset) {
        return policyId + HexUtil.encodeHexString(asset.getNameAsBytes());
    }

    /**
     * Get unit name from policy id and asset name
     * @param policyId policy id
     * @param assetName asset name
     * @return unit name
     */
    public static String getUnit(String policyId, String assetName) {
        Asset asset = new Asset(assetName, BigInteger.ZERO);
        return policyId + HexUtil.encodeHexString(asset.getNameAsBytes());
    }

    /**
     * Create a <code>{@link MultiAsset}</code> from unit and qty
     *
     * @param unit unit of the asset (policy id + asset name)
     * @param qty  value
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

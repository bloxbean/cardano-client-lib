package com.bloxbean.cardano.client.cip.cip102;

import com.bloxbean.cardano.client.cip.cip67.CIP67AssetNameUtil;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Represents a CIP-102 Royalty Token — an NFT with the CIP-67 label {@code 500} and
 * name {@code "Royalty"}, optionally suffixed with a decimal integer.
 *
 * <p>Asset name format: {@code <CIP67-prefix-for-500> || "Royalty" || [postfix digits]}
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code (500)Royalty} — no postfix, compatible with version 1 and 2 datums</li>
 *   <li>{@code (500)Royalty1} — postfix 1, requires {@code version = 2} in the datum</li>
 *   <li>{@code (500)Royalty2} — postfix 2, requires {@code version = 2} in the datum</li>
 * </ul>
 *
 * <p>The CIP-67 prefix for label 500 encodes to hex {@code 001f4d70} followed by the
 * UTF-8 bytes of the name.
 */
public class RoyaltyToken {

    /** CIP-67 label used for royalty tokens. */
    public static final int ROYALTY_TOKEN_LABEL = 500;
    /** Base name component of the royalty token asset name, without any numeric postfix. */
    public static final String ROYALTY_TOKEN_BASE_NAME = "Royalty";

    private final byte[] nameBytes;

    private RoyaltyToken(byte[] nameBytes) {
        this.nameBytes = nameBytes;
    }

    /**
     * Creates a royalty token with no postfix: {@code (500)Royalty}.
     * Compatible with both version 1 and version 2 datums.
     *
     * @return a {@link RoyaltyToken} with name {@code (500)Royalty}
     */
    public static RoyaltyToken create() {
        return new RoyaltyToken(ROYALTY_TOKEN_BASE_NAME.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a royalty token with the given numeric postfix: {@code (500)Royalty{postfix}}.
     * The corresponding datum must use {@code version = 2}.
     *
     * @param postfix positive integer identifying this royalty policy within the collection
     * @return a {@link RoyaltyToken} with name {@code (500)Royalty{postfix}}
     * @throws IllegalArgumentException if postfix is less than 1
     */
    public static RoyaltyToken create(int postfix) {
        if (postfix < 1)
            throw new IllegalArgumentException("Royalty token postfix must be >= 1");
        String name = ROYALTY_TOKEN_BASE_NAME + postfix;
        return new RoyaltyToken(name.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the full asset name as a hex string (CIP-67 label prefix concatenated
     * with the UTF-8 name bytes), prefixed with {@code "0x"}.
     *
     * @return hex-encoded asset name prefixed with {@code "0x"}
     */
    public String getAssetNameAsHex() {
        byte[] prefix = CIP67AssetNameUtil.labelToPrefix(ROYALTY_TOKEN_LABEL);
        return "0x" + HexUtil.encodeHexString(BytesUtil.merge(prefix, nameBytes));
    }

    /**
     * Returns the full asset name as a byte array (CIP-67 label prefix concatenated
     * with the UTF-8 name bytes).
     *
     * @return raw asset name bytes
     */
    public byte[] getAssetNameAsBytes() {
        byte[] prefix = CIP67AssetNameUtil.labelToPrefix(ROYALTY_TOKEN_LABEL);
        return BytesUtil.merge(prefix, nameBytes);
    }

    /**
     * Returns an {@link Asset} representing this royalty token with quantity 1.
     *
     * @return asset with this token's name and quantity 1
     */
    public Asset getAsset() {
        return new Asset(getAssetNameAsHex(), BigInteger.ONE);
    }

    /**
     * Returns the human-readable asset name, e.g. {@code "(500)Royalty"} or
     * {@code "(500)Royalty2"}.
     *
     * @return friendly asset name in the format {@code "({label}){name}"}
     */
    public String getFriendlyName() {
        return String.format("(%d)%s", ROYALTY_TOKEN_LABEL, new String(nameBytes, StandardCharsets.UTF_8));
    }
}

package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * Binary-safe programmable-token asset quantity used by semantic mint and burn intents.
 *
 * <p>{@code name} is the raw asset-name bytes as hexadecimal: no {@code 0x} prefix, even length,
 * at most 32 decoded bytes. It is stored and serialized in lowercase, so two plans naming the
 * same asset serialize identically.</p>
 */
@Data
@NoArgsConstructor
public class ProgrammableTokenAsset {
    private static final int MAX_ASSET_NAME_BYTES = 32;

    /** Asset-name bytes encoded as lowercase hexadecimal without a {@code 0x} prefix. */
    private String name;
    private BigInteger quantity;

    public ProgrammableTokenAsset(String name, BigInteger quantity) {
        setName(name);
        this.quantity = quantity;
    }

    public static ProgrammableTokenAsset from(Asset asset) {
        return new ProgrammableTokenAsset(
                HexUtil.encodeHexString(asset.getNameAsBytes()), asset.getValue());
    }

    /** Canonical form: lowercase, so equal names compare and serialize equal. */
    public void setName(String name) {
        this.name = name == null ? null : name.toLowerCase();
    }

    public Asset toLedgerAsset() {
        return new Asset("0x" + name, quantity);
    }

    /**
     * Validate this entry for one semantic operation, naming the operation and the entry's
     * position so the error points at the plan rather than at a lower-level constructor.
     */
    public void validate(String operation, int index) {
        String entry = operation + " assets[" + index + "]";
        if (name == null)
            throw new IllegalStateException(entry + ": name is required (asset-name bytes as hex)");
        if (name.startsWith("0x"))
            throw new IllegalStateException(entry + ": name must be raw hex without a 0x prefix");
        if (name.length() % 2 != 0 || !name.matches("[0-9a-f]*"))
            throw new IllegalStateException(entry + ": name must be even-length hexadecimal, got '"
                    + name + "'");
        if (name.length() > MAX_ASSET_NAME_BYTES * 2)
            throw new IllegalStateException(entry + ": name decodes to " + name.length() / 2
                    + " bytes; an asset name is at most " + MAX_ASSET_NAME_BYTES + " bytes");
        if (quantity == null)
            throw new IllegalStateException(entry + ": quantity is required");
    }
}

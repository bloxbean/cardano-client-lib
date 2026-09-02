package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/** Binary-safe programmable-token asset quantity used by semantic mint and burn intents. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgrammableTokenAsset {
    /** Asset-name bytes encoded as lowercase hexadecimal without a {@code 0x} prefix. */
    private String name;
    private BigInteger quantity;

    public static ProgrammableTokenAsset from(Asset asset) {
        return new ProgrammableTokenAsset(
                HexUtil.encodeHexString(asset.getNameAsBytes()), asset.getValue());
    }

    public Asset toLedgerAsset() {
        return new Asset("0x" + name, quantity);
    }
}

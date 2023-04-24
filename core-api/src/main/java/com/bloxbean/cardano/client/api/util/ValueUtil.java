package com.bloxbean.cardano.client.api.util;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Utility class for {@link Value}
 */
public class ValueUtil {

    /**
     * Converts {@link Value} to {@link Amount} list
     * @param value {@link Value}
     * @return {@link Amount} list
     */
    public static List<Amount> toAmountList(@NonNull Value value) {
        List<Amount> amounts = new ArrayList<>();
        amounts.add(new Amount(LOVELACE, value.getCoin()));
        for (MultiAsset multiAsset : value.getMultiAssets()) {
            String policyId = multiAsset.getPolicyId();
            for (com.bloxbean.cardano.client.transaction.spec.Asset asset : multiAsset.getAssets()) {
                amounts.add(new Amount(policyId + asset.getNameAsHex().replace("0x", ""), asset.getValue()));
            }
        }
        return amounts;
    }
}

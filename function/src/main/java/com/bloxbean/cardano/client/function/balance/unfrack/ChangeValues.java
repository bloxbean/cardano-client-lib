package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import co.nstant.in.cbor.CborException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Value helpers shared by unfrack strategies.
 */
final class ChangeValues {

    private ChangeValues() {
    }

    static BigInteger coinOf(Value value) {
        return value.getCoin() == null ? BigInteger.ZERO : value.getCoin();
    }

    /**
     * Copy of the policies without zero-quantity assets and without empty policies.
     */
    static List<MultiAsset> nonEmptyPolicies(List<MultiAsset> multiAssets) {
        List<MultiAsset> result = new ArrayList<>();
        if (multiAssets == null)
            return result;
        for (MultiAsset ma : multiAssets) {
            if (ma.getAssets() == null)
                continue;
            List<Asset> assets = new ArrayList<>();
            for (Asset asset : ma.getAssets()) {
                if (asset.getValue() != null && asset.getValue().signum() != 0)
                    assets.add(new Asset(asset.getName(), asset.getValue()));
            }
            if (!assets.isEmpty())
                result.add(new MultiAsset(ma.getPolicyId(), assets));
        }
        return result;
    }

    static boolean hasNegativeAsset(List<MultiAsset> policies) {
        return policies.stream()
                .flatMap(ma -> ma.getAssets().stream())
                .anyMatch(asset -> asset.getValue().signum() < 0);
    }

    /**
     * Value as "lovelace" / "policyId.assetNameHex" to quantity, without zero quantities.
     */
    static Map<String, BigInteger> toUnitMap(Value value) {
        Map<String, BigInteger> units = new HashMap<>();
        BigInteger coin = coinOf(value);
        if (coin.signum() != 0)
            units.put("lovelace", coin);
        if (value.getMultiAssets() != null) {
            for (MultiAsset ma : value.getMultiAssets()) {
                for (Asset asset : ma.getAssets()) {
                    if (asset.getValue().signum() != 0)
                        units.merge(ma.getPolicyId() + "." + asset.getNameAsHex(), asset.getValue(), BigInteger::add);
                }
            }
        }
        units.values().removeIf(qty -> qty.signum() == 0);
        return units;
    }

    static Map<String, BigInteger> sumUnits(List<Value> values) {
        Map<String, BigInteger> sum = new HashMap<>();
        for (Value value : values)
            toUnitMap(value).forEach((unit, qty) -> sum.merge(unit, qty, BigInteger::add));
        sum.values().removeIf(qty -> qty.signum() == 0);
        return sum;
    }

    /**
     * CBOR size in bytes of the given policies as a multi-asset value.
     */
    static int serializedSize(List<MultiAsset> policies) {
        try {
            return CborSerializationUtil.serialize(new Value(BigInteger.ZERO, policies).serialize()).length;
        } catch (CborException e) {
            throw new CborRuntimeException("Unable to serialize multi assets", e);
        }
    }
}

package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * One bundle per policy, split into chunks of at most {@code bundleSize} assets. Policies are never mixed.
 * Same as Evolution SDK.
 */
@Getter
@ToString
public class PolicyBundling implements TokenBundlingStrategy {
    public static final int DEFAULT_BUNDLE_SIZE = 10;

    private final int bundleSize;

    public PolicyBundling() {
        this(DEFAULT_BUNDLE_SIZE);
    }

    public PolicyBundling(int bundleSize) {
        if (bundleSize < 1)
            throw new IllegalArgumentException("bundleSize must be >= 1");
        this.bundleSize = bundleSize;
    }

    @Override
    public List<List<MultiAsset>> bundle(List<MultiAsset> tokens) {
        List<List<MultiAsset>> bundles = new ArrayList<>();
        for (MultiAsset policy : tokens) {
            List<Asset> assets = policy.getAssets();
            for (int i = 0; i < assets.size(); i += bundleSize) {
                List<Asset> chunk = new ArrayList<>(assets.subList(i, Math.min(i + bundleSize, assets.size())));
                bundles.add(List.of(new MultiAsset(policy.getPolicyId(), chunk)));
            }
        }
        return bundles;
    }
}

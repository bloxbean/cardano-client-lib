package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Packs tokens into bundles by serialized size instead of token count.
 * <ul>
 *     <li>A policy that fits into {@code maxBundleBytes} is kept whole; a larger one is split into chunks that fit.</li>
 *     <li>Chunks are packed first-fit decreasing, so small policies share a bundle instead of taking one output
 *     each.</li>
 * </ul>
 * The size of a bundle is estimated as the sum of its chunks' CBOR sizes, which slightly overestimates the real
 * size. A single asset larger than the budget gets its own bundle.
 */
@Getter
@ToString
public class ByteBudgetBundling implements TokenBundlingStrategy {
    public static final int DEFAULT_MAX_BUNDLE_BYTES = 1000;

    private final int maxBundleBytes;

    public ByteBudgetBundling() {
        this(DEFAULT_MAX_BUNDLE_BYTES);
    }

    public ByteBudgetBundling(int maxBundleBytes) {
        if (maxBundleBytes < 1)
            throw new IllegalArgumentException("maxBundleBytes must be >= 1");
        this.maxBundleBytes = maxBundleBytes;
    }

    @Override
    public List<List<MultiAsset>> bundle(List<MultiAsset> tokens) {
        List<Chunk> chunks = new ArrayList<>();
        for (MultiAsset policy : tokens)
            chunks.addAll(chunksOf(policy));

        // Stable sort: equal sizes keep input order, so the result is deterministic
        chunks.sort(Comparator.comparingInt((Chunk c) -> c.size).reversed());

        List<Bin> bins = new ArrayList<>();
        for (Chunk chunk : chunks) {
            Bin target = null;
            for (Bin bin : bins) {
                if (bin.size + chunk.size <= maxBundleBytes) {
                    target = bin;
                    break;
                }
            }
            if (target == null) {
                target = new Bin();
                bins.add(target);
            }
            target.add(chunk);
        }

        List<List<MultiAsset>> bundles = new ArrayList<>(bins.size());
        for (Bin bin : bins)
            bundles.add(bin.policies);
        return bundles;
    }

    /**
     * Split a policy into chunks that fit into the budget, keeping asset order.
     */
    private List<Chunk> chunksOf(MultiAsset policy) {
        int size = ChangeValues.serializedSize(List.of(policy));
        if (size <= maxBundleBytes)
            return List.of(new Chunk(policy, size));

        List<Chunk> chunks = new ArrayList<>();
        List<Asset> current = new ArrayList<>();
        for (Asset asset : policy.getAssets()) {
            current.add(asset);
            if (current.size() > 1 && sizeOf(policy.getPolicyId(), current) > maxBundleBytes) {
                current.remove(current.size() - 1);
                chunks.add(chunk(policy.getPolicyId(), current));
                current = new ArrayList<>();
                current.add(asset);
            }
        }
        chunks.add(chunk(policy.getPolicyId(), current));
        return chunks;
    }

    private static Chunk chunk(String policyId, List<Asset> assets) {
        MultiAsset multiAsset = new MultiAsset(policyId, new ArrayList<>(assets));
        return new Chunk(multiAsset, ChangeValues.serializedSize(List.of(multiAsset)));
    }

    private static int sizeOf(String policyId, List<Asset> assets) {
        return ChangeValues.serializedSize(List.of(new MultiAsset(policyId, assets)));
    }

    private static class Chunk {
        final MultiAsset policy;
        final int size;

        Chunk(MultiAsset policy, int size) {
            this.policy = policy;
            this.size = size;
        }
    }

    private static class Bin {
        final List<MultiAsset> policies = new ArrayList<>();
        int size;

        void add(Chunk chunk) {
            // Merge chunks of the same policy, an output must not repeat a policy id
            MultiAsset existing = policies.stream()
                    .filter(p -> p.getPolicyId().equals(chunk.policy.getPolicyId()))
                    .findFirst()
                    .orElse(null);
            if (existing != null)
                existing.getAssets().addAll(chunk.policy.getAssets());
            else
                policies.add(new MultiAsset(chunk.policy.getPolicyId(), new ArrayList<>(chunk.policy.getAssets())));
            size += chunk.size;
        }
    }
}

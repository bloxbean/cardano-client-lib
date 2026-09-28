package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.MultiAsset;

import java.util.List;

/**
 * Groups change tokens into bundles. Each bundle becomes one change output.
 * <p>
 * Every asset of the input must appear in exactly one bundle, with its full quantity.
 */
@FunctionalInterface
public interface TokenBundlingStrategy {

    /**
     * @param tokens non-empty policies with positive quantities
     * @return bundles, each a list of policies
     */
    List<List<MultiAsset>> bundle(List<MultiAsset> tokens);

    /**
     * Whether a UTxO holding these tokens is a fragment that consolidation should merge. A full bundle is not a
     * fragment, so consolidation doesn't move it again and again. Default: always a fragment.
     *
     * @param tokens tokens of a UTxO
     */
    default boolean isFragment(List<MultiAsset> tokens) {
        return true;
    }
}

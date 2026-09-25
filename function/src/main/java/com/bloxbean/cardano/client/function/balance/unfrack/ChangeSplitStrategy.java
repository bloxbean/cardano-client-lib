package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.Value;

import java.util.List;

/**
 * Algorithm used by {@link Unfrack} to split one change value into several change outputs.
 * <p>
 * The returned values must sum up exactly to the request's {@code change} and each value must meet min-ada
 * at the change address. A single-element result means "do not split". {@link Unfrack} verifies both rules.
 */
@FunctionalInterface
public interface ChangeSplitStrategy {

    List<Value> split(ChangeSplitRequest request);
}

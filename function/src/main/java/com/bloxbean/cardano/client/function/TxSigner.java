package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.Objects;

/**
 * Function to provide signing capability.
 */
@FunctionalInterface
public interface TxSigner {
    /**
     * Apply this function to sign a transaction
     *
     * @param context {@link TxBuilderContext}
     * @param transaction {@link Transaction} to sign
     * @return a signed transaction
     */
    Transaction sign(TxBuilderContext context, Transaction transaction);

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     */
    default TxSigner andThen(TxSigner after) {
        Objects.requireNonNull(after);
        return (context, transaction) -> after.sign(context, sign(context, transaction));
    }
}

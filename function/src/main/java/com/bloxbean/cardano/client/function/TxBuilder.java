package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.Objects;

/**
 * Represents a function to transform a {@link Transaction} object
 */
@FunctionalInterface
public interface TxBuilder {

    /**
     * Transform the transaction object
     *
     * @param context TransactionBuilderContext
     * @param txn     Transaction to transform
     * @exception  TxBuildException
     * @deprecated Use {@link TxBuilder#apply(TxBuilderContext, Transaction)}
     */
    @Deprecated
    default void build(TxBuilderContext context, Transaction txn) {
        apply(context, txn);
    }

    /**
     * Transform the transaction object
     *
     * @param context TransactionBuilderContext
     * @param txn     Transaction to transform
     * @exception  TxBuildException
     */
    void apply(TxBuilderContext context, Transaction txn);

    /**
     * Returns a composed function that first applies this function to its input, and then applies the after function to the result.
     * If evaluation of either function throws an exception, it is relayed to the caller of the composed function.
     *
     * @param after
     * @return
     */
    default TxBuilder andThen(TxBuilder after) {
        Objects.requireNonNull(after);

        return (ctx, txn) -> {
            apply(ctx, txn);
            after.apply(ctx, txn);
        };
    }

}

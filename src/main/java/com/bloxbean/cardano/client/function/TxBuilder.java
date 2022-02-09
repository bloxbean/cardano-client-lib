package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.Objects;

/**
 * Represents a consumer function to transform a {@link Transaction} object
 */
@FunctionalInterface
public interface TxBuilder {

    /**
     * Transform the transaction object
     *
     * @param context TransactionBuilderContext
     * @param txn     Transaction to transform
     * @throws TxBuildException
     * @throws ApiException
     */
    void accept(TxBuilderContext context, Transaction txn) throws TxBuildException, ApiException;

    /**
     * Returns a composed function that first applies this function to its input, and then applies the after function to the result.
     * If evaluation of either function throws an exception, it is relayed to the caller of the composed function.
     *
     * @param after
     * @return
     */
    default TxBuilder andThen(TxBuilder after) {
        Objects.requireNonNull(after);

        return (l, r) -> {
            accept(l, r);
            after.accept(l, r);
        };
    }

}

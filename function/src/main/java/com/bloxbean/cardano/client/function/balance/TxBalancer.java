package com.bloxbean.cardano.client.function.balance;

import com.bloxbean.cardano.client.function.TxBuilder;

/**
 * Pluggable strategy which reshapes the change outputs of a transaction before the transaction is balanced.
 * <p>
 * The returned {@link TxBuilder} is invoked after inputs have been selected and change outputs
 * ({@link com.bloxbean.cardano.client.transaction.spec.ChangeOutput}) have been created, but before fee calculation,
 * min-ada adjustment and script cost evaluation. At this point each change output still holds the full surplus,
 * so a balancer may split it into multiple outputs. Fee is later deducted from the change output with the
 * largest lovelace amount at the fee payer address.
 * <p>
 * Implementations must preserve value: the sum of the new outputs must equal the sum of the replaced outputs.
 */
public interface TxBalancer {

    /**
     * @return a {@link TxBuilder} function which reshapes change outputs before balancing
     */
    TxBuilder preBalance();
}

package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.Tuple;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Represents a function to build inputs from required outputs
 * It takes a list of {@link TransactionOutput} as input to return a list of {@link TransactionInput} and any additional {@link TransactionOutput} as change
 */
@FunctionalInterface
public interface TxInputBuilder extends BiFunction<TxBuilderContext, List<TransactionOutput>, Tuple<List<TransactionInput>, List<TransactionOutput>>> {

}

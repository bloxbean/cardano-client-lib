package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;

import java.math.BigInteger;
import java.util.function.BiFunction;

/**
 * Represents a function which takes two inputs {@link TxBuilderContext} and {@link TransactionOutput} and checks
 * against minimum required Ada to return additional required lovelace in the output
 */
@FunctionalInterface
public interface MinAdaChecker extends BiFunction<TxBuilderContext, TransactionOutput, BigInteger> {

}

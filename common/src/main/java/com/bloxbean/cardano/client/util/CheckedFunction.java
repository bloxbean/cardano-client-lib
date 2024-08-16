package com.bloxbean.cardano.client.util;

/**
 * A functional interface that represents a function which may throw a checked exception.
 * This is useful in scenarios where you want to use lambda expressions or method references
 * that throw checked exceptions, particularly in stream operations or other functional contexts.
 *
 * @param <T> The type of the input to the function.
 */
@FunctionalInterface
public interface CheckedFunction<T> {
    T apply() throws Exception;
}

package com.bloxbean.cardano.client.util;

import java.util.function.Supplier;

/**
 * A utility class that encapsulates a computation that may either result in a successful value
 * or throw an exception. This class provides a way to handle checked exceptions in a functional
 * style, allowing the use of such methods in stream operations without needing to use try-catch blocks.
 *
 * @param <T> The type of the result of the computation.
 */
public class Try<T> {
    private final T result;
    private final Exception exception;

    /**
     * Private constructor to initialize a Try instance.
     *
     * @param result The result of the computation, or null if an exception occurred.
     * @param exception The exception thrown by the computation, or null if the computation was successful.
     */
    private Try(T result, Exception exception) {
        this.result = result;
        this.exception = exception;
    }

    /**
     * Executes the provided CheckedFunction, capturing any thrown exceptions.
     *
     * @param supplier A function that produces a result, potentially throwing an exception.
     * @param <T> The type of the result produced by the function.
     * @return A Try instance representing either a successful result or a captured exception.
     */
    public static <T> Try<T> of(CheckedFunction<T> supplier) {
        try {
            return new Try<>(supplier.apply(), null);
        } catch (Exception e) {
            return new Try<>(null, e);
        }
    }

    /**
     * Checks whether the computation was successful.
     *
     * @return true if the computation was successful (i.e., no exception was thrown), false otherwise.
     */
    public boolean isSuccess() {
        return exception == null;
    }

    /**
     * Returns the result of the computation, if it was successful.
     *
     * @return The result of the computation.
     * @throws RuntimeException if the computation resulted in an exception.
     */
    public T get() {
        if (exception != null) {
            throw new RuntimeException(exception);
        }
        return result;
    }

    /**
     * Returns the result of the computation if successful, or the specified default value
     * if an exception occurred during the computation.
     *
     * @param defaultValue The value to return if the computation resulted in an exception.
     * @return The result of the computation, or the provided default value if an exception occurred.
     */
    public T getOrElse(T defaultValue) {
        if (exception != null) {
            return defaultValue;
        }
        return result;
    }

    /**
     * Returns the result of the computation if successful, or throws the specified exception
     * if an exception was thrown during the computation.
     *
     * @param exceptionSupplier A Supplier that provides the exception to be thrown if
     *                          the computation resulted in an exception.
     * @return The result of the computation.
     * @throws RuntimeException The exception provided by the exceptionSupplier if the
     *                          computation resulted in an exception.
     */
    public T orElseThrow(Supplier<? extends RuntimeException> exceptionSupplier) {
        if (exception != null) {
            throw exceptionSupplier.get();
        }
        return result;
    }

    /**
     * Returns the exception that was thrown during the computation, if any.
     *
     * @return The exception thrown by the computation, or null if the computation was successful.
     */
    public Exception getException() {
        return exception;
    }
}

package com.bloxbean.cardano.client.util;

import java.util.Optional;

/**
 * A generic wrapper class that encapsulates the result of an operation, which can either be a successful value or an error.
 *
 * <p>This class provides a way to handle operations that might return a result or throw an exception, without
 * immediately throwing the exception. Instead, the result or error is stored in the `ResultWrapper`, allowing
 * the caller to decide how to handle the success or failure.</p>
 *
 * @param <T> The type of the result value.
 */
public class ResultWrapper<T> {
    private final Optional<T> value;
    private final Exception error;

    /**
     * Private constructor to create a ResultWrapper instance.
     *
     * @param value the value of the result, wrapped in an Optional.
     * @param error the exception that occurred, if any.
     */
    private ResultWrapper(Optional<T> value, Exception error) {
        this.value = value;
        this.error = error;
    }

    /**
     * Creates a ResultWrapper representing a successful result.
     *
     * @param value the result value.
     * @param <T> the type of the result.
     * @return a ResultWrapper containing the result value.
     */
    public static <T> ResultWrapper<T> success(T value) {
        return new ResultWrapper<>(Optional.ofNullable(value), null);
    }

    /**
     * Creates a ResultWrapper representing a failure due to an exception.
     *
     * @param error the exception that occurred.
     * @param <T> the type of the result that would have been returned on success.
     * @return a ResultWrapper containing the exception.
     */
    public static <T> ResultWrapper<T> failure(Exception error) {
        return new ResultWrapper<>(Optional.empty(), error);
    }

    /**
     * Returns the result value, if present.
     *
     * @return an Optional containing the result value, or an empty Optional if the operation failed.
     */
    public Optional<T> getValue() {
        return value;
    }

    /**
     * Returns the exception that occurred, if any.
     *
     * @return an Optional containing the exception, or an empty Optional if the operation was successful.
     */
    public Optional<Exception> getError() {
        return Optional.ofNullable(error);
    }

    /**
     * Checks if the operation was successful.
     *
     * @return true if the result is present (indicating success), false otherwise.
     */
    public boolean isSuccess() {
        return value.isPresent();
    }

    /**
     * Checks if the operation resulted in a failure.
     *
     * @return true if an error occurred, false otherwise.
     */
    public boolean isFailure() {
        return error != null;
    }
}


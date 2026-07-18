package com.bloxbean.cardano.client.txflow.internal;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Shared duration syntax codec for legacy and portable TxFlow documents.
 *
 * <p>This class parses representation only: zero and negative durations remain
 * visible so the configuration object that owns a field can apply its own policy.
 * Callers must therefore not treat successful parsing as policy validation.</p>
 */
public final class DurationCodec {
    private DurationCodec() {
    }

    /**
     * Parses the legacy grammar: bare seconds or an integer followed by
     * {@code ms}, {@code s}, or {@code m}.
     *
     * @param value serialized duration
     * @param fieldName field name included in validation messages
     * @return parsed duration, including zero or negative values
     * @throws IllegalArgumentException if the value is blank or malformed
     */
    public static Duration parseLegacy(String value, String fieldName) {
        String normalized = normalize(value, fieldName);
        try {
            if (normalized.endsWith("ms")) return Duration.ofMillis(number(normalized, 2));
            if (normalized.endsWith("s")) return Duration.ofSeconds(number(normalized, 1));
            if (normalized.endsWith("m")) return Duration.ofMinutes(number(normalized, 1));
            return Duration.ofSeconds(Long.parseLong(normalized));
        } catch (NumberFormatException failure) {
            throw invalid(fieldName, normalized, failure);
        }
    }

    /**
     * Parses the portable grammar: an integer followed by {@code ms}, {@code s},
     * {@code m}, or {@code h}, or an ISO-8601 duration such as {@code PT2H}.
     * Bare numbers are not portable.
     *
     * @param value serialized duration
     * @param fieldName field name included in validation messages
     * @return parsed duration, including zero or negative values
     * @throws IllegalArgumentException if the value is blank or malformed
     */
    public static Duration parsePortable(String value, String fieldName) {
        String normalized = normalize(value, fieldName);
        try {
            if (normalized.startsWith("p") || normalized.startsWith("-p")) {
                return Duration.parse(value.trim().toUpperCase(Locale.ROOT));
            }
            if (normalized.endsWith("ms")) return Duration.ofMillis(number(normalized, 2));
            if (normalized.endsWith("s")) return Duration.ofSeconds(number(normalized, 1));
            if (normalized.endsWith("m")) return Duration.ofMinutes(number(normalized, 1));
            if (normalized.endsWith("h")) return Duration.ofHours(number(normalized, 1));
            return Duration.parse(value.trim().toUpperCase(Locale.ROOT));
        } catch (NumberFormatException | DateTimeParseException failure) {
            throw invalid(fieldName, normalized, failure);
        }
    }

    /**
     * Formats a duration compactly, preferring exact minutes, then exact seconds,
     * and falling back to milliseconds.
     *
     * @param value duration to format
     * @return compact portable duration text
     */
    public static String format(Duration value) {
        if (value.toMillis() % 1_000 != 0) return value.toMillis() + "ms";
        if (value.getSeconds() % 60 != 0) return value.getSeconds() + "s";
        return value.toMinutes() + "m";
    }

    private static String normalize(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static long number(String value, int suffixLength) {
        return Long.parseLong(value.substring(0, value.length() - suffixLength));
    }

    private static IllegalArgumentException invalid(
            String fieldName, String value, RuntimeException cause) {
        return new IllegalArgumentException(
                "Invalid duration for " + fieldName + ": " + value, cause);
    }
}

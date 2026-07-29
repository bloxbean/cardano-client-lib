package com.bloxbean.cardano.client.txflow.store;

/**
 * Common text compatibility policy for {@link FlowExecutionStore} values.
 *
 * <p>TxFlow stores must preserve identity text identically across in-memory and database-backed
 * implementations. Unicode NUL is therefore rejected before a value reaches an adapter because
 * common relational text types, including PostgreSQL {@code text} and {@code varchar}, cannot
 * represent it. Length limits are expressed in UTF-8 bytes rather than Java characters so the
 * same value fits the certified relational columns and PostgreSQL B-tree identities regardless
 * of whether it is ASCII or multibyte Unicode.</p>
 */
public final class FlowStoreTextPolicy {
    /** Maximum UTF-8 bytes in an idempotency namespace. */
    public static final int MAX_NAMESPACE_BYTES = 255;
    /** Maximum UTF-8 bytes in an idempotency key. */
    public static final int MAX_IDEMPOTENCY_KEY_BYTES = 512;
    /** Maximum UTF-8 bytes in an execution identity. */
    public static final int MAX_EXECUTION_ID_BYTES = 512;
    /** Maximum UTF-8 bytes in a definition or request fingerprint. */
    public static final int MAX_FINGERPRINT_BYTES = 512;
    /** Maximum UTF-8 bytes in a worker owner token. */
    public static final int MAX_OWNER_TOKEN_BYTES = 512;
    /** Maximum UTF-8 bytes in a canonical spending-resource identity. */
    public static final int MAX_RESOURCE_ID_BYTES = 1024;
    /** Maximum UTF-8 bytes in a flow step identity. */
    public static final int MAX_STEP_ID_BYTES = 512;
    /** Maximum UTF-8 bytes in a transaction hash column. */
    public static final int MAX_TRANSACTION_HASH_BYTES = 256;

    private FlowStoreTextPolicy() {
    }

    /**
     * Validates a required, non-blank store identity.
     *
     * @param value identity value
     * @param label safe field label used in validation messages
     * @param maxUtf8Bytes maximum encoded size
     * @return the validated value
     */
    public static String requireIdentifier(String value, String label, int maxUtf8Bytes) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return requireCompatibleText(value, label, maxUtf8Bytes);
    }

    /**
     * Validates required store text while allowing an empty value.
     *
     * @param value required text
     * @param label safe field label used in validation messages
     * @param maxUtf8Bytes maximum encoded size
     * @return the validated value
     */
    public static String requireCompatibleText(String value, String label, int maxUtf8Bytes) {
        if (value == null) throw new NullPointerException(label);
        if (maxUtf8Bytes < 1) {
            throw new IllegalArgumentException("maximum UTF-8 byte count must be positive");
        }
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(label + " cannot contain NUL");
        }
        if (utf8Length(value, label, maxUtf8Bytes) > maxUtf8Bytes) {
            throw new IllegalArgumentException(label + " exceeds " + maxUtf8Bytes
                    + " UTF-8 bytes");
        }
        return value;
    }

    /**
     * Validates optional store text.
     *
     * @param value optional text
     * @param label safe field label used in validation messages
     * @param maxUtf8Bytes maximum encoded size
     * @return the validated value, or {@code null}
     */
    public static String requireOptionalText(String value, String label, int maxUtf8Bytes) {
        return value == null ? null : requireCompatibleText(value, label, maxUtf8Bytes);
    }

    private static int utf8Length(String value, String label, int limit) {
        int bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character <= 0x7f) {
                bytes++;
            } else if (character <= 0x7ff) {
                bytes += 2;
            } else if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(label + " contains malformed Unicode");
                }
                index++;
                bytes += 4;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException(label + " contains malformed Unicode");
            } else {
                bytes += 3;
            }
            if (bytes > limit) return bytes;
        }
        return bytes;
    }
}

package com.bloxbean.cardano.client.plutus.annotation.processor.util;

/**
 * Utility class for handling RFC 6901 JSON Pointer operations.
 *
 * <p>JSON Pointer (RFC 6901) is used in JSON Schema $ref paths to reference definitions.
 * This utility provides methods for escaping and unescaping JSON Pointer paths.</p>
 *
 * @see <a href="https://tools.ietf.org/html/rfc6901">RFC 6901 - JSON Pointer</a>
 */
public class JsonPointerUtil {

    private JsonPointerUtil() {
        // Utility class, prevent instantiation
    }

    /**
     * Unescapes JSON Pointer escape sequences according to RFC 6901.
     *
     * <p>JSON Pointer defines exactly two escape sequences:
     * <ul>
     *   <li><b>~0</b> represents a literal tilde (~) character</li>
     *   <li><b>~1</b> represents a literal forward slash (/) character</li>
     * </ul>
     *
     * <p>These are the ONLY two escape sequences defined in JSON Pointer.
     * No other sequences like ~2, ~3, etc. exist in the specification.
     *
     * <p><b>Why only these two?</b>
     * <ul>
     *   <li>~ needs escaping because it's the escape character itself</li>
     *   <li>/ needs escaping because it's the path separator in JSON Pointer</li>
     * </ul>
     *
     * <p><b>Example from CIP-57 blueprints:</b>
     * <pre>
     * Blueprint definition key: "types/order/Action"
     * JSON $ref path:          "#/definitions/types~1order~1Action"
     * After unescaping:        "types/order/Action"
     * </pre>
     *
     * <p><b>Another example with tildes:</b>
     * <pre>
     * Blueprint definition key: "some~key"
     * JSON $ref path:          "#/definitions/some~0key"
     * After unescaping:        "some~key"
     * </pre>
     *
     * <p><b>Order matters:</b> Must unescape ~1 before ~0 to avoid double-processing.
     * Example: "~01" should become "/", not "~1"
     *
     * @param value the string potentially containing JSON Pointer escape sequences
     * @return the unescaped string, or the original value if null/empty
     * @see <a href="https://tools.ietf.org/html/rfc6901">RFC 6901 - JSON Pointer</a>
     */
    public static String unescape(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // IMPORTANT: Order matters! Must unescape ~1 before ~0 to avoid double-processing
        // Example: "~01" should become "/", not "~1"
        String result = value.replace("~1", "/");
        result = result.replace("~0", "~");

        return result;
    }

    /**
     * Escapes a string for use in JSON Pointer paths according to RFC 6901.
     *
     * <p>Converts:
     * <ul>
     *   <li>~ (tilde) to ~0</li>
     *   <li>/ (forward slash) to ~1</li>
     * </ul>
     *
     * <p><b>Order matters:</b> Must escape ~ before / to avoid double-processing.
     *
     * @param value the string to escape
     * @return the escaped string suitable for use in JSON Pointer paths, or the original value if null/empty
     * @see <a href="https://tools.ietf.org/html/rfc6901">RFC 6901 - JSON Pointer</a>
     */
    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // IMPORTANT: Order matters! Must escape ~ before / to avoid double-processing
        String result = value.replace("~", "~0");
        result = result.replace("/", "~1");

        return result;
    }
}

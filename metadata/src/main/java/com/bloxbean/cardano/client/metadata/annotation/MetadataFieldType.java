package com.bloxbean.cardano.client.metadata.annotation;

/**
 * Controls how a field is serialized into / deserialized from Cardano metadata.
 *
 * <p>Used as the {@code as} attribute of {@link MetadataField}:
 * <pre>
 *   {@literal @}MetadataField(as = MetadataFieldType.STRING)
 *   private int statusCode;
 *
 *   {@literal @}MetadataField(key = "payload", as = MetadataFieldType.STRING_HEX)
 *   private byte[] data;
 * </pre>
 *
 * <p>Valid combinations:
 * <table border="1">
 *   <tr><th>Java type</th><th>DEFAULT</th><th>STRING</th><th>STRING_HEX</th><th>STRING_BASE64</th></tr>
 *   <tr><td>String</td>      <td>✅</td><td>✅ (no-op)</td><td>❌</td><td>❌</td></tr>
 *   <tr><td>int / Integer</td><td>✅</td><td>✅</td><td>❌</td><td>❌</td></tr>
 *   <tr><td>long / Long</td> <td>✅</td><td>✅</td><td>❌</td><td>❌</td></tr>
 *   <tr><td>BigInteger</td>  <td>✅</td><td>✅</td><td>❌</td><td>❌</td></tr>
 *   <tr><td>byte[]</td>      <td>✅</td><td>❌ (ambiguous)</td><td>✅</td><td>✅</td></tr>
 * </table>
 */
public enum MetadataFieldType {

    /**
     * Natural Cardano type mapping for the Java type (default behaviour).
     * int/long/Integer/Long/BigInteger → Cardano integer, String → String, byte[] → bytes.
     */
    DEFAULT,

    /**
     * Force the field to be stored as a UTF-8 String in the metadata map.
     * Numeric types are converted via {@code String.valueOf()} / {@code toString()}.
     * On read-back, the string is parsed back to the original Java type;
     * a malformed value on chain will throw a runtime exception.
     * <p>Not valid for {@code byte[]} — use {@link #STRING_HEX} or {@link #STRING_BASE64} instead.
     */
    STRING,

    /**
     * Encode {@code byte[]} as a lowercase hex string (e.g. {@code "deadbeef"}).
     * Uses {@code HexUtil.encodeHexString} / {@code HexUtil.decodeHexString}.
     * Only valid for {@code byte[]} fields.
     */
    STRING_HEX,

    /**
     * Encode {@code byte[]} as a Base64 string.
     * Uses {@code Base64.getEncoder().encodeToString} / {@code Base64.getDecoder().decode}.
     * Only valid for {@code byte[]} fields.
     */
    STRING_BASE64
}

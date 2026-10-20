package com.bloxbean.cardano.client.quicktx.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serializable metadata binding a document-local namespace to an extension runtime.
 *
 * <p>Immutable. The deployment map is deep-copied on construction and exposed read-only, so a
 * descriptor shared between plans, codecs and builders cannot be changed through any of them.</p>
 *
 * <p>Integer deployment values are normalized to {@link Long}, or {@link BigInteger} beyond the
 * long range, so metadata built in Java equals the same metadata read back from YAML or JSON,
 * where a parser picks the narrowest integer type. Decimal values are kept as given.</p>
 */
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtensionMetadata {
    String extension;

    /** Version of this extension's serialized intent schema, independent of the TxPlan version. */
    @JsonProperty("schema_version")
    String schemaVersion;

    String protocol;

    /** Optional extension-defined informational provenance; QuickTx does not interpret it. */
    @JsonProperty("contract_version")
    String contractVersion;

    /** Extension-defined deployment binding; nested lists and maps are read-only too. */
    Map<String, Object> deployment;

    @Builder(toBuilder = true)
    @Jacksonized
    ExtensionMetadata(String extension,
                      @JsonProperty("schema_version") String schemaVersion,
                      String protocol,
                      @JsonProperty("contract_version") String contractVersion,
                      Map<String, Object> deployment) {
        this.extension = extension;
        this.schemaVersion = schemaVersion;
        this.protocol = protocol;
        this.contractVersion = contractVersion;
        this.deployment = deployment == null ? Map.of() : readOnlyCopy(deployment);
    }

    /** Keys are sorted so equal metadata serializes identically whatever the source map order. */
    private static Map<String, Object> readOnlyCopy(Map<String, Object> source) {
        Map<String, Object> copy = new TreeMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), readOnlyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    @SuppressWarnings("unchecked")
    private static Object readOnlyValue(Object value) {
        if (value instanceof Map) return readOnlyCopy((Map<String, Object>) value);
        if (value instanceof Collection) {
            List<Object> copy = new ArrayList<>();
            ((Collection<?>) value).forEach(item -> copy.add(readOnlyValue(item)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger && ((BigInteger) value).bitLength() < Long.SIZE) {
            return ((BigInteger) value).longValue();
        }
        return value;
    }
}

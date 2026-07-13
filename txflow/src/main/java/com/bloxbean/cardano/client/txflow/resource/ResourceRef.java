package com.bloxbean.cardano.client.txflow.resource;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Normalized logical URI that names a resource without embedding its secret material.
 *
 * <p>A reference must contain a URI scheme and host, for example
 * {@code account://treasury} or {@code script://approved/escrow}. Identity is
 * normalized to a lower-case scheme followed by host and path. Query, fragment,
 * user-info, and port components are not retained and therefore must not be
 * used to distinguish logical resources.</p>
 *
 * @param value normalized logical URI value
 */
public record ResourceRef(String value) implements Comparable<ResourceRef> {
    /**
     * Parses and normalizes a logical resource URI.
     *
     * @param value absolute logical URI with a scheme and host
     */
    public ResourceRef {
        Objects.requireNonNull(value, "value");
        URI uri = URI.create(value);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Resource reference must be an absolute logical URI: " + value);
        }
        value = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost()
                + (uri.getPath() != null ? uri.getPath() : "");
    }

    /**
     * Parses and normalizes a logical resource URI.
     *
     * @param value absolute logical URI
     * @return normalized resource reference
     * @throws IllegalArgumentException if the value is not a logical URI with a scheme and host
     */
    public static ResourceRef of(String value) {
        return new ResourceRef(value);
    }

    /**
     * Orders references lexicographically by their normalized value.
     *
     * @param other reference to compare
     * @return comparison of normalized URI strings
     */
    @Override
    public int compareTo(ResourceRef other) {
        return value.compareTo(other.value);
    }
}

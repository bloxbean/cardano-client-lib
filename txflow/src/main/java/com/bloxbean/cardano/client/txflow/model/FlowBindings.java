package com.bloxbean.cardano.client.txflow.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable runtime values supplied separately from a reusable flow definition.
 *
 * <p>Only portable scalar values are accepted: strings, booleans, and integral
 * Java number types up to {@link Long}. Values are later checked against their
 * corresponding {@link ParameterSpec} during compilation.</p>
 */
public final class FlowBindings {
    private final Map<String, Object> values;

    private FlowBindings(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Starts an empty binding builder.
     *
     * @return mutable builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a binding set containing no runtime values.
     *
     * @return immutable empty bindings
     */
    public static FlowBindings empty() {
        return new FlowBindings(Map.of());
    }

    /**
     * Looks up a binding by its parameter name.
     *
     * @param name parameter name
     * @return bound value, or empty when no value was supplied
     */
    public Optional<Object> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    /**
     * Returns all supplied values in insertion order.
     *
     * @return unmodifiable binding map
     */
    public Map<String, Object> asMap() {
        return values;
    }

    /** Builder that rejects invalid values and duplicate parameter names eagerly. */
    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        /**
         * Adds one portable scalar binding.
         *
         * @param name non-blank parameter name
         * @param value non-null string, boolean, byte, short, integer, or long
         * @return this builder
         * @throws IllegalArgumentException if the name or value is invalid, or the
         *                                  name was already bound
         */
        public Builder put(String name, Object value) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("binding name cannot be blank");
            }
            if (value == null || !(value instanceof String || value instanceof Boolean
                    || value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long)) {
                throw new IllegalArgumentException("binding values must be non-null portable scalars");
            }
            if (values.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("duplicate binding: " + name);
            }
            return this;
        }

        /**
         * Creates an immutable snapshot of the accumulated bindings.
         *
         * @return immutable bindings
         */
        public FlowBindings build() {
            return new FlowBindings(values);
        }
    }
}

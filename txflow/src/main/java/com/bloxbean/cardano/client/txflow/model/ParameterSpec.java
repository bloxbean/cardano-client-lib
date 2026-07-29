package com.bloxbean.cardano.client.txflow.model;

import lombok.Getter;

import java.util.Objects;

/**
 * Immutable declaration of one portable runtime parameter.
 *
 * <p>A specification describes the type and optional constraints used when the
 * compiler validates {@link FlowBindings}. Sensitive parameters are identified in
 * the definition without changing their value type, allowing durable runtimes to
 * require application-controlled secure references.</p>
 */
@Getter
public final class ParameterSpec {
    /** Parameter name used by bindings and portable input expressions. */
    private final String name;
    /** Required portable scalar type. */
    private final ParameterType type;
    /** Whether a caller must supply the parameter. */
    private final boolean required;
    /** Optional value used when no binding is supplied. */
    private final Object defaultValue;
    /** Optional inclusive minimum for integer parameters. */
    private final Long minimum;
    /** Optional inclusive maximum for integer parameters. */
    private final Long maximum;
    /** Optional maximum character count for textual parameters. */
    private final Integer maxLength;
    /** Whether persistence must treat this parameter as sensitive. */
    private final boolean sensitive;

    private ParameterSpec(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.required = builder.required;
        this.defaultValue = builder.defaultValue;
        this.minimum = builder.minimum;
        this.maximum = builder.maximum;
        this.maxLength = builder.maxLength;
        this.sensitive = builder.sensitive;
    }

    /**
     * Starts an integer parameter declaration.
     *
     * @param name non-blank parameter name
     * @return parameter builder
     */
    public static Builder integer(String name) {
        return new Builder(name, ParameterType.INTEGER);
    }

    /**
     * Starts a general string parameter declaration.
     *
     * @param name non-blank parameter name
     * @return parameter builder
     */
    public static Builder string(String name) {
        return new Builder(name, ParameterType.STRING);
    }

    /**
     * Starts an address-valued string parameter declaration.
     *
     * @param name non-blank parameter name
     * @return parameter builder
     */
    public static Builder address(String name) {
        return new Builder(name, ParameterType.ADDRESS);
    }

    /**
     * Starts a boolean parameter declaration.
     *
     * @param name non-blank parameter name
     * @return parameter builder
     */
    public static Builder booleanParameter(String name) {
        return new Builder(name, ParameterType.BOOLEAN);
    }

    /**
     * Starts an asset-unit-valued string parameter declaration.
     *
     * @param name non-blank parameter name
     * @return parameter builder
     */
    public static Builder assetUnit(String name) {
        return new Builder(name, ParameterType.ASSET_UNIT);
    }

    /** Builder for a typed parameter declaration. */
    public static final class Builder {
        private final String name;
        private final ParameterType type;
        private boolean required;
        private Object defaultValue;
        private Long minimum;
        private Long maximum;
        private Integer maxLength;
        private boolean sensitive;

        private Builder(String name, ParameterType type) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("parameter name cannot be blank");
            }
            this.name = name;
            this.type = Objects.requireNonNull(type, "type");
        }

        /**
         * Requires callers to supply a binding for this parameter.
         *
         * @return this builder
         */
        public Builder required() {
            this.required = true;
            return this;
        }

        /**
         * Sets the value used when no runtime binding is supplied.
         * Type compatibility is checked during compilation.
         *
         * @param defaultValue portable scalar default
         * @return this builder
         */
        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Sets the inclusive lower bound for an integer parameter.
         *
         * @param minimum lower bound
         * @return this builder
         */
        public Builder minimum(long minimum) {
            this.minimum = minimum;
            return this;
        }

        /**
         * Sets the inclusive upper bound for an integer parameter.
         *
         * @param maximum upper bound
         * @return this builder
         */
        public Builder maximum(long maximum) {
            this.maximum = maximum;
            return this;
        }

        /**
         * Sets the maximum character count for a textual parameter.
         *
         * @param maxLength non-negative maximum length
         * @return this builder
         */
        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        /**
         * Marks the parameter for sensitive-value handling by durable runtimes.
         *
         * @return this builder
         */
        public Builder sensitive() {
            this.sensitive = true;
            return this;
        }

        /**
         * Validates declaration invariants and creates the immutable specification.
         *
         * @return parameter specification
         * @throws IllegalStateException if a required parameter also has a default,
         *                               bounds conflict, or maximum length is negative
         */
        public ParameterSpec build() {
            if (required && defaultValue != null) {
                throw new IllegalStateException("required parameter cannot also declare a default");
            }
            if (minimum != null && maximum != null && minimum > maximum) {
                throw new IllegalStateException("minimum cannot exceed maximum");
            }
            if (maxLength != null && maxLength < 0) {
                throw new IllegalStateException("maxLength cannot be negative");
            }
            return new ParameterSpec(this);
        }
    }
}

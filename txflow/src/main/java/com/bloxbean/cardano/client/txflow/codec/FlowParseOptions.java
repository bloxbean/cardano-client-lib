package com.bloxbean.cardano.client.txflow.codec;

import lombok.Getter;

import java.util.Set;
import java.util.Objects;

/**
 * Immutable safety and compatibility settings for decoding untrusted TxFlow
 * documents.
 *
 * <p>The limits are enforced before or during decoding. Portable API versions are
 * allow-listed explicitly, and unknown fields are rejected by default. Build an
 * instance once and safely reuse it between parse operations.</p>
 */
@Getter
public final class FlowParseOptions {
    /** Maximum UTF-8 source size accepted by the codec. */
    private final int maxDocumentBytes;
    /** Maximum number of YAML aliases that expand collections; zero disallows them. */
    private final int maxAliases;
    /** Maximum nesting depth accepted by the YAML loader. */
    private final int maxNestingDepth;
    /** Maximum number of steps accepted in a portable flow. */
    private final int maxSteps;
    /** Policy for fields not recognized by the TxFlow envelope. */
    private final UnknownFieldPolicy unknownFieldPolicy;
    /** Portable {@code api_version} identifiers accepted by the parser. */
    private final Set<String> supportedApiVersions;

    private FlowParseOptions(int maxDocumentBytes, int maxAliases, int maxNestingDepth,
                             int maxSteps, UnknownFieldPolicy unknownFieldPolicy,
                             Set<String> supportedApiVersions) {
        this.maxDocumentBytes = maxDocumentBytes;
        this.maxAliases = maxAliases;
        this.maxNestingDepth = maxNestingDepth;
        this.maxSteps = maxSteps;
        this.unknownFieldPolicy = unknownFieldPolicy;
        this.supportedApiVersions = supportedApiVersions;
    }

    /**
     * Starts a builder initialized with the server-oriented defaults.
     *
     * @return a mutable options builder
     */
    public static FlowParseOptionsBuilder builder() {
        return new FlowParseOptionsBuilder();
    }

    /** Builder for immutable {@link FlowParseOptions}. */
    public static class FlowParseOptionsBuilder {
        private int maxDocumentBytes = 3_000_000;
        private int maxAliases = 50;
        private int maxNestingDepth = 100;
        private int maxSteps = 1_000;
        private UnknownFieldPolicy unknownFieldPolicy = UnknownFieldPolicy.REJECT;
        private Set<String> supportedApiVersions = Set.of(
                FlowSchemaVersion.V1ALPHA1.getIdentifier());

        /**
         * Sets the maximum UTF-8 source size.
         *
         * @param value positive byte limit
         * @return this builder
         */
        public FlowParseOptionsBuilder maxDocumentBytes(int value) {
            this.maxDocumentBytes = value;
            return this;
        }

        /**
         * Sets the YAML alias limit.
         *
         * @param value non-negative alias limit
         * @return this builder
         */
        public FlowParseOptionsBuilder maxAliases(int value) {
            this.maxAliases = value;
            return this;
        }

        /**
         * Sets the YAML nesting-depth limit.
         *
         * @param value positive depth limit
         * @return this builder
         */
        public FlowParseOptionsBuilder maxNestingDepth(int value) {
            this.maxNestingDepth = value;
            return this;
        }

        /**
         * Sets the maximum number of portable flow steps.
         *
         * @param value positive step limit
         * @return this builder
         */
        public FlowParseOptionsBuilder maxSteps(int value) {
            this.maxSteps = value;
            return this;
        }

        /**
         * Selects how unknown TxFlow envelope fields are handled.
         *
         * @param value non-null policy
         * @return this builder
         */
        public FlowParseOptionsBuilder unknownFieldPolicy(UnknownFieldPolicy value) {
            this.unknownFieldPolicy = value;
            return this;
        }

        /**
         * Replaces the allow-list of portable schema identifiers.
         *
         * @param value non-empty set of non-blank identifiers
         * @return this builder
         */
        public FlowParseOptionsBuilder supportedApiVersions(Set<String> value) {
            this.supportedApiVersions = value;
            return this;
        }

        /**
         * Validates the configured limits and creates an immutable snapshot.
         *
         * @return reusable parse options
         * @throws NullPointerException if a policy or version set is {@code null}
         * @throws IllegalArgumentException if a limit or version allow-list is invalid
         */
        public FlowParseOptions build() {
            if (maxDocumentBytes < 1 || maxAliases < 0 || maxNestingDepth < 1 || maxSteps < 1) {
                throw new IllegalArgumentException("Parser limits must be positive (aliases may be zero)");
            }
            Objects.requireNonNull(unknownFieldPolicy, "unknownFieldPolicy");
            Objects.requireNonNull(supportedApiVersions, "supportedApiVersions");
            if (supportedApiVersions.isEmpty()
                    || supportedApiVersions.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("supportedApiVersions cannot be empty or blank");
            }
            return new FlowParseOptions(maxDocumentBytes, maxAliases, maxNestingDepth, maxSteps,
                    unknownFieldPolicy, Set.copyOf(supportedApiVersions));
        }
    }

    /**
     * Returns bounded defaults suitable for accepting server-side documents.
     * Unknown fields are rejected and only the current portable API version is
     * allowed.
     *
     * @return default immutable parse options
     */
    public static FlowParseOptions serverDefaults() {
        return builder().build();
    }
}

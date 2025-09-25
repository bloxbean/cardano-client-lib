package com.bloxbean.cardano.statetrees.jmt;

/**
 * Tunable knobs for {@link JellyfishMerkleTreeStore} behaviour.
 */
public final class JellyfishMerkleTreeStoreConfig {

    private final boolean nodeCacheEnabled;
    private final int nodeCacheSize;
    private final boolean valueCacheEnabled;
    private final int valueCacheSize;
    private final int resultNodeLimit;
    private final int resultStaleLimit;
    private final JmtMetrics metrics;

    private JellyfishMerkleTreeStoreConfig(Builder builder) {
        this.nodeCacheEnabled = builder.nodeCacheEnabled;
        this.nodeCacheSize = builder.nodeCacheSize;
        this.valueCacheEnabled = builder.valueCacheEnabled;
        this.valueCacheSize = builder.valueCacheSize;
        this.resultNodeLimit = builder.resultNodeLimit;
        this.resultStaleLimit = builder.resultStaleLimit;
        this.metrics = builder.metrics == null ? JmtMetrics.NOOP : builder.metrics;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JellyfishMerkleTreeStoreConfig defaults() {
        return builder().build();
    }

    public boolean nodeCacheEnabled() {
        return nodeCacheEnabled && nodeCacheSize > 0;
    }

    public int nodeCacheSize() {
        return nodeCacheSize;
    }

    public boolean valueCacheEnabled() {
        return valueCacheEnabled && valueCacheSize > 0;
    }

    public int valueCacheSize() {
        return valueCacheSize;
    }

    public int resultNodeLimit() {
        return resultNodeLimit;
    }

    public int resultStaleLimit() {
        return resultStaleLimit;
    }

    public JmtMetrics metrics() { return metrics; }

    public static final class Builder {
        private boolean nodeCacheEnabled;
        private int nodeCacheSize = 0;
        private boolean valueCacheEnabled;
        private int valueCacheSize = 0;
        private int resultNodeLimit = Integer.MAX_VALUE;
        private int resultStaleLimit = Integer.MAX_VALUE;
        private JmtMetrics metrics = null;

        private Builder() {
        }

        /**
         * Enables an LRU cache for node lookups.
         */
        public Builder enableNodeCache(boolean enabled) {
            this.nodeCacheEnabled = enabled;
            return this;
        }

        public Builder nodeCacheSize(int size) {
            if (size < 0) throw new IllegalArgumentException("nodeCacheSize must be >= 0");
            this.nodeCacheSize = size;
            return this;
        }

        /**
         * Enables a cache for value lookups keyed by hashed user keys.
         */
        public Builder enableValueCache(boolean enabled) {
            this.valueCacheEnabled = enabled;
            return this;
        }

        public Builder valueCacheSize(int size) {
            if (size < 0) throw new IllegalArgumentException("valueCacheSize must be >= 0");
            this.valueCacheSize = size;
            return this;
        }

        /**
         * Limits how many nodes are retained in {@link JellyfishMerkleTree.CommitResult} for streaming commits.
         * A value of {@code Integer.MAX_VALUE} retains all nodes; {@code 0} records none and minimises memory.
         */
        public Builder resultNodeLimit(int limit) {
            if (limit < 0) throw new IllegalArgumentException("resultNodeLimit must be >= 0");
            this.resultNodeLimit = limit;
            return this;
        }

        public Builder resultStaleLimit(int limit) {
            if (limit < 0) throw new IllegalArgumentException("resultStaleLimit must be >= 0");
            this.resultStaleLimit = limit;
            return this;
        }

        /** Supply a metrics sink. Defaults to a no-op if not provided. */
        public Builder metrics(JmtMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public JellyfishMerkleTreeStoreConfig build() {
            if (nodeCacheEnabled && nodeCacheSize <= 0) {
                throw new IllegalStateException("nodeCacheSize must be > 0 when node cache is enabled");
            }
            if (valueCacheEnabled && valueCacheSize <= 0) {
                throw new IllegalStateException("valueCacheSize must be > 0 when value cache is enabled");
            }
            return new JellyfishMerkleTreeStoreConfig(this);
        }
    }
}

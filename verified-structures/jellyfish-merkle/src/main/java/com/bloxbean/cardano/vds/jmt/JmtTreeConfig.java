package com.bloxbean.cardano.vds.jmt;

/**
 * Configuration options for {@link JellyfishMerkleTree}.
 */
public final class JmtTreeConfig {

    private static final int DEFAULT_NODE_CACHE = 2048;
    private static final int DEFAULT_VALUE_CACHE = 2048;

    private final int nodeCacheCapacity;
    private final int valueCacheCapacity;

    private JmtTreeConfig(int nodeCacheCapacity, int valueCacheCapacity) {
        if (nodeCacheCapacity < 0) {
            throw new IllegalArgumentException("nodeCacheCapacity must be >= 0");
        }
        if (valueCacheCapacity < 0) {
            throw new IllegalArgumentException("valueCacheCapacity must be >= 0");
        }
        this.nodeCacheCapacity = nodeCacheCapacity;
        this.valueCacheCapacity = valueCacheCapacity;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JmtTreeConfig defaultConfig() {
        return builder().build();
    }

    public int nodeCacheCapacity() {
        return nodeCacheCapacity;
    }

    public int valueCacheCapacity() {
        return valueCacheCapacity;
    }

    public static final class Builder {
        private int nodeCacheCapacity = DEFAULT_NODE_CACHE;
        private int valueCacheCapacity = DEFAULT_VALUE_CACHE;

        private Builder() {
        }

        public Builder nodeCacheCapacity(int capacity) {
            this.nodeCacheCapacity = capacity;
            return this;
        }

        public Builder valueCacheCapacity(int capacity) {
            this.valueCacheCapacity = capacity;
            return this;
        }

        public JmtTreeConfig build() {
            return new JmtTreeConfig(nodeCacheCapacity, valueCacheCapacity);
        }
    }
}

package com.bloxbean.cardano.vds.mpt.rocksdb;

import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;

/**
 * Schema definition for MPT RocksDB storage with namespace support.
 *
 * <p>Supports dual independent prefixes:
 * <ul>
 *   <li>Column Family Prefix: Controls which CF to use (e.g., "account_nodes")</li>
 *   <li>Key Prefix: Always present (1 byte), prepended to all keys</li>
 * </ul>
 */
final class RocksDbMptSchema {
    // Base column family names (no _shared suffix needed)
    static final String CF_NODES = "nodes";
    static final String CF_ROOTS = "roots";

    /**
     * Generates column family names and key prefix from namespace options.
     *
     * @param options namespace configuration (null uses defaults)
     * @return column families with CF names and key prefix
     */
    static ColumnFamilies columnFamilies(NamespaceOptions options) {
        NamespaceOptions opts = (options != null) ? options : NamespaceOptions.defaults();

        // Simple: just add CF prefix if present
        String cfPrefix = opts.columnFamilyPrefix().isEmpty()
            ? ""
            : opts.columnFamilyPrefix() + "_";

        return new ColumnFamilies(
                cfPrefix + CF_NODES,
                cfPrefix + CF_ROOTS,
                opts.keyPrefix()  // Always present!
        );
    }

    /**
     * Legacy method for backward compatibility.
     *
     * @deprecated Use {@link #columnFamilies(NamespaceOptions)} instead
     */
    @Deprecated
    static ColumnFamilies columnFamilies(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return columnFamilies(NamespaceOptions.defaults());
        } else {
            return columnFamilies(NamespaceOptions.columnFamily(namespace.trim()));
        }
    }

    static final class ColumnFamilies {
        private final String nodes;
        private final String roots;
        private final byte keyPrefix;  // Always present, not nullable!

        private ColumnFamilies(String nodes, String roots, byte keyPrefix) {
            this.nodes = nodes;
            this.roots = roots;
            this.keyPrefix = keyPrefix;
        }

        String nodes() {
            return nodes;
        }

        String roots() {
            return roots;
        }

        byte keyPrefix() {
            return keyPrefix;
        }
    }

    private RocksDbMptSchema() {
        throw new AssertionError("Utility class");
    }
}

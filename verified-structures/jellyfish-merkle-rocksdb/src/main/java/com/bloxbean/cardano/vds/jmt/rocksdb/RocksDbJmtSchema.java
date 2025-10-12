package com.bloxbean.cardano.vds.jmt.rocksdb;

import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;

/**
 * Schema definition for JMT RocksDB storage with namespace support.
 *
 * <p>Supports dual independent prefixes:
 * <ul>
 *   <li>Column Family Prefix: Controls which CF to use (e.g., "account_nodes_jmt")</li>
 *   <li>Key Prefix: Always present (1 byte), prepended to all keys</li>
 * </ul>
 */
final class RocksDbJmtSchema {
    // Base column family names (no _shared suffix needed)
    static final String CF_NODES = "nodes_jmt";
    static final String CF_VALUES = "values_jmt";
    static final String CF_ROOTS = "roots_jmt";
    static final String CF_STALE = "stale_jmt";
    static final String CF_NODES_BY_VERSION = "nodes_by_ver_jmt";
    static final String CF_VALUES_BY_VERSION = "values_by_ver_jmt";

    static final byte[] LATEST_ROOT_KEY = new byte[]{'J', 'M', 'T', '_', 'L', 'A', 'T', 'E', 'S', 'T'};
    static final byte[] LATEST_VERSION_KEY = new byte[]{'J', 'M', 'T', '_', 'V', 'E', 'R'};

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
                cfPrefix + CF_VALUES,
                cfPrefix + CF_ROOTS,
                cfPrefix + CF_STALE,
                cfPrefix + CF_NODES_BY_VERSION,
                cfPrefix + CF_VALUES_BY_VERSION,
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
        private final String values;
        private final String roots;
        private final String stale;
        private final String nodesByVersion;
        private final String valuesByVersion;
        private final byte keyPrefix;  // Always present, not nullable!

        private ColumnFamilies(String nodes, String values, String roots, String stale,
                               String nodesByVersion, String valuesByVersion, byte keyPrefix) {
            this.nodes = nodes;
            this.values = values;
            this.roots = roots;
            this.stale = stale;
            this.nodesByVersion = nodesByVersion;
            this.valuesByVersion = valuesByVersion;
            this.keyPrefix = keyPrefix;
        }

        String nodes() {
            return nodes;
        }

        String values() {
            return values;
        }

        String roots() {
            return roots;
        }

        String stale() {
            return stale;
        }

        String nodesByVersion() {
            return nodesByVersion;
        }

        String valuesByVersion() {
            return valuesByVersion;
        }

        byte keyPrefix() {
            return keyPrefix;
        }
    }

    private RocksDbJmtSchema() {
        throw new AssertionError("Utility class");
    }
}

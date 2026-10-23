package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.DiskMarkSet;
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

        String nodes = cfPrefix + CF_NODES;
        String roots = cfPrefix + CF_ROOTS;
        // GC drops leftover mark-set column families by exact name on open (never the opening store's own CFs).
        // The resolved names end in _nodes / _roots, so they cannot match; keep the invariant explicit.
        if (DiskMarkSet.isMarkSetColumnFamily(nodes) || DiskMarkSet.isMarkSetColumnFamily(roots)) {
            throw new IllegalArgumentException("Column family names '" + nodes + "' / '" + roots
                    + "' collide with GC temporary column family names");
        }
        return new ColumnFamilies(nodes, roots, opts.keyPrefix());
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

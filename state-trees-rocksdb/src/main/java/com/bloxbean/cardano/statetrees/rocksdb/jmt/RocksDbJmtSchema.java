package com.bloxbean.cardano.statetrees.rocksdb.jmt;

final class RocksDbJmtSchema {
    static final String CF_NODES = "nodes_jmt";
    static final String CF_VALUES = "values_jmt";
    static final String CF_ROOTS = "roots_jmt";
    static final String CF_STALE = "stale_jmt";
    static final String CF_NODES_BY_VERSION = "nodes_by_ver_jmt";
    static final String CF_VALUES_BY_VERSION = "values_by_ver_jmt";

    static final byte[] LATEST_ROOT_KEY = new byte[]{'J', 'M', 'T', '_', 'L', 'A', 'T', 'E', 'S', 'T'};
    static final byte[] LATEST_VERSION_KEY = new byte[]{'J', 'M', 'T', '_', 'V', 'E', 'R'};

    static ColumnFamilies columnFamilies(String namespace) {
        String prefix = (namespace == null || namespace.isBlank()) ? "" : namespace.trim() + "_";
        return new ColumnFamilies(
                prefix + CF_NODES,
                prefix + CF_VALUES,
                prefix + CF_ROOTS,
                prefix + CF_STALE,
                prefix + CF_NODES_BY_VERSION,
                prefix + CF_VALUES_BY_VERSION
        );
    }

    static final class ColumnFamilies {
        private final String nodes;
        private final String values;
        private final String roots;
        private final String stale;
        private final String nodesByVersion;
        private final String valuesByVersion;

        private ColumnFamilies(String nodes, String values, String roots, String stale,
                               String nodesByVersion, String valuesByVersion) {
            this.nodes = nodes;
            this.values = values;
            this.roots = roots;
            this.stale = stale;
            this.nodesByVersion = nodesByVersion;
            this.valuesByVersion = valuesByVersion;
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
    }

    private RocksDbJmtSchema() {
        throw new AssertionError("Utility class");
    }
}

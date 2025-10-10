package com.bloxbean.cardano.statetrees.rocksdb.namespace;

/**
 * Namespace configuration for both JMT and MPT RocksDB stores.
 *
 * <p>This class provides dual independent namespace dimensions:</p>
 * <ul>
 *   <li><b>Column Family Prefix:</b> Controls which RocksDB column family to use.
 *       Empty string uses default CFs (e.g., "nodes_jmt"), non-empty adds prefix
 *       (e.g., "account_nodes_jmt")</li>
 *   <li><b>Key Prefix:</b> Always present (1 byte), prepended to every key.
 *       Default is 0x00. Enables multiple logical namespaces within same CF.</li>
 * </ul>
 *
 * <p><b>Four Usage Patterns:</b></p>
 * <ol>
 *   <li>Default CF + Default Key Prefix (0x00): Single tree (most common)</li>
 *   <li>Default CF + Custom Key Prefixes: Multiple trees sharing resources</li>
 *   <li>Custom CF + Default Key Prefix: Isolated tree with dedicated resources</li>
 *   <li>Custom CF + Custom Key Prefixes: Multiple trees within isolated CF group</li>
 * </ol>
 *
 * <p><b>Thread Safety:</b> Immutable and thread-safe.</p>
 *
 * @since 0.8.0
 */
public final class NamespaceOptions {
    private final String columnFamilyPrefix;
    private final byte keyPrefix;

    /** Reserved key prefix for future internal use. Users cannot use this value. */
    private static final byte RESERVED_PREFIX = (byte) 0xFF;

    /**
     * Constructs namespace options with specified CF and key prefixes.
     *
     * @param columnFamilyPrefix the column family prefix (null or empty for default CFs)
     * @param keyPrefix the key prefix byte (0x00-0xFE, excluding reserved 0xFF)
     * @throws IllegalArgumentException if keyPrefix is the reserved value 0xFF
     */
    public NamespaceOptions(String columnFamilyPrefix, byte keyPrefix) {
        this.columnFamilyPrefix = (columnFamilyPrefix == null) ? "" : columnFamilyPrefix;
        this.keyPrefix = keyPrefix;
        validate();
    }

    private void validate() {
        if (keyPrefix == RESERVED_PREFIX) {
            throw new IllegalArgumentException(
                "Key prefix 0xFF is reserved for internal use"
            );
        }
    }

    /**
     * Returns default namespace options (empty CF prefix, key prefix 0x00).
     * <p>Use for single tree or as the primary/default tree.</p>
     *
     * @return default namespace options
     */
    public static NamespaceOptions defaults() {
        return new NamespaceOptions("", (byte) 0x00);
    }

    /**
     * Returns options with custom column family prefix and default key prefix.
     * <p>Use for isolated tree with dedicated RocksDB resources.</p>
     *
     * @param cfPrefix the column family prefix (e.g., "account", "main_state")
     * @return namespace options with custom CF prefix
     */
    public static NamespaceOptions columnFamily(String cfPrefix) {
        return new NamespaceOptions(cfPrefix, (byte) 0x00);
    }

    /**
     * Returns options with default CF and custom key prefix.
     * <p>Use for multiple trees sharing column family resources.</p>
     *
     * @param keyPrefix the key prefix byte (0x01-0xFE)
     * @return namespace options with custom key prefix
     */
    public static NamespaceOptions keyPrefix(byte keyPrefix) {
        return new NamespaceOptions("", keyPrefix);
    }

    /**
     * Returns options with both custom CF prefix and custom key prefix.
     * <p>Use for multiple trees within isolated CF group.</p>
     *
     * @param cfPrefix the column family prefix
     * @param keyPrefix the key prefix byte (0x01-0xFE)
     * @return namespace options with both prefixes
     */
    public static NamespaceOptions both(String cfPrefix, byte keyPrefix) {
        return new NamespaceOptions(cfPrefix, keyPrefix);
    }

    /**
     * Returns the column family prefix.
     * <p>Empty string indicates default column families should be used.</p>
     *
     * @return the CF prefix (never null, may be empty)
     */
    public String columnFamilyPrefix() {
        return columnFamilyPrefix;
    }

    /**
     * Returns the key prefix byte.
     * <p>This value is always prepended to keys in RocksDB.</p>
     *
     * @return the key prefix byte
     */
    public byte keyPrefix() {
        return keyPrefix;
    }

    /**
     * Checks if this configuration uses the default column family.
     *
     * @return true if CF prefix is empty (default), false otherwise
     */
    public boolean usesDefaultColumnFamily() {
        return columnFamilyPrefix.isEmpty();
    }

    /**
     * Checks if this configuration uses the default key prefix (0x00).
     *
     * @return true if key prefix is 0x00, false otherwise
     */
    public boolean usesDefaultKeyPrefix() {
        return keyPrefix == 0x00;
    }

    @Override
    public String toString() {
        return String.format("NamespaceOptions{cfPrefix='%s', keyPrefix=0x%02X}",
            columnFamilyPrefix.isEmpty() ? "<default>" : columnFamilyPrefix,
            keyPrefix & 0xFF);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NamespaceOptions that = (NamespaceOptions) obj;
        return keyPrefix == that.keyPrefix && columnFamilyPrefix.equals(that.columnFamilyPrefix);
    }

    @Override
    public int hashCode() {
        return 31 * columnFamilyPrefix.hashCode() + keyPrefix;
    }
}

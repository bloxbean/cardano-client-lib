package com.bloxbean.cardano.statetrees.rocksdb.namespace;

import org.rocksdb.ReadOptions;

/**
 * Utility for prefixing RocksDB keys with a 1-byte namespace identifier.
 *
 * <p>All keys in RocksDB are prefixed with the namespace ID to enable logical
 * separation of data within the same column family. This class provides
 * efficient prefix/unprefix operations and RocksDB-specific configuration.</p>
 *
 * <p><b>Key Format:</b> {@code [namespaceId:1 byte][original key:variable]}</p>
 *
 * <p><b>Thread Safety:</b> Immutable and thread-safe.</p>
 *
 * @since 0.8.0
 */
public final class KeyPrefixer {
    private final byte namespaceId;

    /**
     * Constructs a key prefixer with the specified namespace ID.
     *
     * @param namespaceId the namespace identifier byte
     */
    public KeyPrefixer(byte namespaceId) {
        this.namespaceId = namespaceId;
    }

    /**
     * Prefixes a key with the namespace ID.
     *
     * @param key the original key bytes
     * @return a new byte array with format [namespaceId][key...]
     * @throws NullPointerException if key is null
     */
    public byte[] prefix(byte[] key) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }
        byte[] prefixed = new byte[1 + key.length];
        prefixed[0] = namespaceId;
        System.arraycopy(key, 0, prefixed, 1, key.length);
        return prefixed;
    }

    /**
     * Removes the namespace prefix from a key.
     *
     * @param prefixedKey the key with namespace prefix
     * @return the original key without the prefix
     * @throws IllegalArgumentException if the key is too short to have a prefix
     */
    public byte[] unprefix(byte[] prefixedKey) {
        if (prefixedKey == null || prefixedKey.length < 1) {
            throw new IllegalArgumentException(
                "Key too short to unprefix (must be at least 1 byte)"
            );
        }
        byte[] unprefixed = new byte[prefixedKey.length - 1];
        System.arraycopy(prefixedKey, 1, unprefixed, 0, unprefixed.length);
        return unprefixed;
    }

    /**
     * Creates RocksDB ReadOptions configured for prefix filtering.
     *
     * <p>This enables efficient iteration over keys with the same namespace prefix.
     * Only keys starting with the namespace ID will be visited during iteration.</p>
     *
     * <p><b>Important:</b> Column families must be configured with
     * {@code useFixedLengthPrefixExtractor(1)} for this to work efficiently.</p>
     *
     * @return ReadOptions with prefix_same_as_start enabled
     */
    public ReadOptions createPrefixReadOptions() {
        ReadOptions opts = new ReadOptions();
        opts.setPrefixSameAsStart(true);
        return opts;
    }

    /**
     * Validates that a prefixed key has the expected namespace.
     *
     * @param prefixedKey the key to check
     * @return true if the key has the correct namespace prefix, false otherwise
     */
    public boolean hasCorrectPrefix(byte[] prefixedKey) {
        return prefixedKey != null
            && prefixedKey.length > 0
            && prefixedKey[0] == namespaceId;
    }

    /**
     * Returns the namespace ID used by this prefixer.
     *
     * @return the namespace identifier byte
     */
    public byte getNamespaceId() {
        return namespaceId;
    }

    @Override
    public String toString() {
        return String.format("KeyPrefixer{namespaceId=0x%02X}", namespaceId & 0xFF);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        KeyPrefixer that = (KeyPrefixer) obj;
        return namespaceId == that.namespaceId;
    }

    @Override
    public int hashCode() {
        return namespaceId;
    }
}

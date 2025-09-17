package com.bloxbean.cardano.statetrees.rocksdb.keys;

import java.nio.ByteBuffer;

/**
 * Type-safe key for version-based root hash storage in RocksDB.
 * 
 * <p>This key type represents 8-byte version numbers used to index
 * root hashes in the RootsIndex. The keys are stored in big-endian
 * format to ensure proper lexicographic ordering for range queries.</p>
 * 
 * <p><b>Key Properties:</b></p>
 * <ul>
 *   <li>Fixed 8-byte length (long value)</li>
 *   <li>Big-endian encoding for proper RocksDB key ordering</li>
 *   <li>Support for block numbers, slot numbers, or custom version schemes</li>
 *   <li>Efficient range query support</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * VersionKey blockKey = VersionKey.of(12345L);
 * VersionKey slotKey = VersionKey.of(67890L);
 * 
 * // Type-safe storage operations
 * rootsIndex.put(blockKey, rootHash1);
 * rootsIndex.put(slotKey, rootHash2);
 * 
 * // Range queries work correctly due to big-endian encoding
 * Map<VersionKey, byte[]> range = rootsIndex.listRange(
 *     VersionKey.of(10000L), 
 *     VersionKey.of(20000L)
 * );
 * }</pre>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class VersionKey extends RocksDbKey {
    
    /**
     * The expected length of a version key in bytes (8 bytes for long).
     */
    public static final int VERSION_LENGTH = 8;
    
    /**
     * The version number this key represents.
     */
    private final long version;
    
    /**
     * Private constructor - use factory method {@link #of(long)} to create instances.
     * 
     * @param version the version number
     */
    private VersionKey(long version) {
        super(longToBytes(version));
        this.version = version;
    }
    
    /**
     * Creates a new VersionKey from a long version number.
     * 
     * <p>The version number is encoded as a big-endian 8-byte array to ensure
     * proper lexicographic ordering in RocksDB for range queries.</p>
     * 
     * @param version the version number (block number, slot number, etc.)
     * @return a new VersionKey instance
     */
    public static VersionKey of(long version) {
        return new VersionKey(version);
    }
    
    /**
     * Creates a new VersionKey from existing key bytes.
     * 
     * <p>Used when reconstructing keys from RocksDB iterator results.
     * Validates that the key bytes are exactly 8 bytes long.</p>
     * 
     * @param keyBytes the 8-byte key representation
     * @return a new VersionKey instance
     * @throws IllegalArgumentException if keyBytes is not 8 bytes
     */
    public static VersionKey fromBytes(byte[] keyBytes) {
        validateLength(keyBytes, VERSION_LENGTH, "Version key");
        long version = ByteBuffer.wrap(keyBytes).getLong();
        return new VersionKey(version);
    }
    
    /**
     * Returns the version number represented by this key.
     * 
     * @return the version number
     */
    public long getVersion() {
        return version;
    }
    
    /**
     * Converts a long value to its big-endian 8-byte representation.
     * 
     * <p>Big-endian encoding ensures that version keys have proper
     * lexicographic ordering in RocksDB, enabling efficient range queries.</p>
     * 
     * @param version the version number to encode
     * @return the 8-byte big-endian representation
     */
    private static byte[] longToBytes(long version) {
        return ByteBuffer.allocate(VERSION_LENGTH).putLong(version).array();
    }
    
    /**
     * Compares this version key with another for ordering.
     * 
     * <p>Version keys are ordered by their version number in ascending order.</p>
     * 
     * @param other the other version key to compare with
     * @return negative if this &lt; other, zero if equal, positive if this &gt; other
     */
    public int compareTo(VersionKey other) {
        return Long.compare(this.version, other.version);
    }
    
    /**
     * Returns a readable string representation of this version key.
     * 
     * @return a string representation like "VersionKey[12345]"
     */
    @Override
    public String toString() {
        return String.format("VersionKey[%d]", version);
    }
}
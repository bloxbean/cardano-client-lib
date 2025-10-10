package com.bloxbean.cardano.statetrees.rocksdb.mpt.keys;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Type-safe key for special metadata entries in RocksDB.
 *
 * <p>This key type represents special metadata keys used by the storage system
 * for tracking latest values, version counters, and other system metadata.
 * These keys are stored with human-readable string identifiers for debugging
 * and maintenance purposes.</p>
 *
 * <p><b>Pre-defined Special Keys:</b></p>
 * <ul>
 *   <li>LATEST: Always contains the most recent root hash</li>
 *   <li>LAST_VERSION: Tracks the highest version number used</li>
 * </ul>
 *
 * <p><b>Key Properties:</b></p>
 * <ul>
 *   <li>Human-readable string-based identifiers</li>
 *   <li>UTF-8 encoding for international character support</li>
 *   <li>Pre-defined constants for common metadata</li>
 *   <li>Extensible for custom metadata needs</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Using pre-defined special keys
 * rootsIndex.put(SpecialKey.LATEST, currentRootHash);
 * rootsIndex.put(SpecialKey.LAST_VERSION, versionBytes);
 *
 * // Creating custom special keys
 * SpecialKey customKey = SpecialKey.of("CHECKPOINT_MARKER");
 * rootsIndex.put(customKey, checkpointData);
 * }</pre>
 *
 * @since 0.8.0
 */
public final class SpecialKey extends RocksDbKey {

    /**
     * Pre-defined key for storing the latest root hash.
     * Always contains the most recently stored root hash for quick access.
     */
    public static final SpecialKey LATEST = new SpecialKey("LATEST");

    /**
     * Pre-defined key for tracking the last used version number.
     * Used for monotonic version generation and range query optimization.
     */
    public static final SpecialKey LAST_VERSION = new SpecialKey("VERSION");

    /**
     * The human-readable identifier for this special key.
     */
    private final String identifier;

    /**
     * Private constructor - use factory methods to create instances.
     *
     * @param identifier the human-readable identifier
     */
    private SpecialKey(String identifier) {
        super(Objects.requireNonNull(identifier, "Identifier cannot be null")
                .getBytes(StandardCharsets.UTF_8));
        this.identifier = identifier;
    }

    /**
     * Creates a new SpecialKey with the specified identifier.
     *
     * <p>The identifier is encoded using UTF-8 to support international
     * characters and ensure consistent byte representation across platforms.</p>
     *
     * @param identifier the human-readable identifier (must not be null or empty)
     * @return a new SpecialKey instance
     * @throws IllegalArgumentException if identifier is null or empty
     */
    public static SpecialKey of(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Special key identifier cannot be null or empty");
        }
        return new SpecialKey(identifier.trim());
    }

    /**
     * Creates a SpecialKey from existing key bytes.
     *
     * <p>Used when reconstructing keys from RocksDB iterator results.
     * Assumes the bytes represent a UTF-8 encoded string identifier.</p>
     *
     * @param keyBytes the UTF-8 encoded identifier bytes
     * @return a new SpecialKey instance
     * @throws IllegalArgumentException if keyBytes cannot be decoded as UTF-8
     */
    public static SpecialKey fromBytes(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length == 0) {
            throw new IllegalArgumentException("Special key bytes cannot be null or empty");
        }

        try {
            String identifier = new String(keyBytes, StandardCharsets.UTF_8);
            return new SpecialKey(identifier);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot decode special key bytes as UTF-8", e);
        }
    }

    /**
     * Returns the human-readable identifier for this special key.
     *
     * @return the identifier string
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Checks if this special key matches the given identifier.
     *
     * <p>Comparison is case-sensitive and uses exact string matching.</p>
     *
     * @param identifier the identifier to check against
     * @return true if the identifiers match exactly, false otherwise
     */
    public boolean matches(String identifier) {
        return this.identifier.equals(identifier);
    }

    /**
     * Returns a readable string representation of this special key.
     *
     * @return a string representation like "SpecialKey[LATEST]"
     */
    @Override
    public String toString() {
        return String.format("SpecialKey[%s]", identifier);
    }
}

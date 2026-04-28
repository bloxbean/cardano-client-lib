package com.bloxbean.cardano.client.test.vds;

/**
 * Represents a single trie operation for property-based testing of trie data structures.
 */
public record TrieOperation(OpType type, byte[] key, byte[] value) {
    public enum OpType { PUT, DELETE, GET }
}

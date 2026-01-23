package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Visitor that collects all key-value entries from the trie.
 *
 * <p>This visitor traverses the entire tree structure, accumulating the path as nibbles
 * and extracting values from leaf nodes. In MpfTrie (which uses hashed keys), all values
 * are stored exclusively in leaf nodes - branch nodes do not contain values.</p>
 *
 * <p>The collected entries contain the hashed keys (not original keys) since MpfTrie
 * hashes all keys before storage using Blake2b-256.</p>
 *
 * <p><b>Usage (unlimited):</b></p>
 * <pre>{@code
 * EntriesCollectorVisitor visitor = new EntriesCollectorVisitor(persistence);
 * rootNode.accept(visitor);
 * List<MpfTrie.Entry> entries = visitor.getEntries();
 * }</pre>
 *
 * <p><b>Usage (limited):</b></p>
 * <pre>{@code
 * EntriesCollectorVisitor visitor = new EntriesCollectorVisitor(persistence, 100);
 * rootNode.accept(visitor);
 * List<MpfTrie.Entry> entries = visitor.getEntries(); // at most 100 entries
 * }</pre>
 *
 * @since 0.8.0
 */
final class EntriesCollectorVisitor implements NodeVisitor<Void> {
    private final NodePersistence persistence;
    private final Deque<Integer> pathAccumulator;
    private final List<MpfTrie.Entry> entries;
    private final int limit;  // -1 for unlimited

    /**
     * Creates a new entries collector visitor with no limit.
     *
     * @param persistence the node persistence layer for loading child nodes
     */
    public EntriesCollectorVisitor(NodePersistence persistence) {
        this(persistence, new ArrayDeque<>(), new ArrayList<>(), -1);
    }

    /**
     * Creates a new entries collector visitor with a limit.
     *
     * @param persistence the node persistence layer for loading child nodes
     * @param limit       maximum number of entries to collect (must be positive)
     */
    public EntriesCollectorVisitor(NodePersistence persistence, int limit) {
        this(persistence, new ArrayDeque<>(), new ArrayList<>(), limit);
    }

    /**
     * Private constructor for creating child visitors with shared state.
     *
     * @param persistence     the node persistence layer
     * @param pathAccumulator the shared path accumulator
     * @param entries         the shared entries list
     * @param limit           maximum number of entries to collect (-1 for unlimited)
     */
    private EntriesCollectorVisitor(NodePersistence persistence,
                                    Deque<Integer> pathAccumulator,
                                    List<MpfTrie.Entry> entries,
                                    int limit) {
        this.persistence = persistence;
        this.pathAccumulator = pathAccumulator;
        this.entries = entries;
        this.limit = limit;
    }

    /**
     * Checks if the collection limit has been reached.
     *
     * @return true if limit is set and entries.size() >= limit
     */
    private boolean limitReached() {
        return limit > 0 && entries.size() >= limit;
    }

    @Override
    public Void visitLeaf(LeafNode leaf) {
        // Early termination if limit reached
        if (limitReached()) {
            return null;
        }

        // Unpack HP-encoded suffix nibbles
        Nibbles.HP hp = Nibbles.unpackHP(leaf.getHp());
        int[] leafNibbles = hp.nibbles;

        // Add leaf nibbles to path
        for (int nibble : leafNibbles) {
            pathAccumulator.addLast(nibble);
        }

        // Combine path to get full (hashed) key
        int[] fullPath = toArray(pathAccumulator);
        byte[] hashedKey = Nibbles.fromNibbles(fullPath);

        // Get original key if present (may be null)
        byte[] originalKey = leaf.getOriginalKey();

        // Add entry with hashed key, value, and optional original key
        entries.add(new MpfTrie.Entry(hashedKey, leaf.getValue(), originalKey));

        // Remove leaf nibbles from path (cleanup for sibling traversal)
        for (int i = 0; i < leafNibbles.length; i++) {
            pathAccumulator.removeLast();
        }

        return null;
    }

    @Override
    public Void visitBranch(BranchNode branch) {
        // Early termination if limit reached
        if (limitReached()) {
            return null;
        }

        // Note: In MpfTrie, branch nodes do not have values (all keys are hashed to 32 bytes,
        // so all values are stored exclusively in leaf nodes at depth 64).
        // However, we handle branch values for completeness in case of non-MpfTrie usage.
        byte[] branchValue = branch.getValue();
        if (branchValue != null && !limitReached()) {
            int[] fullPath = toArray(pathAccumulator);
            byte[] key = Nibbles.fromNibbles(fullPath);
            entries.add(new MpfTrie.Entry(key, branchValue));
        }

        // Traverse all non-null children (0-15), stopping early if limit reached
        for (int i = 0; i < 16 && !limitReached(); i++) {
            byte[] childHash = branch.getChild(i);
            if (childHash != null && childHash.length > 0) {
                // Push nibble to path
                pathAccumulator.addLast(i);

                // Load and visit child
                Node childNode = persistence.load(NodeHash.of(childHash));
                if (childNode != null) {
                    EntriesCollectorVisitor childVisitor =
                            new EntriesCollectorVisitor(persistence, pathAccumulator, entries, limit);
                    childNode.accept(childVisitor);
                }

                // Pop nibble from path
                pathAccumulator.removeLast();
            }
        }

        return null;
    }

    @Override
    public Void visitExtension(ExtensionNode extension) {
        // Early termination if limit reached
        if (limitReached()) {
            return null;
        }

        // Unpack HP-encoded nibbles
        Nibbles.HP hp = Nibbles.unpackHP(extension.getHp());
        int[] extNibbles = hp.nibbles;

        // Push all extension nibbles to path
        for (int nibble : extNibbles) {
            pathAccumulator.addLast(nibble);
        }

        // Load and visit child
        byte[] childHash = extension.getChild();
        if (childHash != null && childHash.length > 0) {
            Node childNode = persistence.load(NodeHash.of(childHash));
            if (childNode != null) {
                EntriesCollectorVisitor childVisitor =
                        new EntriesCollectorVisitor(persistence, pathAccumulator, entries, limit);
                childNode.accept(childVisitor);
            }
        }

        // Pop all extension nibbles from path
        for (int i = 0; i < extNibbles.length; i++) {
            pathAccumulator.removeLast();
        }

        return null;
    }

    /**
     * Returns the collected entries.
     *
     * @return list of all entries found in the trie
     */
    public List<MpfTrie.Entry> getEntries() {
        return entries;
    }

    /**
     * Converts a deque of integers to an array.
     */
    private static int[] toArray(Deque<Integer> deque) {
        int[] result = new int[deque.size()];
        int index = 0;
        for (int value : deque) {
            result[index++] = value;
        }
        return result;
    }
}
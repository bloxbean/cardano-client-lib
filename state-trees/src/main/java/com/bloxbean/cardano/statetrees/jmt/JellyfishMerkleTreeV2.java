package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.jmt.TreeCache.NodeEntry;
import com.bloxbean.cardano.statetrees.jmt.TreeCache.StaleNodeIndex;
import com.bloxbean.cardano.statetrees.jmt.TreeCache.TreeUpdateBatch;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;

import java.util.*;

/**
 * Unified Jellyfish Merkle Tree implementation following Diem's architecture.
 *
 * <p>This implementation uses {@link TreeCache} for batch-local state management,
 * enabling consistent behavior across both in-memory and persistent storage backends.
 * It follows the Diem pattern of delete-then-create for all node updates, ensuring
 * proper versioning and stale node tracking.</p>
 *
 * <h3>Key Differences from Reference Implementation:</h3>
 * <ul>
 *   <li><b>Storage Abstraction</b>: Works with any {@link JmtStore} implementation</li>
 *   <li><b>TreeCache Pattern</b>: Uses three-tier lookup (staged → frozen → storage)</li>
 *   <li><b>Copy-on-Write</b>: Creates new nodes for each version, marks old ones stale</li>
 *   <li><b>Single Code Path</b>: Same logic works for in-memory and persistent storage</li>
 * </ul>
 *
 * @see TreeCache
 * @see JmtStore
 */
public final class JellyfishMerkleTreeV2 {

    private final JmtStore store;
    private final CommitmentScheme commitments;
    private final HashFunction hashFn;

    /**
     * Creates a new JellyfishMerkleTree backed by the specified store.
     *
     * @param store       the storage layer for nodes and values
     * @param commitments the commitment scheme for computing node hashes
     * @param hashFn      the hash function for keys and values
     */
    public JellyfishMerkleTreeV2(JmtStore store, CommitmentScheme commitments, HashFunction hashFn) {
        this.store = Objects.requireNonNull(store, "store");
        this.commitments = Objects.requireNonNull(commitments, "commitments");
        this.hashFn = Objects.requireNonNull(hashFn, "hashFn");
    }

    /**
     * Applies a batch of key-value updates and commits them as a new version.
     *
     * <p><b>Diem-Compatible Architecture:</b></p>
     * <p>Following Diem's design, this implementation does NOT support individual key deletion.
     * All values must be non-null. For deletion semantics in blockchain applications, use
     * the version-based rollback pattern (see ADR-0012 for reorg/rollback handling).</p>
     *
     * <p>Commit Pattern:
     * <ol>
     *   <li>Create TreeCache for this version</li>
     *   <li>Apply all updates (inserts/updates) to the cache</li>
     *   <li>Freeze the cache to capture root hash and statistics</li>
     *   <li>Commit the batch to storage</li>
     * </ol>
     *
     * @param version the version number for this commit (must be monotonically increasing)
     * @param updates map of key → value (values must be non-null)
     * @return commit result with root hash, nodes, and stale markers
     * @throws NullPointerException if updates is null, any key is null, or any value is null
     */
    public CommitResult put(long version, Map<byte[], byte[]> updates) {
        Objects.requireNonNull(updates, "updates");

        // 1. Create TreeCache for this version
        TreeCache cache = new TreeCache(store, version);
        NodeKey initialRoot = cache.getRootNodeKey();

        // 2. Track value operations for the result
        List<ValueOperation> valueOps = new ArrayList<>(updates.size());

        // 3. Apply each update (Diem-compatible: no deletes, all values must be non-null)
        for (Map.Entry<byte[], byte[]> entry : updates.entrySet()) {
            byte[] key = Objects.requireNonNull(entry.getKey(), "key");
            byte[] value = Objects.requireNonNull(entry.getValue(), "value"); // Null not supported

            byte[] keyHash = hashFn.digest(key);
            byte[] valueHash = hashFn.digest(value);

            putValue(keyHash, valueHash, cache);
            valueOps.add(ValueOperation.put(keyHash, value));
        }

        // 4. Compute root hash before freezing
        NodeKey finalRoot = cache.getRootNodeKey();
        Optional<NodeEntry> rootEntryOpt = cache.getNode(finalRoot.path());
        byte[] rootHash;
        if (rootEntryOpt.isPresent()) {
            rootHash = computeNodeHash(finalRoot, rootEntryOpt.get().node());
        } else {
            // Empty tree - use zero hash
            rootHash = new byte[32]; // All zeros
        }

        // 5. Freeze cache to capture state
        cache.freeze(rootHash);

        // 6. Convert to batch and commit to storage
        TreeUpdateBatch batch = cache.toBatch();
        commitBatchToStore(version, rootHash, batch, valueOps);

        // 7. Build result
        Map<NodeKey, JmtNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<NibblePath, NodeEntry> entry : batch.nodes().entrySet()) {
            nodes.put(entry.getValue().nodeKey(), entry.getValue().node());
        }

        List<NodeKey> staleNodes = new ArrayList<>();
        for (StaleNodeIndex staleIndex : batch.staleIndices()) {
            staleNodes.add(staleIndex.nodeKey());
        }

        return new CommitResult(version, rootHash, nodes, staleNodes, valueOps);
    }

    /**
     * Generates a proof of inclusion or non-inclusion for a key at a specific version.
     *
     * @param key     the key to prove (will be hashed)
     * @param version the tree version to query
     * @return the proof, or empty if version doesn't exist
     */
    public Optional<JmtProof> getProof(byte[] key, long version) {
        Objects.requireNonNull(key, "key");

        // Get root hash for this version
        Optional<byte[]> rootHashOpt = store.rootHash(version);
        if (rootHashOpt.isEmpty()) {
            return Optional.empty();
        }

        byte[] keyHash = hashFn.digest(key);
        int[] nibbles = Nibbles.toNibbles(keyHash);

        // Check if tree is empty at this version
        NodeKey rootKey = NodeKey.of(NibblePath.EMPTY, version);
        Optional<JmtStore.NodeEntry> rootEntryOpt = store.getNode(version, NibblePath.EMPTY);

        if (rootEntryOpt.isEmpty()) {
            // Empty tree - non-inclusion proof
            List<JmtProof.BranchStep> emptySteps = Collections.emptyList();
            return Optional.of(JmtProof.nonInclusionEmpty(emptySteps));
        }

        // Navigate tree to find leaf
        List<JmtProof.BranchStep> steps = new ArrayList<>();
        NibblePath currentPath = NibblePath.EMPTY;
        int depth = 0;

        Optional<JmtStore.NodeEntry> currentEntryOpt = rootEntryOpt;

        while (currentEntryOpt.isPresent()) {
            JmtStore.NodeEntry entry = currentEntryOpt.get();
            JmtNode node = entry.node();

            if (node instanceof JmtLeafNode) {
                JmtLeafNode leaf = (JmtLeafNode) node;

                // Check if this is the leaf we're looking for
                if (Arrays.equals(leaf.keyHash(), keyHash)) {
                    // Inclusion proof - found the key
                    byte[] value = store.getValueAt(keyHash, version).orElse(null);
                    int[] fullNibbles = Nibbles.toNibbles(leaf.keyHash());
                    NibblePath fullPath = NibblePath.of(fullNibbles);
                    int pathLen = currentPath.getNibbles().length;
                    NibblePath suffix = fullPath.slice(pathLen, fullPath.length());

                    return Optional.of(JmtProof.inclusion(
                            steps,
                            value,
                            leaf.valueHash(),
                            suffix,
                            leaf.keyHash()
                    ));
                } else {
                    // Non-inclusion proof - found a different leaf
                    int[] fullNibbles = Nibbles.toNibbles(leaf.keyHash());
                    NibblePath fullPath = NibblePath.of(fullNibbles);
                    int pathLen = currentPath.getNibbles().length;
                    NibblePath suffix = fullPath.slice(pathLen, fullPath.length());

                    return Optional.of(JmtProof.nonInclusionDifferentLeaf(
                            steps,
                            leaf.keyHash(),
                            leaf.valueHash(),
                            suffix
                    ));
                }
            } else if (node instanceof JmtInternalNode) {
                JmtInternalNode internal = (JmtInternalNode) node;

                if (depth >= nibbles.length) {
                    throw new IllegalStateException("Depth exceeds key length");
                }

                int nibble = nibbles[depth];

                // Expand child hashes to full 16-element array
                byte[][] fullChildHashes = expandChildHashes(internal.bitmap(), internal.childHashes());

                // Collect neighbor information for proof
                int neighborCount = 0;
                int neighborNibble = -1;
                byte[] leafNeighborKey = null;
                byte[] leafNeighborValue = null;
                NibblePath forkPrefix = null;
                byte[] forkRoot = null;

                for (int idx = 0; idx < 16; idx++) {
                    if (idx == nibble) continue;
                    if (fullChildHashes[idx] != null) {
                        neighborCount++;
                        neighborNibble = idx;

                        // Try to load the neighbor node to determine its type
                        NibblePath neighborPath = currentPath.concat(NibblePath.of(idx));
                        Optional<JmtStore.NodeEntry> neighborOpt = store.getNode(version, neighborPath);

                        if (neighborOpt.isPresent()) {
                            JmtNode neighborNode = neighborOpt.get().node();
                            if (neighborNode instanceof JmtLeafNode) {
                                JmtLeafNode neighborLeaf = (JmtLeafNode) neighborNode;
                                leafNeighborKey = neighborLeaf.keyHash();
                                leafNeighborValue = neighborLeaf.valueHash();
                            } else if (neighborNode instanceof JmtInternalNode) {
                                forkPrefix = neighborPath;
                                forkRoot = fullChildHashes[idx];
                            }
                        }

                        if (neighborCount > 1) break;
                    }
                }

                boolean singleNeighbor = (neighborCount == 1);

                // Add branch step
                steps.add(new JmtProof.BranchStep(
                        currentPath,
                        fullChildHashes,
                        nibble,
                        singleNeighbor,
                        neighborNibble,
                        forkPrefix,
                        forkRoot,
                        leafNeighborKey,
                        leafNeighborValue
                ));

                // Check if child exists at this nibble
                if (fullChildHashes[nibble] == null) {
                    // Non-inclusion - path doesn't exist
                    return Optional.of(JmtProof.nonInclusionEmpty(steps));
                }

                // Navigate to child
                currentPath = currentPath.concat(NibblePath.of(nibble));
                currentEntryOpt = store.getNode(version, currentPath);
                depth++;
            } else {
                throw new IllegalStateException("Unknown node type: " + node.getClass());
            }
        }

        // Path doesn't exist - non-inclusion proof
        return Optional.of(JmtProof.nonInclusionEmpty(steps));
    }

    /**
     * Inserts or updates a single key-value pair in the tree.
     *
     * <p>This is the entry point for individual insertions. It follows Diem's pattern
     * of navigating from root to the insertion point, deleting old nodes along the path,
     * and creating new nodes with the current version.
     *
     * @param keyHash   the hash of the key (32 bytes)
     * @param valueHash the hash of the value (32 bytes)
     * @param cache     the tree cache for this transaction
     */
    private void putValue(byte[] keyHash, byte[] valueHash, TreeCache cache) {
        // Convert key hash to nibble path
        int[] nibbles = Nibbles.toNibbles(keyHash);
        NibblePath fullPath = NibblePath.of(nibbles);

        // Get current root
        NodeKey rootKey = cache.getRootNodeKey();

        // Insert at root and get new root key
        NodeKey newRootKey = insertAt(rootKey, fullPath, 0, keyHash, valueHash, cache);

        // Update root reference
        cache.setRootNodeKey(newRootKey);
    }

    /**
     * Recursive insertion following Diem's insertAt pattern.
     *
     * <p><b>Critical Pattern: Delete-Then-Create</b></p>
     * Every node update follows this sequence:
     * <ol>
     *   <li>Delete the old version: {@code cache.deleteNode(oldKey, isLeaf)}</li>
     *   <li>Create new version: {@code cache.putNode(newKey, newNode)}</li>
     *   <li>Return the new NodeKey</li>
     * </ol>
     *
     * @param nodeKey   the current node's key
     * @param targetPath the full path to the target leaf
     * @param depth     current depth in the tree (nibbles consumed)
     * @param keyHash   the key hash being inserted
     * @param valueHash the value hash being inserted
     * @param cache     the tree cache
     * @return the NodeKey of the updated node
     */
    private NodeKey insertAt(
            NodeKey nodeKey,
            NibblePath targetPath,
            int depth,
            byte[] keyHash,
            byte[] valueHash,
            TreeCache cache) {

        long version = cache.nextVersion();

        // Retrieve current node (or empty if doesn't exist)
        Optional<NodeEntry> nodeOpt = cache.getNode(nodeKey.path());

        if (!nodeOpt.isPresent()) {
            // Empty position - create new leaf
            return createLeaf(nodeKey.path(), version, keyHash, valueHash, cache);
        }

        JmtNode node = nodeOpt.get().node();

        if (node instanceof JmtLeafNode) {
            // Hit existing leaf - need to handle collision
            return insertAtLeaf(nodeKey, (JmtLeafNode) node, targetPath, depth, keyHash, valueHash, cache);
        } else if (node instanceof JmtInternalNode) {
            // Traverse internal node
            return insertAtInternal(nodeKey, (JmtInternalNode) node, targetPath, depth, keyHash, valueHash, cache);
        } else {
            throw new IllegalStateException("Unknown node type: " + node.getClass());
        }
    }

    /**
     * Updates an internal node by recursing into the appropriate child.
     *
     * <p>Pattern:
     * <ol>
     *   <li>Delete old internal node</li>
     *   <li>Recurse into child at nibble index</li>
     *   <li>Create new internal node with updated child hash</li>
     * </ol>
     *
     * @param nodeKey    the current internal node's key
     * @param internal   the internal node
     * @param targetPath the full path to target leaf
     * @param depth      current depth
     * @param keyHash    key being inserted
     * @param valueHash  value being inserted
     * @param cache      the tree cache
     * @return the new NodeKey for the updated internal node
     */
    private NodeKey insertAtInternal(
            NodeKey nodeKey,
            JmtInternalNode internal,
            NibblePath targetPath,
            int depth,
            byte[] keyHash,
            byte[] valueHash,
            TreeCache cache) {

        long version = cache.nextVersion();

        // CRITICAL: Delete old version first (Diem pattern)
        cache.deleteNode(nodeKey, false /* not a leaf */);

        // Determine which child to update
        int childNibble = targetPath.getNibbles()[depth];

        // Compute child path
        NibblePath childPath = targetPath.slice(0, depth + 1);

        // Find the existing child's version (or use current version if new child)
        long childVersion = findChildVersion(childPath, cache, version);

        // Generate child node key with correct version
        NodeKey childKey = NodeKey.of(childPath, childVersion);

        // Recurse into child
        NodeKey newChildKey = insertAt(childKey, targetPath, depth + 1, keyHash, valueHash, cache);

        // Build new internal node with updated child
        JmtInternalNode newInternal = updateInternalNodeChild(
                internal,
                childNibble,
                newChildKey,
                cache
        );

        // Create new internal node at current version
        NodeKey newNodeKey = NodeKey.of(nodeKey.path(), version);
        cache.putNode(newNodeKey, newInternal);

        return newNodeKey;
    }

    /**
     * Handles insertion when hitting an existing leaf node.
     *
     * <p>Two cases:
     * <ol>
     *   <li><b>Same key</b>: Replace the leaf (update value)</li>
     *   <li><b>Different key</b>: Split into internal node with both leaves as children</li>
     * </ol>
     *
     * @param nodeKey    the existing leaf's key
     * @param leaf       the existing leaf node
     * @param targetPath the full path to new leaf
     * @param depth      current depth
     * @param keyHash    new key being inserted
     * @param valueHash  new value being inserted
     * @param cache      the tree cache
     * @return the new NodeKey (leaf if same key, internal if split)
     */
    private NodeKey insertAtLeaf(
            NodeKey nodeKey,
            JmtLeafNode leaf,
            NibblePath targetPath,
            int depth,
            byte[] keyHash,
            byte[] valueHash,
            TreeCache cache) {

        long version = cache.nextVersion();

        // Check if same key (update case)
        if (Arrays.equals(leaf.keyHash(), keyHash)) {
            // Same key - replace leaf
            // Delete old leaf
            cache.deleteNode(nodeKey, true /* is leaf */);

            // Create new leaf with updated value
            JmtLeafNode newLeaf = JmtLeafNode.of(keyHash, valueHash);
            NodeKey newLeafKey = NodeKey.of(nodeKey.path(), version);
            cache.putNode(newLeafKey, newLeaf);

            return newLeafKey;
        }

        // Different keys - need to create internal node to accommodate both
        // Delete old leaf
        cache.deleteNode(nodeKey, true /* is leaf */);

        // Find divergence point
        int[] existingNibbles = Nibbles.toNibbles(leaf.keyHash());
        int[] newNibbles = targetPath.getNibbles();

        int divergeDepth = depth;
        while (divergeDepth < existingNibbles.length &&
               divergeDepth < newNibbles.length &&
               existingNibbles[divergeDepth] == newNibbles[divergeDepth]) {
            divergeDepth++;
        }

        // Create internal node at divergence point
        return createInternalNodeWithTwoLeaves(
                nodeKey.path(),
                depth,
                divergeDepth,
                existingNibbles,
                leaf.keyHash(),
                leaf.valueHash(),
                newNibbles,
                keyHash,
                valueHash,
                version,
                cache
        );
    }

    /**
     * Creates a new leaf node.
     *
     * @param path      the path to this leaf
     * @param version   the version for this leaf
     * @param keyHash   the key hash
     * @param valueHash the value hash
     * @param cache     the tree cache
     * @return the NodeKey of the new leaf
     */
    private NodeKey createLeaf(
            NibblePath path,
            long version,
            byte[] keyHash,
            byte[] valueHash,
            TreeCache cache) {

        JmtLeafNode leaf = JmtLeafNode.of(keyHash, valueHash);
        NodeKey leafKey = NodeKey.of(path, version);
        cache.putNode(leafKey, leaf);

        return leafKey;
    }

    /**
     * Creates an internal node that accommodates two leaves after a split.
     *
     * @param currentPath    the current path (before divergence)
     * @param currentDepth   the current depth
     * @param divergeDepth   the depth where keys diverge
     * @param existingNibbles nibbles of existing leaf's key
     * @param existingKeyHash existing leaf's key hash
     * @param existingValueHash existing leaf's value hash
     * @param newNibbles     nibbles of new key
     * @param newKeyHash     new key hash
     * @param newValueHash   new value hash
     * @param version        current version
     * @param cache          tree cache
     * @return NodeKey of the newly created internal node
     */
    private NodeKey createInternalNodeWithTwoLeaves(
            NibblePath currentPath,
            int currentDepth,
            int divergeDepth,
            int[] existingNibbles,
            byte[] existingKeyHash,
            byte[] existingValueHash,
            int[] newNibbles,
            byte[] newKeyHash,
            byte[] newValueHash,
            long version,
            TreeCache cache) {

        // Build path to divergence point by following shared nibbles
        NibblePath path = currentPath;
        for (int depth = currentDepth; depth < divergeDepth; depth++) {
            // Both keys share the same nibble at this depth
            int sharedNibble = existingNibbles[depth]; // same as newNibbles[depth]
            path = path.concat(NibblePath.of(sharedNibble));
        }

        // At divergence point, create two leaves with different nibbles
        int existingChildNibble = existingNibbles[divergeDepth];
        int newChildNibble = newNibbles[divergeDepth];

        // Create leaves by extending divergence path
        NibblePath existingLeafPath = path.concat(NibblePath.of(existingChildNibble));
        NodeKey existingLeafKey = createLeaf(existingLeafPath, version, existingKeyHash, existingValueHash, cache);

        NibblePath newLeafPath = path.concat(NibblePath.of(newChildNibble));
        NodeKey newLeafKey = createLeaf(newLeafPath, version, newKeyHash, newValueHash, cache);

        // Create internal node at divergence point with two children
        int bitmap = (1 << existingChildNibble) | (1 << newChildNibble);
        byte[][] childHashes = new byte[2][];

        // Get child hashes
        byte[] existingHash = computeLeafHash(existingLeafPath, existingKeyHash, existingValueHash);
        byte[] newHash = computeLeafHash(newLeafPath, newKeyHash, newValueHash);

        // Order children by nibble index
        if (existingChildNibble < newChildNibble) {
            childHashes[0] = existingHash;
            childHashes[1] = newHash;
        } else {
            childHashes[0] = newHash;
            childHashes[1] = existingHash;
        }

        JmtInternalNode internalNode = JmtInternalNode.of(bitmap, childHashes, null);
        byte[] internalHash = computeInternalHash(path, internalNode);
        NodeKey internalKey = NodeKey.of(path, version);
        cache.putNode(internalKey, internalNode);

        // Now create single-child internal nodes walking back up to currentPath
        for (int depth = divergeDepth - 1; depth >= currentDepth; depth--) {
            NibblePath parentPath = currentPath;
            for (int d = currentDepth; d < depth; d++) {
                parentPath = parentPath.concat(NibblePath.of(existingNibbles[d]));
            }

            int childNibble = existingNibbles[depth];
            int parentBitmap = 1 << childNibble;
            byte[][] parentChildHashes = new byte[][]{internalHash};

            JmtInternalNode parentNode = JmtInternalNode.of(parentBitmap, parentChildHashes, null);
            internalHash = computeInternalHash(parentPath, parentNode);
            NodeKey parentKey = NodeKey.of(parentPath, version);
            cache.putNode(parentKey, parentNode);
            internalKey = parentKey;
        }

        return internalKey;
    }

    /**
     * Updates an internal node by replacing one child's hash.
     *
     * @param internal      the existing internal node
     * @param childNibble   the child index to update
     * @param newChildKey   the new child's NodeKey
     * @param cache         the tree cache
     * @return new JmtInternalNode with updated child
     */
    private JmtInternalNode updateInternalNodeChild(
            JmtInternalNode internal,
            int childNibble,
            NodeKey newChildKey,
            TreeCache cache) {

        // Compute new child hash
        Optional<NodeEntry> childEntry = cache.getNode(newChildKey.path());
        byte[] newChildHash = childEntry.map(e -> computeNodeHash(newChildKey, e.node()))
                .orElseThrow(() -> new IllegalStateException("Child node not found after insert"));

        // Clone child hashes and update the one that changed
        int bitmap = internal.bitmap();
        byte[][] oldChildHashes = internal.childHashes();
        byte[][] newChildHashes = new byte[oldChildHashes.length][];

        // Copy all hashes
        for (int i = 0; i < oldChildHashes.length; i++) {
            newChildHashes[i] = oldChildHashes[i].clone();
        }

        // Find position of this child in the array
        int position = calculateChildPosition(bitmap, childNibble);

        // Update or add the child hash
        if ((bitmap & (1 << childNibble)) != 0) {
            // Child already exists - update
            newChildHashes[position] = newChildHash;
        } else {
            // New child - need to expand array
            bitmap |= (1 << childNibble);
            int newSize = Integer.bitCount(bitmap);
            byte[][] expandedHashes = new byte[newSize][];

            int srcIdx = 0, dstIdx = 0;
            for (int nibble = 0; nibble < 16; nibble++) {
                if ((bitmap & (1 << nibble)) != 0) {
                    if (nibble == childNibble) {
                        expandedHashes[dstIdx++] = newChildHash;
                    } else {
                        expandedHashes[dstIdx++] = newChildHashes[srcIdx++].clone();
                    }
                }
            }
            newChildHashes = expandedHashes;
        }

        return JmtInternalNode.of(bitmap, newChildHashes, internal.compressedPath());
    }

    /**
     * Calculates the position of a child in the compressed child array.
     *
     * @param bitmap      the bitmap indicating which children exist
     * @param childNibble the nibble index (0-15)
     * @return the position in the child array
     */
    private int calculateChildPosition(int bitmap, int childNibble) {
        // Count how many children come before this nibble
        int mask = (1 << childNibble) - 1;
        return Integer.bitCount(bitmap & mask);
    }

    /**
     * Finds the version of a child node by looking it up in the cache/storage.
     *
     * <p>Unlike Diem which stores child versions in a Child struct, we look up
     * the existing child node from TreeCache/storage to find its version.
     * This is necessary because our InternalNode only stores child hashes, not versions.
     *
     * @param childPath     the full path to the child node
     * @param cache         the tree cache
     * @param parentVersion the parent's version (used as fallback if child not found)
     * @return the child's version
     */
    private long findChildVersion(NibblePath childPath, TreeCache cache, long parentVersion) {
        // Look up the existing child node to get its version
        Optional<TreeCache.NodeEntry> childEntry = cache.getNode(childPath);
        if (childEntry.isPresent()) {
            return childEntry.get().nodeKey().version();
        }
        // Child doesn't exist yet - will be created at current version
        return parentVersion;
    }

    /**
     * Computes the hash of a node given its storage path.
     *
     * @param nodeKey the node's key (contains path)
     * @param node the node to hash
     * @return the computed hash
     */
    private byte[] computeNodeHash(NodeKey nodeKey, JmtNode node) {
        if (node instanceof JmtLeafNode) {
            JmtLeafNode leaf = (JmtLeafNode) node;
            return computeLeafHash(nodeKey.path(), leaf.keyHash(), leaf.valueHash());
        } else if (node instanceof JmtInternalNode) {
            JmtInternalNode internal = (JmtInternalNode) node;
            return computeInternalHash(nodeKey.path(), internal);
        } else {
            throw new IllegalStateException("Unknown node type: " + node.getClass());
        }
    }

    /**
     * Computes the hash of a leaf node.
     *
     * <p>The hash is computed as H(0x00 || suffix || valueHash) where:
     * <ul>
     *   <li>suffix = remaining nibbles from the leaf's position to the full key</li>
     *   <li>valueHash = hash of the value</li>
     * </ul>
     *
     * <p>Example: If keyHash has 64 nibbles and the leaf is stored at path length 5,
     * then suffix = nibbles[5..64].
     *
     * @param leafPath the storage path where this leaf is located
     * @param keyHash the full key hash (32 bytes)
     * @param valueHash the value hash (32 bytes)
     * @return the computed leaf hash
     */
    private byte[] computeLeafHash(NibblePath leafPath, byte[] keyHash, byte[] valueHash) {
        // Convert keyHash to full nibble path (64 nibbles for 32-byte hash)
        int[] fullNibbles = Nibbles.toNibbles(keyHash);
        NibblePath fullPath = NibblePath.of(fullNibbles);

        // Compute suffix: remaining nibbles from leaf position to end
        int pathLen = leafPath.getNibbles().length;
        NibblePath suffix = fullPath.slice(pathLen, fullPath.length());

        // Use commitment scheme with suffix
        return commitments.commitLeaf(suffix, valueHash);
    }

    /**
     * Computes the hash of an internal node.
     *
     * <p>The hash is computed as H(0x01 || bitmap || child[0] || ... || child[15])
     * where null children are replaced with zero hashes.
     *
     * @param nodePath the storage path where this internal node is located
     * @param internal the internal node
     * @return the computed internal hash
     */
    private byte[] computeInternalHash(NibblePath nodePath, JmtInternalNode internal) {
        // For classic JMT, internal nodes don't include the path in the hash
        // The path parameter is ignored by ClassicJmtCommitmentScheme.commitBranch
        NibblePath prefix = internal.compressedPath() != null
                ? NibblePath.of(Nibbles.toNibbles(internal.compressedPath()))
                : NibblePath.EMPTY;

        // Expand compressed child hashes to full 16-element array
        byte[][] fullChildHashes = expandChildHashes(internal.bitmap(), internal.childHashes());

        return commitments.commitBranch(prefix, fullChildHashes);
    }

    /**
     * Expands compressed child hashes to a full 16-element array.
     *
     * @param bitmap      the bitmap indicating which children exist
     * @param compressed  the compressed array of child hashes
     * @return full 16-element array with nulls for missing children
     */
    private byte[][] expandChildHashes(int bitmap, byte[][] compressed) {
        byte[][] expanded = new byte[16][];
        int compressedIdx = 0;

        for (int nibble = 0; nibble < 16; nibble++) {
            if ((bitmap & (1 << nibble)) != 0) {
                expanded[nibble] = compressed[compressedIdx++];
            } else {
                expanded[nibble] = null;
            }
        }

        return expanded;
    }

    /**
     * Commits a TreeUpdateBatch to the underlying storage.
     *
     * @param version  the version being committed
     * @param rootHash the root hash for this version
     * @param batch    the batch of nodes and stale markers
     */
    private void commitBatchToStore(long version, byte[] rootHash, TreeUpdateBatch batch, List<ValueOperation> valueOps) {
        JmtStore.CommitBatch storeBatch = store.beginCommit(version, JmtStore.CommitConfig.defaults());

        try {
            // Write all nodes
            for (Map.Entry<NibblePath, NodeEntry> entry : batch.nodes().entrySet()) {
                NodeEntry nodeEntry = entry.getValue();
                storeBatch.putNode(nodeEntry.nodeKey(), nodeEntry.node());
            }

            // Mark stale nodes
            for (StaleNodeIndex staleIndex : batch.staleIndices()) {
                storeBatch.markStale(staleIndex.nodeKey());
            }

            // Persist value operations
            for (ValueOperation valueOp : valueOps) {
                if (valueOp.type() == ValueOperation.Type.DELETE) {
                    storeBatch.deleteValue(valueOp.keyHash());
                } else {
                    storeBatch.putValue(valueOp.keyHash(), valueOp.value());
                }
            }

            // Set root hash
            storeBatch.setRootHash(rootHash);

            // Commit atomically
            storeBatch.commit();
        } finally {
            storeBatch.close();
        }
    }

    // ===== Result Types =====

    /**
     * Result of a commit operation.
     */
    public static final class CommitResult {
        private final long version;
        private final byte[] rootHash;
        private final Map<NodeKey, JmtNode> nodes;
        private final List<NodeKey> staleNodes;
        private final List<ValueOperation> valueOperations;

        public CommitResult(
                long version,
                byte[] rootHash,
                Map<NodeKey, JmtNode> nodes,
                List<NodeKey> staleNodes,
                List<ValueOperation> valueOperations) {
            this.version = version;
            this.rootHash = Arrays.copyOf(rootHash, rootHash.length);
            this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
            this.staleNodes = Collections.unmodifiableList(new ArrayList<>(staleNodes));
            this.valueOperations = Collections.unmodifiableList(new ArrayList<>(valueOperations));
        }

        public long version() {
            return version;
        }

        public byte[] rootHash() {
            return Arrays.copyOf(rootHash, rootHash.length);
        }

        public Map<NodeKey, JmtNode> nodes() {
            return nodes;
        }

        public List<NodeKey> staleNodes() {
            return staleNodes;
        }

        public List<ValueOperation> valueOperations() {
            return valueOperations;
        }
    }

    /**
     * Represents a value operation (put or delete).
     */
    public static final class ValueOperation {
        public enum Type {PUT, DELETE}

        private final Type type;
        private final byte[] keyHash;
        private final byte[] value;

        private ValueOperation(Type type, byte[] keyHash, byte[] value) {
            this.type = type;
            this.keyHash = Arrays.copyOf(keyHash, keyHash.length);
            this.value = value != null ? Arrays.copyOf(value, value.length) : null;
        }

        public static ValueOperation put(byte[] keyHash, byte[] value) {
            return new ValueOperation(Type.PUT, keyHash, value);
        }

        public static ValueOperation delete(byte[] keyHash) {
            return new ValueOperation(Type.DELETE, keyHash, null);
        }

        public Type type() {
            return type;
        }

        public byte[] keyHash() {
            return Arrays.copyOf(keyHash, keyHash.length);
        }

        public byte[] value() {
            return value != null ? Arrays.copyOf(value, value.length) : null;
        }
    }
}

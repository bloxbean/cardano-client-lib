package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.jmt.TreeCache.NodeEntry;
import com.bloxbean.cardano.vds.jmt.TreeCache.StaleNodeIndex;
import com.bloxbean.cardano.vds.jmt.TreeCache.TreeUpdateBatch;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.metrics.JmtMetrics;
import com.bloxbean.cardano.vds.jmt.proof.ClassicJmtProofCodec;
import com.bloxbean.cardano.vds.jmt.proof.JmtProofCodec;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;

import java.util.*;

/**
 * Jellyfish Merkle Tree implementation inspired by Diem's JMT implementation.
 *
 * <p>This implementation uses {@link TreeCache} for batch-local state management,
 * enabling consistent behavior across both in-memory and persistent storage backends.
 * It follows the Diem pattern of delete-then-create for all node updates, ensuring
 * proper versioning and stale node tracking.</p>
 *
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
public final class JellyfishMerkleTree {

    private final JmtStore store;
    private final CommitmentScheme commitments;
    private final HashFunction hashFn;
    private final JmtMetrics metrics;
    private final JmtProofCodec proofCodec;

    /**
     * Creates a new JellyfishMerkleTree backed by the specified store.
     *
     * @param store       the storage layer for nodes and values
     * @param commitments the commitment scheme for computing node hashes
     * @param hashFn      the hash function for keys and values
     */
    public JellyfishMerkleTree(JmtStore store, CommitmentScheme commitments, HashFunction hashFn) {
        this(store, commitments, hashFn, JmtMetrics.NOOP);
    }

    /**
     * Creates a new JellyfishMerkleTree with metrics enabled.
     *
     * @param store       the storage layer for nodes and values
     * @param commitments the commitment scheme for computing node hashes
     * @param hashFn      the hash function for keys and values
     * @param metrics     metrics collector (use JmtMetrics.NOOP to disable)
     */
    public JellyfishMerkleTree(JmtStore store, CommitmentScheme commitments, HashFunction hashFn, JmtMetrics metrics) {
        this(store, commitments, hashFn, metrics, new ClassicJmtProofCodec());
    }

    /**
     * Creates a new JellyfishMerkleTree with full customization (metrics and custom proof codec).
     *
     * @param store       the storage layer for nodes and values
     * @param commitments the commitment scheme for computing node hashes
     * @param hashFn      the hash function for keys and values
     * @param metrics     metrics collector (use JmtMetrics.NOOP to disable)
     * @param proofCodec  the codec for encoding/decoding proofs to wire format
     */
    public JellyfishMerkleTree(JmtStore store, CommitmentScheme commitments, HashFunction hashFn, JmtMetrics metrics, JmtProofCodec proofCodec) {
        this.store = Objects.requireNonNull(store, "store");
        this.commitments = Objects.requireNonNull(commitments, "commitments");
        this.hashFn = Objects.requireNonNull(hashFn, "hashFn");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.proofCodec = Objects.requireNonNull(proofCodec, "proofCodec");
    }

    /**
     * Creates a new JellyfishMerkleTree with default ClassicJmtCommitmentScheme and no metrics.
     *
     * <p>This is the simplest constructor for most use cases. It uses the classic JMT commitment scheme
     * inspired by Diem's reference implementation.
     *
     * @param store  the storage layer for nodes and values
     * @param hashFn the hash function for keys and values
     */
    public JellyfishMerkleTree(JmtStore store, HashFunction hashFn) {
        this(store, new ClassicJmtCommitmentScheme(hashFn), hashFn, JmtMetrics.NOOP);
    }

    /**
     * Creates a new JellyfishMerkleTree with default ClassicJmtCommitmentScheme and metrics enabled.
     *
     * <p>This constructor is useful when you want metrics but don't need a custom commitment scheme.
     *
     * @param store   the storage layer for nodes and values
     * @param hashFn  the hash function for keys and values
     * @param metrics metrics collector
     */
    public JellyfishMerkleTree(JmtStore store, HashFunction hashFn, JmtMetrics metrics) {
        this(store, new ClassicJmtCommitmentScheme(hashFn), hashFn, metrics);
    }

    /**
     * Applies a batch of key-value updates and commits them as a new version.
     *
     * <p><b>Diem-Inspired Architecture:</b></p>
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

        long startTime = System.currentTimeMillis();

        // 1. Create TreeCache for this version
        TreeCache cache = new TreeCache(store, version);
        NodeKey initialRoot = cache.getRootNodeKey();

        // 2. Track value operations for the result
        List<ValueOperation> valueOps = new ArrayList<>(updates.size());

        // 3. Apply each update (Diem-inspired: no deletes, all values must be non-null)
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
        Optional<NodeEntry> rootEntryOpt = cache.getNode(finalRoot);
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
        for (Map.Entry<NodeKey, NodeEntry> entry : batch.nodes().entrySet()) {
            nodes.put(entry.getKey(), entry.getValue().node());
        }

        List<NodeKey> staleNodes = new ArrayList<>();
        for (StaleNodeIndex staleIndex : batch.staleIndices()) {
            staleNodes.add(staleIndex.nodeKey());
        }

        // 8. Record metrics
        long durationMs = System.currentTimeMillis() - startTime;
        metrics.recordCommit(durationMs, version, updates.size(), nodes.size(), staleNodes.size());

        // Record storage stats (partial: version and root hash size available)
        metrics.recordStorageStats(version, rootHash.length, 0, 0);

        // Record cache stats (cache size only, no hit/miss tracking)
        metrics.recordCacheStats(0, 0, batch.nodes().size());

        return new CommitResult(version, rootHash, nodes, staleNodes, valueOps);
    }

    /**
     * Retrieves the value for a key at the latest version.
     *
     * <p><b>Performance:</b> This is the fastest way to read values, directly querying
     * the storage layer without tree traversal or proof generation.
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Trusted internal operations where proof verification is not needed</li>
     *   <li>High-performance queries for the latest state</li>
     *   <li>Simple key-value lookups</li>
     * </ul>
     *
     * <p><b>Verifiability:</b> This method does NOT verify that the value is in the tree
     * or that the tree structure is correct. For untrusted data or when cryptographic
     * verification is required, use {@link #getProof(byte[], long)} (byte[], long)} instead.
     *
     * @param key the key to look up (will be hashed)
     * @return the value if present, empty otherwise
     */
    public Optional<byte[]> get(byte[] key) {
        Objects.requireNonNull(key, "key");
        byte[] keyHash = hashFn.digest(key);

        long startTime = System.currentTimeMillis();
        Optional<byte[]> value = store.getValue(keyHash);

        // Record metrics
        long durationMs = System.currentTimeMillis() - startTime;
        metrics.recordRead(durationMs, false, value.isPresent());

        return value;
    }

    /**
     * Retrieves the value for a key at a specific version.
     *
     * <p><b>Performance:</b> Fast storage-layer read without tree traversal or proof generation.
     * Approximately 20-30x faster than {@link #getProof(byte[], long)} for deep trees.
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Historical state queries where proof verification is not needed</li>
     *   <li>Trusted internal operations requiring versioned reads</li>
     *   <li>Bulk data retrieval and analysis</li>
     * </ul>
     *
     * <p><b>Verifiability:</b> This method does NOT verify that the value is in the tree
     * or that the tree structure is correct. For untrusted data or when cryptographic
     * verification is required, use {@link #getProof(byte[], long)} (byte[], long)} instead.
     *
     * @param key     the key to look up (will be hashed)
     * @param version the tree version to query
     * @return the value if present at that version, empty otherwise
     */
    public Optional<byte[]> get(byte[] key, long version) {
        Objects.requireNonNull(key, "key");
        byte[] keyHash = hashFn.digest(key);

        long startTime = System.currentTimeMillis();
        Optional<byte[]> value = store.getValueAt(keyHash, version);

        // Record metrics
        long durationMs = System.currentTimeMillis() - startTime;
        metrics.recordRead(durationMs, false, value.isPresent());

        return value;
    }

    /**
     * Generates a proof of inclusion or non-inclusion for a key at a specific version.
     *
     * <p><b>This is the recommended method for untrusted data sources.</b>
     * Following Diem's design philosophy, this method returns a proof that contains both
     * the value (accessible via {@link JmtProof#value()}) and cryptographic proof data
     * that can be independently verified against the root hash.
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Cross-service communication requiring verification</li>
     *   <li>Blockchain state synchronization</li>
     *   <li>Untrusted data sources where cryptographic proof is needed</li>
     *   <li>Audit trails and compliance requirements</li>
     * </ul>
     *
     * <p><b>Performance:</b> 2-3x slower than {@link #get(byte[], long)} due to:
     * <ul>
     *   <li>Tree traversal with neighbor node loading</li>
     *   <li>Proof construction with branch steps</li>
     *   <li>Additional allocations for proof objects</li>
     * </ul>
     *
     * <p><b>Usage Example:</b>
     * <pre>
     * Optional&lt;JmtProof&gt; proofOpt = tree.getProof(key, version);
     * if (proofOpt.isPresent()) {
     *     JmtProof proof = proofOpt.get();
     *     byte[] value = proof.value();  // Extract value from proof
     *
     *     // Verify the proof
     *     byte[] rootHash = store.rootHash(version).orElseThrow();
     *     boolean valid = JmtProofVerifier.verify(rootHash, key, value, proof, hashFn, commitments);
     * }
     * </pre>
     *
     * @param key     the key to look up (will be hashed)
     * @param version the tree version to query
     * @return proof containing both value and cryptographic proof data, or empty if version doesn't exist
     * @see JmtProof#value()
     * @see JmtProofVerifier
     */
    public Optional<JmtProof> getProof(byte[] key, long version) {
        Objects.requireNonNull(key, "key");

        long startTime = System.currentTimeMillis();

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
        // Use depth indexing to avoid concat() allocations during traversal
        List<JmtProof.BranchStep> steps = new ArrayList<>();
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
                    // Use depth directly instead of materializing path (zero-allocation)
                    NibblePath suffix = fullPath.slice(depth, fullPath.length());

                    JmtProof proof = JmtProof.inclusion(
                            steps,
                            value,
                            leaf.valueHash(),
                            suffix,
                            leaf.keyHash()
                    );

                    // Record metrics
                    long durationMs = System.currentTimeMillis() - startTime;
                    metrics.recordProofGeneration(durationMs, steps.size(), true);

                    return Optional.of(proof);
                } else {
                    // Non-inclusion proof - found a different leaf
                    int[] fullNibbles = Nibbles.toNibbles(leaf.keyHash());
                    NibblePath fullPath = NibblePath.of(fullNibbles);
                    // Use depth directly instead of materializing path (zero-allocation)
                    NibblePath suffix = fullPath.slice(depth, fullPath.length());

                    JmtProof proof = JmtProof.nonInclusionDifferentLeaf(
                            steps,
                            leaf.keyHash(),
                            leaf.valueHash(),
                            suffix
                    );

                    // Record metrics
                    long durationMs = System.currentTimeMillis() - startTime;
                    metrics.recordProofGeneration(durationMs, steps.size(), false);

                    return Optional.of(proof);
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
                        // Build path: nibbles[0..depth-1] + idx
                        // We need to construct a path with one more nibble (the neighbor idx)
                        int[] neighborNibbles = new int[depth + 1];
                        System.arraycopy(nibbles, 0, neighborNibbles, 0, depth);
                        neighborNibbles[depth] = idx;
                        NibblePath neighborPath = NibblePath.fromRaw(neighborNibbles);
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

                // Materialize current path for BranchStep (only when needed)
                NibblePath currentPath = NibblePath.fromRange(nibbles, 0, depth);

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
                    long durationMs = System.currentTimeMillis() - startTime;
                    metrics.recordProofGeneration(durationMs, steps.size(), false);
                    return Optional.of(JmtProof.nonInclusionEmpty(steps));
                }

                // Navigate to child: increment depth to include this nibble in the path
                depth++;
                NibblePath childPath = NibblePath.fromRange(nibbles, 0, depth);
                currentEntryOpt = store.getNode(version, childPath);
            } else {
                throw new IllegalStateException("Unknown node type: " + node.getClass());
            }
        }

        // Path doesn't exist - non-inclusion proof
        long durationMs = System.currentTimeMillis() - startTime;
        metrics.recordProofGeneration(durationMs, steps.size(), false);
        return Optional.of(JmtProof.nonInclusionEmpty(steps));
    }

    /**
     * Generates a proof in CBOR wire format for a key at a specific version.
     *
     * <p>This is a convenience method that combines {@link #getProof(byte[], long)} with
     * CBOR encoding via {@link com.bloxbean.cardano.vds.jmt.proof.ClassicJmtProofCodec}.
     * The wire format is a CBOR array of encoded nodes (internal nodes + optional leaf),
     * following the classic JMT proof format inspired by Diem.
     *
     * <p><b>Wire Format Structure:</b>
     * <pre>
     * [
     *   &lt;InternalNode CBOR&gt;,  // Branch step 1
     *   &lt;InternalNode CBOR&gt;,  // Branch step 2
     *   ...
     *   &lt;LeafNode CBOR&gt;      // Terminal (if applicable)
     * ]
     * </pre>
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Network transmission of proofs (RPC APIs, sync protocols)</li>
     *   <li>Proof persistence and caching</li>
     *   <li>Cross-service proof verification</li>
     *   <li>Diem-inspired proof format structure</li>
     * </ul>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * Optional<byte[]> wireOpt = tree.getProofWire("alice".getBytes(), 1L);
     * if (wireOpt.isPresent()) {
     *     byte[] wire = wireOpt.get();
     *     // Transmit or store wire format proof
     *
     *     // Later: verify the wire proof
     *     byte[] rootHash = store.rootHash(1L).orElseThrow();
     *     boolean valid = tree.verifyProofWire(rootHash, "alice".getBytes(), value, true, wire);
     * }
     * }</pre>
     *
     * @param key     the key to generate proof for (will be hashed)
     * @param version the tree version to query
     * @return CBOR-encoded proof bytes, or empty if version doesn't exist
     * @see #verifyProofWire(byte[], byte[], byte[], boolean, byte[])
     * @see com.bloxbean.cardano.vds.jmt.proof.ClassicJmtProofCodec
     */
    public Optional<byte[]> getProofWire(byte[] key, long version) {
        Objects.requireNonNull(key, "key");

        return getProof(key, version).map(proof ->
            proofCodec.toWire(proof, key, hashFn, commitments)
        );
    }

    /**
     * Verifies a CBOR wire format proof against a root hash.
     *
     * <p>This method decodes and verifies a wire format proof generated by
     * {@link #getProofWire(byte[], long)}. It follows the classic JMT verification
     * algorithm, reconstructing the root hash from the proof and comparing it
     * against the expected root.
     *
     * <p><b>Verification Process:</b>
     * <ol>
     *   <li>Decode CBOR array into node sequence</li>
     *   <li>Traverse nodes following key nibbles</li>
     *   <li>Reconstruct root hash from leaf up through internal nodes</li>
     *   <li>Compare computed root with expected root</li>
     * </ol>
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Verify proofs received over network without trusting the sender</li>
     *   <li>Light client verification without full tree access</li>
     *   <li>Audit and compliance verification</li>
     *   <li>Cross-chain state proof verification</li>
     * </ul>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Generate proof
     * byte[] rootHash = store.rootHash(1L).orElseThrow();
     * byte[] wire = tree.getProofWire("alice".getBytes(), 1L).orElseThrow();
     *
     * // Verify proof (can be done without tree instance)
     * boolean valid = tree.verifyProofWire(
     *     rootHash,
     *     "alice".getBytes(),
     *     "balance:1000".getBytes(),
     *     true,  // inclusion proof
     *     wire
     * );
     *
     * if (valid) {
     *     // Proof is cryptographically valid
     *     // Value "balance:1000" exists for key "alice" at this root
     * }
     * }</pre>
     *
     * @param expectedRoot the expected root hash to verify against
     * @param key          the key being proved (will be hashed)
     * @param value        the value being proved (null for non-inclusion)
     * @param including    true for inclusion proof, false for non-inclusion
     * @param wire         the CBOR-encoded proof bytes
     * @return true if proof is valid and root hash matches, false otherwise
     * @see #getProofWire(byte[], long)
     * @see com.bloxbean.cardano.vds.jmt.proof.ClassicJmtProofCodec
     */
    public boolean verifyProofWire(byte[] expectedRoot, byte[] key, byte[] value,
                                   boolean including, byte[] wire) {
        Objects.requireNonNull(expectedRoot, "expectedRoot");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(wire, "wire");

        return proofCodec.verify(expectedRoot, key, value, including, wire, hashFn, commitments);
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
        int[] keyNibbles = Nibbles.toNibbles(keyHash);

        // Get current root
        NodeKey rootKey = cache.getRootNodeKey();

        // Insert at root and get new root key
        NodeKey newRootKey = insertAt(rootKey, keyNibbles, 0, keyHash, valueHash, cache);

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
            int[] keyNibbles,
            int depth,
            byte[] keyHash,
            byte[] valueHash,
            TreeCache cache) {

        long version = cache.nextVersion();

        // Retrieve current node (or empty if doesn't exist)
        Optional<NodeEntry> nodeOpt = cache.getNode(nodeKey);

        if (!nodeOpt.isPresent()) {
            // Empty position - create new leaf
            return createLeaf(nodeKey.path(), version, keyHash, valueHash, cache);
        }

        JmtNode node = nodeOpt.get().node();

        if (node instanceof JmtLeafNode) {
            // Hit existing leaf - need to handle collision
            return insertAtLeaf(nodeKey, (JmtLeafNode) node, keyNibbles, depth, keyHash, valueHash, cache);
        } else if (node instanceof JmtInternalNode) {
            // Traverse internal node
            return insertAtInternal(nodeKey, (JmtInternalNode) node, keyNibbles, depth, keyHash, valueHash, cache);
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
            int[] keyNibbles,
            int depth,
            byte[] keyHash,
            byte[] valueHash,
            TreeCache cache) {

        long version = cache.nextVersion();

        // CRITICAL: Delete old version first (Diem pattern)
        cache.deleteNode(nodeKey, false /* not a leaf */);

        // Determine which child to update
        int childNibble = keyNibbles[depth];

        // Compute child path
        NibblePath childPath = prefixPath(keyNibbles, depth + 1);

        // Find the existing child's version (or use current version if new child)
        long childVersion = findChildVersion(childPath, cache, version);

        // Generate child node key with correct version
        NodeKey childKey = NodeKey.of(childPath, childVersion);

        // Recurse into child
        NodeKey newChildKey = insertAt(childKey, keyNibbles, depth + 1, keyHash, valueHash, cache);

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
            int[] keyNibbles,
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

        int divergeDepth = depth;
        while (divergeDepth < existingNibbles.length &&
               divergeDepth < keyNibbles.length &&
               existingNibbles[divergeDepth] == keyNibbles[divergeDepth]) {
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
                keyNibbles,
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

        int[] currentPrefix = currentPath.getNibbles();
        int sharedPrefix = Math.max(0, divergeDepth - currentDepth);

        int[] divergenceNibbles;
        if (sharedPrefix == 0) {
            divergenceNibbles = Arrays.copyOf(currentPrefix, currentPrefix.length);
        } else {
            divergenceNibbles = new int[currentPrefix.length + sharedPrefix];
            System.arraycopy(currentPrefix, 0, divergenceNibbles, 0, currentPrefix.length);
            System.arraycopy(existingNibbles, currentDepth, divergenceNibbles, currentPrefix.length, sharedPrefix);
        }
        NibblePath divergencePath = NibblePath.fromRaw(divergenceNibbles);

        int existingChildNibble = existingNibbles[divergeDepth];
        int newChildNibble = newNibbles[divergeDepth];

        int[] existingLeafNibbles = Arrays.copyOf(divergenceNibbles, divergenceNibbles.length + 1);
        existingLeafNibbles[existingLeafNibbles.length - 1] = existingChildNibble;
        NibblePath existingLeafPath = NibblePath.fromRaw(existingLeafNibbles);
        createLeaf(existingLeafPath, version, existingKeyHash, existingValueHash, cache);

        int[] newLeafNibbles = Arrays.copyOf(divergenceNibbles, divergenceNibbles.length + 1);
        newLeafNibbles[newLeafNibbles.length - 1] = newChildNibble;
        NibblePath newLeafPath = NibblePath.fromRaw(newLeafNibbles);
        createLeaf(newLeafPath, version, newKeyHash, newValueHash, cache);

        byte[] existingHash = computeLeafHash(existingLeafPath, existingNibbles, existingValueHash);
        byte[] newHash = computeLeafHash(newLeafPath, newNibbles, newValueHash);

        byte[][] childHashes = new byte[2][];
        if (existingChildNibble < newChildNibble) {
            childHashes[0] = existingHash;
            childHashes[1] = newHash;
        } else {
            childHashes[0] = newHash;
            childHashes[1] = existingHash;
        }

        int bitmap = (1 << existingChildNibble) | (1 << newChildNibble);
        JmtInternalNode internalNode = JmtInternalNode.of(bitmap, childHashes, null);
        byte[] internalHash = computeInternalHash(divergencePath, internalNode);
        NodeKey currentKey = NodeKey.of(divergencePath, version);
        cache.putNode(currentKey, internalNode);

        byte[] currentHash = internalHash;
        for (int depth = divergeDepth - 1; depth >= currentDepth; depth--) {
            int extension = Math.max(0, depth - currentDepth);
            int totalLength = currentPrefix.length + extension;
            int[] parentNibbles = new int[totalLength];
            System.arraycopy(currentPrefix, 0, parentNibbles, 0, currentPrefix.length);
            for (int offset = 0; offset < extension; offset++) {
                parentNibbles[currentPrefix.length + offset] = existingNibbles[currentDepth + offset];
            }

            NibblePath parentPath = NibblePath.fromRaw(parentNibbles);
            int childNibble = existingNibbles[depth];
            byte[][] parentChildHashes = new byte[][]{currentHash};
            JmtInternalNode parentNode = JmtInternalNode.of(1 << childNibble, parentChildHashes, null);

            currentHash = computeInternalHash(parentPath, parentNode);
            currentKey = NodeKey.of(parentPath, version);
            cache.putNode(currentKey, parentNode);
        }

        return currentKey;
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
        Optional<NodeEntry> childEntry = cache.getNode(newChildKey);
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
        // We need to try multiple versions: first the parent version (most common case),
        // then fall back to checking if the child exists from an earlier frozen transaction
        NodeKey childKey = NodeKey.of(childPath, parentVersion);
        Optional<TreeCache.NodeEntry> childEntry = cache.getNode(childKey);
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
        return computeLeafHash(leafPath, fullNibbles, valueHash);
    }

    private byte[] computeLeafHash(NibblePath leafPath, int[] keyNibbles, byte[] valueHash) {
        int pathLen = leafPath.length();
        if (pathLen >= keyNibbles.length) {
            return commitments.commitLeaf(NibblePath.EMPTY, valueHash);
        }

        NibblePath suffix = NibblePath.fromRange(keyNibbles, pathLen, keyNibbles.length - pathLen);
        return commitments.commitLeaf(suffix, valueHash);
    }

    private NibblePath prefixPath(int[] nibbles, int length) {
        if (length == 0) {
            return NibblePath.EMPTY;
        }
        return NibblePath.fromRange(nibbles, 0, length);
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
            for (Map.Entry<NodeKey, NodeEntry> entry : batch.nodes().entrySet()) {
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

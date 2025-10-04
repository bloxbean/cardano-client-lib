package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.proof.ClassicJmtProofCodec;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Store-backed Jellyfish Merkle Tree façade built around the classic JMT
 * commitment and proof semantics.
 *
 * <p>REFERENCE mode defers to the in-memory reference JMT for correctness,
 * persisting results to the provided store. STREAMING mode performs lazy
 * lookups and streams updates via {@link JmtStore.CommitBatch} while preserving
 * proof behaviour.</p>
 */
public final class JellyfishMerkleTreeStore {

    private final Engine engine;
    private final CommitmentScheme commitments;
    private final HashFunction hashFn;
    private final ClassicJmtProofCodec proofCodec;

    public JellyfishMerkleTreeStore(JmtStore store,
                                    HashFunction hashFn) {
        this(store, null, hashFn, EngineMode.STREAMING, JellyfishMerkleTreeStoreConfig.defaults());
    }

    public JellyfishMerkleTreeStore(JmtStore store,
                                    CommitmentScheme commitments,
                                    HashFunction hashFn) {
        this(store, commitments, hashFn, EngineMode.STREAMING, JellyfishMerkleTreeStoreConfig.defaults());
    }

    public JellyfishMerkleTreeStore(JmtStore store,
                                    CommitmentScheme commitments,
                                    HashFunction hashFn,
                                    EngineMode mode) {
        this(store, commitments, hashFn, mode, JellyfishMerkleTreeStoreConfig.defaults());
    }

    public JellyfishMerkleTreeStore(JmtStore store,
                                    CommitmentScheme commitments,
                                    HashFunction hashFn,
                                    EngineMode mode,
                                    JellyfishMerkleTreeStoreConfig config) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(hashFn, "hashFn");
        EngineMode effective = mode == null ? EngineMode.STREAMING : mode;
        this.hashFn = hashFn;
        this.commitments = commitments != null ? commitments : new ClassicJmtCommitmentScheme(hashFn);
        this.proofCodec = new ClassicJmtProofCodec();
        this.engine = (effective == EngineMode.REFERENCE)
                ? new ReferenceEngine(store, this.commitments, hashFn)
                : new StreamingEngine(store, this.commitments, hashFn, Objects.requireNonNullElse(config, JellyfishMerkleTreeStoreConfig.defaults()));
    }

    public Optional<Long> latestVersion() {
        return engine.latestVersion();
    }

    public byte[] latestRootHash() {
        return engine.latestRootHash();
    }

    public byte[] rootHash(long version) {
        return engine.rootHash(version);
    }

    public synchronized JellyfishMerkleTree.CommitResult commit(long version, Map<byte[], byte[]> updates) {
        return engine.commit(version, updates);
    }

    public byte[] get(byte[] key) {
        return engine.get(key);
    }

    public byte[] get(byte[] key, long version) {
        return engine.get(key, version);
    }

    public Optional<JmtProof> getProof(byte[] key, long version) {
        return engine.getProof(key, version);
    }

    /**
     * Encodes a classic JMT proof using the wire format (CBOR node list).
     */
    public Optional<byte[]> getProofWire(byte[] key, long version) {
        Objects.requireNonNull(key, "key");
        return getProof(key, version).map(p -> proofCodec.toWire(p, key, hashFn, commitments));
    }

    /**
     * Verifies a classic wire proof (CBOR node list).
     */
    public boolean verifyProofWire(byte[] expectedRoot, byte[] key, byte[] valueOrNull,
                                   boolean including, byte[] wire) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(wire, "wire");
        return proofCodec.verify(expectedRoot, key, valueOrNull, including, wire, hashFn, commitments);
    }

    public PruneReport prune(long versionInclusive) {
        return engine.prune(versionInclusive);
    }

    public void truncateAfter(long version) {
        engine.truncateAfter(version);
    }

    interface Engine {
        Optional<Long> latestVersion();

        byte[] latestRootHash();

        byte[] rootHash(long version);

        JellyfishMerkleTree.CommitResult commit(long version, Map<byte[], byte[]> updates);

        byte[] get(byte[] key);

        byte[] get(byte[] key, long version);

        Optional<JmtProof> getProof(byte[] key, long version);

        PruneReport prune(long versionInclusive);

        void truncateAfter(long version);
    }

    static final class ReferenceEngine implements Engine {
        private final JmtStore store;
        private final JellyfishMerkleTree delegate;

        ReferenceEngine(JmtStore store, CommitmentScheme commitments, HashFunction hashFn) {
            this.store = store;
            this.delegate = new JellyfishMerkleTree(commitments, hashFn);
        }

        @Override
        public Optional<Long> latestVersion() {
            return delegate.latestVersion();
        }

        @Override
        public byte[] latestRootHash() {
            return delegate.latestRootHash();
        }

        @Override
        public byte[] rootHash(long version) {
            return delegate.rootHash(version);
        }

        @Override
        public JellyfishMerkleTree.CommitResult commit(long version, Map<byte[], byte[]> updates) {
            JellyfishMerkleTree.CommitResult result = delegate.commit(version, updates);
            persist(result);
            return result;
        }

        private void persist(JellyfishMerkleTree.CommitResult result) {
            try (JmtStore.CommitBatch batch = store.beginCommit(result.version(), JmtStore.CommitConfig.defaults())) {
                for (Map.Entry<NodeKey, JmtNode> e : result.nodes().entrySet()) batch.putNode(e.getKey(), e.getValue());
                for (NodeKey stale : result.staleNodes()) batch.markStale(stale);
                for (JellyfishMerkleTree.CommitResult.ValueOperation op : result.valueOperations()) {
                    switch (op.type()) {
                        case PUT:
                            batch.putValue(op.keyHash(), op.value());
                            break;
                        case DELETE:
                            batch.deleteValue(op.keyHash());
                            break;
                    }
                }
                batch.setRootHash(result.rootHash());
                batch.commit();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to persist JMT commit", e);
            }
        }

        @Override
        public byte[] get(byte[] key) {
            return delegate.get(key);
        }

        @Override
        public byte[] get(byte[] key, long version) {
            return delegate.get(key, version);
        }

        @Override
        public Optional<JmtProof> getProof(byte[] key, long version) {
            return delegate.getProof(key, version);
        }

        @Override
        public PruneReport prune(long versionInclusive) {
            int nodesPruned = store.pruneUpTo(versionInclusive);
            return new PruneReport(versionInclusive, nodesPruned, 0, 0);
        }

        @Override
        public void truncateAfter(long version) {
            throw new UnsupportedOperationException("truncateAfter not supported in REFERENCE mode");
        }
    }

    static final class StreamingEngine implements Engine {
        private final JmtStore store;
        private final CommitmentScheme commitments;
        private final HashFunction hashFn;

        private final JellyfishMerkleTreeStoreConfig config;
        private final JmtMetrics metrics;
        private final NodeCache nodeCache;
        private final NegativeNodeCache negativeNodeCache;
        private final ValueCache valueCache;

        StreamingEngine(JmtStore store, CommitmentScheme commitments, HashFunction hashFn, JellyfishMerkleTreeStoreConfig config) {
            this.store = store;
            this.commitments = commitments;
            this.hashFn = hashFn;
            this.config = Objects.requireNonNull(config, "config");
            this.metrics = this.config.metrics();
            this.nodeCache = config.nodeCacheEnabled() ? new NodeCache(config.nodeCacheSize()) : null;
            this.negativeNodeCache = config.nodeCacheEnabled() ? new NegativeNodeCache(config.nodeCacheSize()) : null;
            this.valueCache = config.valueCacheEnabled() ? new ValueCache(config.valueCacheSize()) : null;
        }

        @Override
        public Optional<Long> latestVersion() {
            return store.latestRoot().map(JmtStore.VersionedRoot::version);
        }

        @Override
        public byte[] latestRootHash() {
            return store.latestRoot()
                    .map(JmtStore.VersionedRoot::rootHash)
                    .orElseGet(() -> commitments.nullHash().clone());
        }

        @Override
        public byte[] rootHash(long version) {
            return store.rootHash(version)
                    .orElseGet(() -> commitments.nullHash().clone());
        }

        @Override
        public JellyfishMerkleTree.CommitResult commit(long version, Map<byte[], byte[]> updates) {
            Objects.requireNonNull(updates, "updates");
            Optional<JmtStore.VersionedRoot> latestRoot = store.latestRoot();
            long baseVersion = latestRoot.map(JmtStore.VersionedRoot::version).orElse(-1L);
            if (baseVersion >= 0 && Long.compareUnsigned(version, baseVersion) <= 0) {
                throw new IllegalArgumentException("version must be greater than latest committed version");
            }

            byte[] baseRootHash = latestRoot
                    .map(JmtStore.VersionedRoot::rootHash)
                    .orElseGet(() -> commitments.nullHash().clone());

            if (updates.isEmpty()) {
                return JellyfishMerkleTree.CommitResult.streaming(
                        version,
                        baseRootHash.clone(),
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyList(),
                        java.util.Collections.emptyList());
            }

            WorkingSet working = new WorkingSet(baseVersion, version, baseRootHash);
            for (Map.Entry<byte[], byte[]> entry : updates.entrySet()) {
                byte[] key = Objects.requireNonNull(entry.getKey(), "update key");
                byte[] value = entry.getValue();
                if (value == null) {
                    working.delete(key);
                } else {
                    working.put(key, value);
                }
            }

            try (JmtStore.CommitBatch batch = store.beginCommit(version, JmtStore.CommitConfig.defaults())) {
                long t0 = System.currentTimeMillis();
                working.flush(batch);
                JellyfishMerkleTree.CommitResult res = working.commitResult();
                long elapsed = System.currentTimeMillis() - t0;
                int puts = res.nodes().size() + (int) res.valueOperations().stream().filter(op -> op.type() == JellyfishMerkleTree.CommitResult.ValueOperation.Type.PUT).count();
                int dels = (int) res.valueOperations().stream().filter(op -> op.type() == JellyfishMerkleTree.CommitResult.ValueOperation.Type.DELETE).count() + res.staleNodes().size();
                metrics.recordCommit(elapsed, puts, dels);
                return res;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to persist JMT commit", e);
            }
        }

        @Override
        public byte[] get(byte[] key) {
            Optional<Long> latest = latestVersion();
            if (!latest.isPresent()) {
                return null;
            }
            return get(key, latest.get());
        }

        @Override
        public byte[] get(byte[] key, long version) {
            Objects.requireNonNull(key, "key");
            if (!versionExists(version)) {
                return null;
            }
            byte[] keyHash = hashFn.digest(key);
            if (!leafExistsAt(keyHash, version)) {
                return null;
            }
            Optional<byte[]> cached = cachedValue(keyHash, version);
            metrics.valueCacheHit(cached.isPresent());
            if (cached.isPresent()) {
                return cached.get().clone();
            }
            return store.getValueAt(keyHash, version)
                    .map(value -> {
                        cacheValue(keyHash, version, value);
                        return value.clone();
                    })
                    .orElse(null);
        }

        @Override
        public Optional<JmtProof> getProof(byte[] key, long version) {
            Objects.requireNonNull(key, "key");
            if (!versionExists(version)) {
                return Optional.empty();
            }
            return computeStoreProof(key, version);
        }

        private Optional<JmtProof> computeStoreProof(byte[] key, long version) {
            byte[] keyHash = hashFn.digest(key);
            int[] nibbles = com.bloxbean.cardano.statetrees.common.nibbles.Nibbles.toNibbles(keyHash);
            com.bloxbean.cardano.statetrees.common.NibblePath fullPath = com.bloxbean.cardano.statetrees.common.NibblePath.of(nibbles);

            // Check if the key has a value (not deleted) at this version
            // If the key was deleted (value is null), we treat it as non-existent
            boolean hasValue = store.getValueAt(keyHash, version).isPresent();

            java.util.List<JmtProof.BranchStep> steps = new java.util.ArrayList<>();
            int fallbackStepIndex = -1;
            com.bloxbean.cardano.statetrees.common.NibblePath prefix = com.bloxbean.cardano.statetrees.common.NibblePath.EMPTY;
            java.util.Optional<JmtStore.NodeEntry> current = fetchNode(version, prefix);
            if (!current.isPresent() && hasValue) {
                current = fetchNode(version, fullPath);
                if (!current.isPresent()) {
                    current = findDescendant(version, fullPath);
                }
            }
            if (!current.isPresent()) {
                current = findDescendant(version, prefix);
                if (!hasValue && current.isPresent()) {
                    System.out.println("[JMT debug] missing key version=" + version + " resolved to node "
                            + current.get().node().getClass().getSimpleName() + " pathLen="
                            + current.get().nodeKey().path().length());
                }
            }
            if (!current.isPresent()) {
                return java.util.Optional.of(JmtProof.nonInclusionEmpty(java.util.Collections.emptyList()));
            }

            prefix = current.get().nodeKey().path();
            int depth = (current.get().node() instanceof JmtLeafNode) ? steps.size() : prefix.length();
            while (current.isPresent() && current.get().node() instanceof JmtInternalNode) {
                JmtInternalNode internal = (JmtInternalNode) current.get().node();
                byte[][] fullHashes = expandChildHashes(internal);
                int nibble = depth < nibbles.length ? nibbles[depth] : 0;
                boolean hasChild = depth < nibbles.length && fullHashes[nibble] != null;
                NeighborInfo neighbor = neighborInfo(version, prefix, internal, nibble);
                steps.add(new JmtProof.BranchStep(prefix, cloneMatrix(fullHashes), nibble,
                        neighbor.singleNeighbor, neighbor.neighborNibble,
                        neighbor.forkPrefix, neighbor.forkRoot,
                        neighbor.leafNeighborKeyHash, neighbor.leafNeighborValueHash));
                if (!hasChild) {
                    return java.util.Optional.of(JmtProof.nonInclusionEmpty(steps));
                }

                // Descend towards target child. If the direct child node is not materialized
                // at prefix+Nibble (because it may be a leaf deeper down), try to locate any
                // descendant node under that prefix using floorNode on an upper bound path.
                com.bloxbean.cardano.statetrees.common.NibblePath childPrefix = append(prefix, nibble);
                java.util.Optional<JmtStore.NodeEntry> next = fetchNode(version, childPrefix);
                if (!next.isPresent()) {
                    boolean advanced = false;
                    if (hasValue) {
                        // Walk down along the target key path to find the next materialized node (internal or leaf).
                        for (int j = childPrefix.length() + 1; j <= fullPath.length(); j++) {
                            com.bloxbean.cardano.statetrees.common.NibblePath pfx = fullPath.slice(0, j);
                            java.util.Optional<JmtStore.NodeEntry> e = fetchNode(version, pfx);
                            if (e.isPresent()) {
                                current = e;
                                prefix = e.get().nodeKey().path();
                                depth = prefix.length();
                                advanced = true;
                                break;
                            }
                        }
                        if (advanced) {
                            continue;
                        }
                    }
                    if (!hasValue) {
                        return java.util.Optional.of(JmtProof.nonInclusionEmpty(steps));
                    }
                    // Otherwise, find any descendant under this child prefix (e.g., neighbor leaf).
                    java.util.Optional<JmtStore.NodeEntry> descendant = findDescendant(version, childPrefix);
                    if (descendant.isPresent()) {
                        fallbackStepIndex = steps.size() - 1;
                        current = descendant;
                        prefix = descendant.get().nodeKey().path();
                        depth = prefix.length();
                        continue;
                    }
                    current = java.util.Optional.empty();
                    break;
                } else {
                    prefix = childPrefix;
                    current = next;
                    depth++;
                }
            }

            if (!current.isPresent() || !(current.get().node() instanceof JmtLeafNode)) {
                return java.util.Optional.of(JmtProof.nonInclusionEmpty(steps));
            }

            JmtLeafNode leaf = (JmtLeafNode) current.get().node();
            if (java.util.Arrays.equals(leaf.keyHash(), keyHash)) {
                byte[] value = store.getValueAt(keyHash, version).orElse(null);
                // Calculate suffix as the untraversed portion of the target key's path
                // This matches Diem's approach where verification uses the target key's path
                com.bloxbean.cardano.statetrees.common.NibblePath suffix = fullPath.slice(steps.size(), fullPath.length());
                // Check if the key actually exists at this version (not deleted)
                if (value != null) {
                    return java.util.Optional.of(JmtProof.inclusion(
                            java.util.Collections.unmodifiableList(steps),
                            value,
                            leaf.valueHash(),
                            suffix,
                            leaf.keyHash()));
                } else {
                    // Key was deleted - treat as non-inclusion (empty position)
                    return java.util.Optional.of(JmtProof.nonInclusionEmpty(
                            java.util.Collections.unmodifiableList(steps)));
                }
            } else {
                com.bloxbean.cardano.statetrees.common.NibblePath suffix = current.get().nodeKey().path()
                        .slice(depth, current.get().nodeKey().path().length());
                if (!steps.isEmpty()) {
                    int targetIndex = fallbackStepIndex >= 0 ? fallbackStepIndex : steps.size() - 1;
                    JmtProof.BranchStep target = steps.get(targetIndex);
                    byte[][] adjusted = target.childHashes();
                    int childIdx = target.childIndex();
                    if (adjusted[childIdx] == null) {
                        byte[] leafDigest = commitments.commitLeaf(suffix, leaf.valueHash());
                        adjusted[childIdx] = leafDigest;
                    }
                    steps.set(targetIndex, new JmtProof.BranchStep(
                            target.prefix(),
                            adjusted,
                            target.childIndex(),
                            target.hasSingleNeighbor(),
                            target.neighborNibble(),
                            target.forkNeighborPrefix(),
                            target.forkNeighborRoot(),
                            target.leafNeighborKeyHash(),
                            target.leafNeighborValueHash()));
                }
                return java.util.Optional.of(JmtProof.nonInclusionDifferentLeaf(
                        steps,
                        leaf.keyHash(),
                        leaf.valueHash(),
                        suffix));
            }
        }

        /**
         * Attempts to locate any node that is a descendant of the supplied prefix at the given version.
         * This is used when an internal node reports a child at a nibble, but the concrete child node
         * is not directly materialized at prefix+Nibble (e.g., the child is a leaf deeper down).
         */
        private java.util.Optional<JmtStore.NodeEntry> findDescendant(long version,
                                                                       com.bloxbean.cardano.statetrees.common.NibblePath prefix) {
            // Construct an upper bound path by appending a large number of 0xF nibbles to ensure
            // we seek past any descendants under this prefix, then use floorNode to get the last
            // node <= upperBound and check if it shares the prefix.
            int[] base = prefix.getNibbles();
            int extra = 128; // generous upper bound beyond any realistic path length
            int[] upper = java.util.Arrays.copyOf(base, base.length + extra);
            java.util.Arrays.fill(upper, base.length, upper.length, 0xF);
            com.bloxbean.cardano.statetrees.common.NibblePath upperBound =
                    com.bloxbean.cardano.statetrees.common.NibblePath.of(upper);

            java.util.Optional<JmtStore.NodeEntry> floor = store.floorNode(version, upperBound);
            if (!floor.isPresent()) return java.util.Optional.empty();
            com.bloxbean.cardano.statetrees.common.NibblePath candidate = floor.get().nodeKey().path();
            int cpl = commonPrefixLength(prefix, candidate);
            return (cpl >= prefix.length()) ? floor : java.util.Optional.empty();
        }

        private int commonPrefixLength(com.bloxbean.cardano.statetrees.common.NibblePath a,
                                       com.bloxbean.cardano.statetrees.common.NibblePath b) {
            int[] an = a.getNibbles();
            int[] bn = b.getNibbles();
            int len = Math.min(an.length, bn.length);
            int idx = 0;
            while (idx < len && an[idx] == bn[idx]) {
                idx++;
            }
            return idx;
        }

        private NeighborInfo neighborInfo(long version,
                                          com.bloxbean.cardano.statetrees.common.NibblePath prefix,
                                          JmtInternalNode internal,
                                          int targetNibble) {
            byte[][] full = expandChildHashes(internal);
            int neighborNibble = -1;
            int count = 0;
            for (int i = 0; i < 16; i++) {
                if (i == targetNibble) continue;
                if (full[i] != null) {
                    neighborNibble = i;
                    count++;
                    if (count > 1) break;
                }
            }
            NeighborInfo info = new NeighborInfo();
            if (count == 1) {
                info.singleNeighbor = true;
                info.neighborNibble = neighborNibble;
                java.util.Optional<JmtStore.NodeEntry> neighbor = fetchNode(version, append(prefix, neighborNibble));
                if (neighbor.isPresent()) {
                    JmtNode n = neighbor.get().node();
                    if (n instanceof JmtLeafNode) {
                        JmtLeafNode ln = (JmtLeafNode) n;
                        info.leafNeighborKeyHash = ln.keyHash();
                        info.leafNeighborValueHash = ln.valueHash();
                    } else if (n instanceof JmtInternalNode) {
                        info.forkPrefix = neighbor.get().nodeKey().path();
                        info.forkRoot = commitments.commitBranch(info.forkPrefix, expandChildHashes((JmtInternalNode) n));
                    }
                }
            }
            return info;
        }

        private boolean leafExistsAt(byte[] keyHash, long version) {
            com.bloxbean.cardano.statetrees.common.NibblePath path = com.bloxbean.cardano.statetrees.common.NibblePath.of(
                    com.bloxbean.cardano.statetrees.common.nibbles.Nibbles.toNibbles(keyHash));
            java.util.Optional<JmtStore.NodeEntry> e = fetchNode(version, path);
            if (!e.isPresent()) return false;
            if (!(e.get().node() instanceof JmtLeafNode)) return false;
            JmtLeafNode leaf = (JmtLeafNode) e.get().node();
            return java.util.Arrays.equals(leaf.keyHash(), keyHash);
        }

        @Override
        public PruneReport prune(long versionInclusive) {
            int nodesPruned = store.pruneUpTo(versionInclusive);
            int cacheEvicted = 0;
            if (nodeCache != null) {
                cacheEvicted += nodeCache.evictUpTo(versionInclusive);
            }
            if (negativeNodeCache != null) {
                cacheEvicted += negativeNodeCache.evictUpTo(versionInclusive);
            }
            int valuesCleared = 0;
            if (valueCache != null) {
                valuesCleared = valueCache.size();
                valueCache.clear();
            }
            return new PruneReport(versionInclusive, nodesPruned, cacheEvicted, valuesCleared);
        }

        @Override
        public void truncateAfter(long version) {
            store.truncateAfter(version);
            if (nodeCache != null) {
                nodeCache.evictUpTo(version);
            }
            if (negativeNodeCache != null) {
                negativeNodeCache.evictUpTo(version);
            }
            if (valueCache != null) {
                valueCache.clear();
            }
        }

        private java.util.Optional<JmtStore.NodeEntry> fetchNode(long version, com.bloxbean.cardano.statetrees.common.NibblePath path) {
            if (version < 0) {
                return java.util.Optional.empty();
            }
            NodeAddress address = new NodeAddress(path, version);
            java.util.Optional<JmtStore.NodeEntry> cached = cachedNode(address);
            metrics.nodeCacheHit(cached.isPresent());
            if (cached.isPresent()) {
                return cached;
            }
            if (isNegative(address)) {
                return java.util.Optional.empty();
            }
            java.util.Optional<JmtStore.NodeEntry> entry = store.getNode(version, path);
            if (entry.isPresent()) {
                cacheNodeEntry(entry.get());
                forgetNegative(address);
            } else {
                rememberNegative(address);
            }
            return entry;
        }

        private boolean versionExists(long version) {
            if (version < 0) {
                return false;
            }
            java.util.Optional<JmtStore.VersionedRoot> latest = store.latestRoot();
            if (!latest.isPresent()) {
                return false;
            }
            return Long.compareUnsigned(version, latest.get().version()) <= 0;
        }

        private java.util.Optional<JmtStore.NodeEntry> cachedNode(long version, com.bloxbean.cardano.statetrees.common.NibblePath path) {
            return cachedNode(new NodeAddress(path, version));
        }

        private java.util.Optional<JmtStore.NodeEntry> cachedNode(NodeAddress key) {
            if (nodeCache == null) {
                return java.util.Optional.empty();
            }
            JmtStore.NodeEntry entry = nodeCache.get(key);
            return entry == null ? java.util.Optional.empty() : java.util.Optional.of(entry);
        }

        private boolean isNegative(NodeAddress address) {
            return negativeNodeCache != null && negativeNodeCache.containsKey(address);
        }

        private void rememberNegative(NodeAddress address) {
            if (negativeNodeCache != null) {
                negativeNodeCache.put(address, Boolean.TRUE);
            }
        }

        private void forgetNegative(NodeAddress address) {
            if (negativeNodeCache != null) {
                negativeNodeCache.remove(address);
            }
        }

        private void cacheNode(NodeKey nodeKey, JmtNode node) {
            if (nodeCache == null) {
                return;
            }
            NodeAddress address = new NodeAddress(nodeKey.path(), nodeKey.version());
            nodeCache.put(address, new JmtStore.NodeEntry(nodeKey, node));
            forgetNegative(address);
        }

        private void cacheNodeEntry(JmtStore.NodeEntry entry) {
            if (nodeCache == null) {
                return;
            }
            NodeAddress address = new NodeAddress(entry.nodeKey().path(), entry.nodeKey().version());
            nodeCache.put(address, entry);
            forgetNegative(address);
        }

        private void invalidateNode(NodeKey nodeKey) {
            if (nodeCache == null) {
                return;
            }
            NodeAddress address = new NodeAddress(nodeKey.path(), nodeKey.version());
            nodeCache.remove(address);
            forgetNegative(address);
        }

        private java.util.Optional<byte[]> cachedValue(byte[] keyHash, long version) {
            if (valueCache == null) {
                return java.util.Optional.empty();
            }
            byte[] cached = valueCache.get(new ValueCacheKey(keyHash, version));
            return cached == null ? java.util.Optional.empty() : java.util.Optional.of(cached);
        }

        private void cacheValue(byte[] keyHash, long version, byte[] value) {
            if (valueCache == null) {
                return;
            }
            valueCache.put(new ValueCacheKey(keyHash, version), value.clone());
        }

        private void evictValue(byte[] keyHash) {
            if (valueCache == null) {
                return;
            }
            valueCache.removeEntriesForKey(keyHash);
        }

        private static final class NeighborInfo {
            boolean singleNeighbor;
            int neighborNibble;
            com.bloxbean.cardano.statetrees.common.NibblePath forkPrefix;
            byte[] forkRoot;
            byte[] leafNeighborKeyHash;
            byte[] leafNeighborValueHash;
        }

        private static byte[][] expandChildHashes(JmtInternalNode node) {
            byte[][] compact = node.childHashes();
            byte[][] full = new byte[16][];
            int bitmap = node.bitmap();
            int idx = 0;
            for (int nib = 0; nib < 16; nib++) {
                if ((bitmap & (1 << nib)) != 0) {
                    full[nib] = compact[idx++].clone();
                }
            }
            return full;
        }

        private static byte[][] cloneMatrix(byte[][] matrix) {
            byte[][] clone = new byte[matrix.length][];
            for (int i = 0; i < matrix.length; i++) clone[i] = matrix[i] == null ? null : matrix[i].clone();
            return clone;
        }

        private static JmtInternalNode buildInternalNodeFromFull(byte[][] full) {
            int bitmap = 0;
            java.util.List<byte[]> compact = new java.util.ArrayList<>();
            for (int i = 0; i < 16; i++) {
                if (full[i] != null) {
                    bitmap |= (1 << i);
                    compact.add(full[i].clone());
                }
            }
            byte[][] compactArr = compact.toArray(new byte[0][]);
            return JmtInternalNode.of(bitmap, compactArr, null);
        }

        private static com.bloxbean.cardano.statetrees.common.NibblePath append(
                com.bloxbean.cardano.statetrees.common.NibblePath prefix, int nibble) {
            int[] nibbles = prefix.getNibbles();
            int[] extended = java.util.Arrays.copyOf(nibbles, nibbles.length + 1);
            extended[extended.length - 1] = nibble & 0xF;
            return com.bloxbean.cardano.statetrees.common.NibblePath.of(extended);
        }

        private final class WorkingSet {
            private final long baseVersion;
            private final long newVersion;
            private byte[] currentRootHash;

            private final java.util.Map<com.bloxbean.cardano.statetrees.common.NibblePath, JmtStore.NodeEntry> stagedNodes = new java.util.HashMap<>();
            private final java.util.List<NodeWrite> pendingNodes = new java.util.ArrayList<>();
            private final java.util.LinkedHashSet<NodeKey> pendingStale = new java.util.LinkedHashSet<>();
            private final java.util.LinkedHashMap<NodeKey, JmtNode> resultNodes = new java.util.LinkedHashMap<>();
            private final java.util.List<NodeKey> resultStale = new java.util.ArrayList<>();
            private final java.util.List<JellyfishMerkleTree.CommitResult.ValueOperation> valueOperations = new java.util.ArrayList<>();
            private JmtStore.NodeEntry rootLeafEntry;

            WorkingSet(long baseVersion, long newVersion, byte[] baseRootHash) {
                this.baseVersion = baseVersion;
                this.newVersion = newVersion;
                this.currentRootHash = baseRootHash.clone();
                this.rootLeafEntry = null;
            }

            void put(byte[] rawKey, byte[] value) {
                byte[] valueCopy = java.util.Arrays.copyOf(value, value.length);
                byte[] keyHash = hashFn.digest(rawKey);
                int[] nibbles = com.bloxbean.cardano.statetrees.common.nibbles.Nibbles.toNibbles(keyHash);
                com.bloxbean.cardano.statetrees.common.NibblePath fullPath = com.bloxbean.cardano.statetrees.common.NibblePath.of(nibbles);
                byte[] valueHash = hashFn.digest(valueCopy);

                Traversal traversal = traverse(nibbles, fullPath);
                if (!traversal.terminal.isPresent()) {
                    insertLeaf(traversal, fullPath, keyHash, valueCopy, valueHash);
                    return;
                }

                JmtStore.NodeEntry terminalEntry = traversal.terminal.get();
                if (!(terminalEntry.node() instanceof JmtLeafNode)) {
                    throw new IllegalStateException("Unexpected node type during put traversal");
                }

                JmtLeafNode existingLeaf = (JmtLeafNode) terminalEntry.node();
                if (java.util.Arrays.equals(existingLeaf.keyHash(), keyHash)) {
                    updateLeaf(traversal, fullPath, terminalEntry, keyHash, valueCopy, valueHash);
                } else {
                    collide(traversal, fullPath, keyHash, valueCopy, valueHash, terminalEntry);
                }
            }

            void delete(byte[] rawKey) {
                byte[] keyHash = hashFn.digest(rawKey);
                int[] nibbles = com.bloxbean.cardano.statetrees.common.nibbles.Nibbles.toNibbles(keyHash);
                com.bloxbean.cardano.statetrees.common.NibblePath fullPath = com.bloxbean.cardano.statetrees.common.NibblePath.of(nibbles);

                Traversal traversal = traverse(nibbles, fullPath);
                if (!traversal.terminal.isPresent()) {
                    return;
                }

                JmtStore.NodeEntry terminalEntry = traversal.terminal.get();
                if (!(terminalEntry.node() instanceof JmtLeafNode)) {
                    return;
                }
                JmtLeafNode leaf = (JmtLeafNode) terminalEntry.node();
                if (!java.util.Arrays.equals(leaf.keyHash(), keyHash)) {
                    return;
                }

                recordStale(terminalEntry.nodeKey());
                stagedNodes.remove(fullPath);
                valueOperations.add(JellyfishMerkleTree.CommitResult.ValueOperation.delete(keyHash));
                evictValue(keyHash);

                byte[] childHash = null;
                childHash = propagateUp(traversal.frames, childHash);
                updateRoot(childHash);
                if (childHash == null) {
                    rootLeafEntry = null;
                }
            }

            void flush(JmtStore.CommitBatch batch) {
                for (NodeWrite write : pendingNodes) {
                    batch.putNode(write.key, write.node);
                }
                pendingNodes.clear();

                for (NodeKey stale : pendingStale) {
                    batch.markStale(stale);
                }
                pendingStale.clear();

                for (JellyfishMerkleTree.CommitResult.ValueOperation op : valueOperations) {
                    switch (op.type()) {
                        case PUT:
                            batch.putValue(op.keyHash(), op.value());
                            break;
                        case DELETE:
                            batch.deleteValue(op.keyHash());
                            break;
                    }
                }
                batch.setRootHash(currentRootHash.clone());
                batch.commit();
            }

            JellyfishMerkleTree.CommitResult commitResult() {
                return JellyfishMerkleTree.CommitResult.streaming(
                        newVersion,
                        currentRootHash.clone(),
                        new java.util.LinkedHashMap<>(resultNodes),
                        new java.util.ArrayList<>(resultStale),
                        new java.util.ArrayList<>(valueOperations));
            }

            private Traversal traverse(int[] nibbles,
                                       com.bloxbean.cardano.statetrees.common.NibblePath fullPath) {
                java.util.List<PathFrame> frames = new java.util.ArrayList<>();
                com.bloxbean.cardano.statetrees.common.NibblePath prefix = com.bloxbean.cardano.statetrees.common.NibblePath.EMPTY;
                java.util.Optional<JmtStore.NodeEntry> current = lookup(prefix);
                int depth = 0;
                while (current.isPresent() && current.get().node() instanceof JmtInternalNode) {
                    JmtInternalNode internal = (JmtInternalNode) current.get().node();
                    byte[][] full = expandChildHashes(internal);
                    int nib = depth < nibbles.length ? nibbles[depth] : 0;
                    frames.add(new PathFrame(prefix, current.get(), full, nib));
                    if (depth >= nibbles.length || full[nib] == null) {
                        return new Traversal(frames, java.util.Optional.empty(), depth);
                    }
                    prefix = append(prefix, nib);
                    current = lookup(prefix);
                    depth++;
                }
                if (!current.isPresent()) {
                    if (frames.isEmpty() && rootLeafEntry != null) {
                        return new Traversal(frames, java.util.Optional.of(rootLeafEntry), depth);
                    }
                    if (frames.isEmpty() && baseVersion >= 0) {
                        java.util.Optional<JmtStore.NodeEntry> floor = store.floorNode(baseVersion, fullPath);
                        floor.ifPresent(StreamingEngine.this::cacheNodeEntry);
                        if (floor.isPresent() && !pendingStale.contains(floor.get().nodeKey())) {
                            int prefixLen = commonPrefixLength(fullPath, floor.get().nodeKey().path());
                            return new Traversal(frames, floor, prefixLen);
                        }
                    }
                    java.util.Optional<JmtStore.NodeEntry> leaf = lookup(fullPath);
                    if (leaf.isPresent()) {
                        return new Traversal(frames, leaf, nibbles.length);
                    }
                }
                return new Traversal(frames, current, depth);
            }

            private java.util.Optional<JmtStore.NodeEntry> lookup(com.bloxbean.cardano.statetrees.common.NibblePath path) {
                JmtStore.NodeEntry staged = stagedNodes.get(path);
                if (staged != null) {
                    return java.util.Optional.of(staged);
                }
                if (baseVersion < 0) {
                    return java.util.Optional.empty();
                }
                java.util.Optional<JmtStore.NodeEntry> baseEntry = fetchNode(baseVersion, path);
                if (!baseEntry.isPresent()) {
                    return baseEntry;
                }
                if (pendingStale.contains(baseEntry.get().nodeKey())) {
                    return java.util.Optional.empty();
                }
                return baseEntry;
            }

            private void insertLeaf(Traversal traversal,
                                    com.bloxbean.cardano.statetrees.common.NibblePath fullPath,
                                    byte[] keyHash,
                                    byte[] value,
                                    byte[] valueHash) {
                int prefixLen = traversal.frames.size();
                byte[] leafHash = commitments.commitLeaf(fullPath.slice(prefixLen, fullPath.length()), valueHash);
                JmtLeafNode newLeaf = JmtLeafNode.of(keyHash, valueHash);
                NodeKey leafKey = NodeKey.of(fullPath, newVersion);
                stagedNodes.put(fullPath, new JmtStore.NodeEntry(leafKey, newLeaf));
                recordNode(leafKey, newLeaf);
                valueOperations.add(JellyfishMerkleTree.CommitResult.ValueOperation.put(keyHash, value));
                cacheValue(keyHash, value, newVersion);

                byte[] childHash = leafHash;
                childHash = propagateUp(traversal.frames, childHash);
                updateRoot(childHash);
                if (traversal.frames.isEmpty()) {
                    rootLeafEntry = new JmtStore.NodeEntry(leafKey, newLeaf);
                } else {
                    rootLeafEntry = null;
                }
            }

            private void updateLeaf(Traversal traversal,
                                    com.bloxbean.cardano.statetrees.common.NibblePath fullPath,
                                    JmtStore.NodeEntry existingLeafEntry,
                                    byte[] keyHash,
                                    byte[] value,
                                    byte[] valueHash) {
                recordStale(existingLeafEntry.nodeKey());

                // Use the number of traversed internal frames as the prefix length
                // to compute the leaf suffix, matching the reference implementation.
                int prefixLen = traversal.frames.size();
                byte[] leafHash = commitments.commitLeaf(fullPath.slice(prefixLen, fullPath.length()), valueHash);
                JmtLeafNode newLeaf = JmtLeafNode.of(keyHash, valueHash);
                NodeKey leafKey = NodeKey.of(fullPath, newVersion);
                stagedNodes.put(fullPath, new JmtStore.NodeEntry(leafKey, newLeaf));
                recordNode(leafKey, newLeaf);
                valueOperations.add(JellyfishMerkleTree.CommitResult.ValueOperation.put(keyHash, value));
                cacheValue(keyHash, value, newVersion);

                byte[] childHash = leafHash;
                childHash = propagateUp(traversal.frames, childHash);
                updateRoot(childHash);
                if (traversal.frames.isEmpty()) {
                    rootLeafEntry = new JmtStore.NodeEntry(leafKey, newLeaf);
                }
            }

            private void collide(Traversal traversal,
                                 com.bloxbean.cardano.statetrees.common.NibblePath fullPath,
                                 byte[] newKeyHash,
                                 byte[] newValue,
                                 byte[] newValueHash,
                                 JmtStore.NodeEntry existingLeafEntry) {
                JmtLeafNode existingLeaf = (JmtLeafNode) existingLeafEntry.node();
                com.bloxbean.cardano.statetrees.common.NibblePath existingFullPath = existingLeafEntry.nodeKey().path();

                recordStale(existingLeafEntry.nodeKey());

                NodeKey existingLeafKeyNew = NodeKey.of(existingFullPath, newVersion);
                JmtLeafNode existingLeafCopy = JmtLeafNode.of(existingLeaf.keyHash(), existingLeaf.valueHash());
                stagedNodes.put(existingFullPath, new JmtStore.NodeEntry(existingLeafKeyNew, existingLeafCopy));
                recordNode(existingLeafKeyNew, existingLeafCopy);

                NodeKey newLeafKey = NodeKey.of(fullPath, newVersion);
                JmtLeafNode newLeaf = JmtLeafNode.of(newKeyHash, newValueHash);
                stagedNodes.put(fullPath, new JmtStore.NodeEntry(newLeafKey, newLeaf));
                recordNode(newLeafKey, newLeaf);
                valueOperations.add(JellyfishMerkleTree.CommitResult.ValueOperation.put(newKeyHash, newValue));
                cacheValue(newKeyHash, newValue, newVersion);

                int depth = traversal.depth;
                byte[] existingLeafHash = commitments.commitLeaf(existingFullPath.slice(depth + 1, existingFullPath.length()), existingLeaf.valueHash());
                byte[] newLeafHash = commitments.commitLeaf(fullPath.slice(depth + 1, fullPath.length()), newValueHash);

                int existingNib = existingFullPath.get(depth);
                int newNib = fullPath.get(depth);
                com.bloxbean.cardano.statetrees.common.NibblePath prefix = traversal.frames.isEmpty()
                        ? com.bloxbean.cardano.statetrees.common.NibblePath.EMPTY
                        : traversal.frames.get(traversal.frames.size() - 1).prefix;

                byte[][] full = new byte[16][];
                full[existingNib] = existingLeafHash;
                full[newNib] = newLeafHash;
                byte[] branchHash = commitments.commitBranch(prefix, full);
                JmtInternalNode branch = buildInternalNodeFromFull(full);
                NodeKey branchKey = NodeKey.of(prefix, newVersion);
                stagedNodes.put(prefix, new JmtStore.NodeEntry(branchKey, branch));
                recordNode(branchKey, branch);

                if (!traversal.frames.isEmpty()) {
                    PathFrame parentFrame = traversal.frames.get(traversal.frames.size() - 1);
                    if (parentFrame.entry != null && parentFrame.entry.nodeKey().version() != newVersion) {
                        recordStale(parentFrame.entry.nodeKey());
                    }
                }

                java.util.List<PathFrame> remaining = traversal.frames.isEmpty()
                        ? java.util.Collections.emptyList()
                        : traversal.frames.subList(0, Math.max(0, traversal.frames.size() - 1));

                byte[] childHash = branchHash;
                childHash = propagateUp(remaining, childHash);
                updateRoot(childHash);
                rootLeafEntry = null;
            }

            private byte[] propagateUp(java.util.List<PathFrame> frames, byte[] childHash) {
                for (int i = frames.size() - 1; i >= 0; i--) {
                    PathFrame frame = frames.get(i);
                    byte[][] full = cloneMatrix(frame.fullChildHashes);
                    full[frame.childIndex] = childHash == null ? null : childHash.clone();

                    int childCount = countChildren(full);
                    NodeKey existingKey = frame.entry == null ? null : frame.entry.nodeKey();
                    com.bloxbean.cardano.statetrees.common.NibblePath prefix = frame.prefix;

                    if (childCount == 0) {
                        if (existingKey != null) {
                            recordStale(existingKey);
                        }
                        stagedNodes.remove(prefix);
                        childHash = null;
                        continue;
                    }

                    if (childCount == 1) {
                        // Collapse unary branch: do not persist an internal node at this prefix.
                        // Propagate the sole child's digest upward so the parent directly
                        // references the leaf/subtree, matching the reference implementation.
                        if (existingKey != null) {
                            // Mark any previously persisted node at this prefix as stale.
                            if (existingKey.version() != newVersion) {
                                recordStale(existingKey);
                            }
                        }
                        stagedNodes.remove(prefix);

                        // Find and propagate the only child digest
                        byte[] onlyChild = null;
                        for (int c = 0; c < 16; c++) {
                            if (full[c] != null) {
                                onlyChild = full[c];
                                break;
                            }
                        }
                        childHash = onlyChild == null ? null : onlyChild.clone();
                        continue;
                    }

                    byte[] branchHash = commitments.commitBranch(prefix, full);
                    JmtInternalNode node = buildInternalNodeFromFull(full);
                    NodeKey newKey = NodeKey.of(prefix, newVersion);
                    stagedNodes.put(prefix, new JmtStore.NodeEntry(newKey, node));
                    recordNode(newKey, node);
                    if (existingKey != null && existingKey.version() != newVersion) {
                        recordStale(existingKey);
                    }
                    childHash = branchHash;
                }
                return childHash;
            }

            // helper methods removed

            private void recordNode(NodeKey nodeKey, JmtNode node) {
                pendingNodes.add(new NodeWrite(nodeKey, node));
                cacheNode(nodeKey, node);
                if (config.resultNodeLimit() <= 0) {
                    return;
                }
                if (resultNodes.size() < config.resultNodeLimit() || resultNodes.containsKey(nodeKey)) {
                    resultNodes.put(nodeKey, node);
                }
            }

            private void recordStale(NodeKey nodeKey) {
                if (nodeKey.version() == newVersion) {
                    return;
                }
                if (pendingStale.add(nodeKey)) {
                    invalidateNode(nodeKey);
                }
                if (config.resultStaleLimit() <= 0) {
                    return;
                } else if (resultStale.size() < config.resultStaleLimit() || resultStale.contains(nodeKey)) {
                    if (!resultStale.contains(nodeKey)) {
                        resultStale.add(nodeKey);
                    }
                }
            }

            private void cacheValue(byte[] keyHash, byte[] value, long version) {
                StreamingEngine.this.cacheValue(keyHash, version, value);
            }

            private void evictValue(byte[] keyHash) {
                StreamingEngine.this.evictValue(keyHash);
            }

            private void updateRoot(byte[] childHash) {
                if (childHash == null) {
                    currentRootHash = commitments.nullHash().clone();
                } else {
                    currentRootHash = childHash.clone();
                }
            }

            private int countChildren(byte[][] full) {
                int count = 0;
                for (byte[] child : full) {
                    if (child != null) count++;
                }
                return count;
            }

            private int commonPrefixLength(com.bloxbean.cardano.statetrees.common.NibblePath a,
                                           com.bloxbean.cardano.statetrees.common.NibblePath b) {
                int[] an = a.getNibbles();
                int[] bn = b.getNibbles();
                int len = Math.min(an.length, bn.length);
                int idx = 0;
                while (idx < len && an[idx] == bn[idx]) {
                    idx++;
                }
                return idx;
            }

            private final class NodeWrite {
                final NodeKey key;
                final JmtNode node;

                NodeWrite(NodeKey key, JmtNode node) {
                    this.key = key;
                    this.node = node;
                }
            }
        }

        private static final class NodeCache extends java.util.LinkedHashMap<NodeAddress, JmtStore.NodeEntry> {
            private final int maxEntries;

            NodeCache(int maxEntries) {
                super(Math.max(16, maxEntries), 0.75f, true);
                this.maxEntries = maxEntries;
            }

            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<NodeAddress, JmtStore.NodeEntry> eldest) {
                return size() > maxEntries;
            }

            int evictUpTo(long versionInclusive) {
                int removed = 0;
                java.util.Iterator<java.util.Map.Entry<NodeAddress, JmtStore.NodeEntry>> iterator = entrySet().iterator();
                while (iterator.hasNext()) {
                    java.util.Map.Entry<NodeAddress, JmtStore.NodeEntry> entry = iterator.next();
                    if (Long.compareUnsigned(entry.getKey().version(), versionInclusive) <= 0) {
                        iterator.remove();
                        removed++;
                    }
                }
                return removed;
            }
        }

        private static final class NegativeNodeCache extends java.util.LinkedHashMap<NodeAddress, Boolean> {
            private final int maxEntries;

            NegativeNodeCache(int maxEntries) {
                super(Math.max(16, maxEntries), 0.75f, true);
                this.maxEntries = Math.max(1, maxEntries);
            }

            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<NodeAddress, Boolean> eldest) {
                return size() > maxEntries;
            }

            int evictUpTo(long versionInclusive) {
                int removed = 0;
                java.util.Iterator<java.util.Map.Entry<NodeAddress, Boolean>> iterator = entrySet().iterator();
                while (iterator.hasNext()) {
                    java.util.Map.Entry<NodeAddress, Boolean> entry = iterator.next();
                    if (Long.compareUnsigned(entry.getKey().version(), versionInclusive) <= 0) {
                        iterator.remove();
                        removed++;
                    }
                }
                return removed;
            }
        }

        private static final class ValueCache extends java.util.LinkedHashMap<ValueCacheKey, byte[]> {
            private final int maxEntries;

            ValueCache(int maxEntries) {
                super(Math.max(16, maxEntries), 0.75f, true);
                this.maxEntries = maxEntries;
            }

            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<ValueCacheKey, byte[]> eldest) {
                return size() > maxEntries;
            }

            void removeEntriesForKey(byte[] keyHash) {
                java.util.Iterator<java.util.Map.Entry<ValueCacheKey, byte[]>> iterator = entrySet().iterator();
                while (iterator.hasNext()) {
                    java.util.Map.Entry<ValueCacheKey, byte[]> entry = iterator.next();
                    if (java.util.Arrays.equals(entry.getKey().keyHash, keyHash)) {
                        iterator.remove();
                    }
                }
            }
        }

        private static final class NodeAddress {
            private final com.bloxbean.cardano.statetrees.common.NibblePath path;
            private final long version;

            NodeAddress(com.bloxbean.cardano.statetrees.common.NibblePath path, long version) {
                this.path = path;
                this.version = version;
            }

            com.bloxbean.cardano.statetrees.common.NibblePath path() {
                return path;
            }

            long version() {
                return version;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof NodeAddress)) return false;
                NodeAddress that = (NodeAddress) o;
                return version == that.version && path.equals(that.path);
            }

            @Override
            public int hashCode() {
                return java.util.Objects.hash(path, version);
            }
        }

        private static final class ValueCacheKey {
            private final byte[] keyHash;
            private final long version;
            private final int hash;

            ValueCacheKey(byte[] keyHash, long version) {
                this.keyHash = java.util.Arrays.copyOf(keyHash, keyHash.length);
                this.version = version;
                this.hash = 31 * java.util.Arrays.hashCode(this.keyHash) + Long.hashCode(version);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof ValueCacheKey)) return false;
                ValueCacheKey that = (ValueCacheKey) o;
                return version == that.version && java.util.Arrays.equals(keyHash, that.keyHash);
            }

            @Override
            public int hashCode() {
                return hash;
            }
        }

        private final class Traversal {
            final java.util.List<PathFrame> frames;
            final java.util.Optional<JmtStore.NodeEntry> terminal;
            final int depth;

            Traversal(java.util.List<PathFrame> frames,
                      java.util.Optional<JmtStore.NodeEntry> terminal,
                      int depth) {
                this.frames = frames;
                this.terminal = terminal;
                this.depth = depth;
            }
        }

        private static final class PathFrame {
            final com.bloxbean.cardano.statetrees.common.NibblePath prefix;
            final JmtStore.NodeEntry entry;
            final byte[][] fullChildHashes;
            final int childIndex;

            PathFrame(com.bloxbean.cardano.statetrees.common.NibblePath prefix,
                      JmtStore.NodeEntry entry,
                      byte[][] fullChildHashes,
                      int childIndex) {
                this.prefix = prefix;
                this.entry = entry;
                this.fullChildHashes = fullChildHashes;
                this.childIndex = childIndex;
            }
        }
    }

    public enum EngineMode {REFERENCE, STREAMING}

    public static final class PruneReport {
        private final long versionInclusive;
        private final int nodesPruned;
        private final int cacheEntriesEvicted;
        private final int valueCacheCleared;

        public PruneReport(long versionInclusive, int nodesPruned, int cacheEntriesEvicted, int valueCacheCleared) {
            this.versionInclusive = versionInclusive;
            this.nodesPruned = nodesPruned;
            this.cacheEntriesEvicted = cacheEntriesEvicted;
            this.valueCacheCleared = valueCacheCleared;
        }

        public long versionInclusive() {
            return versionInclusive;
        }

        public int nodesPruned() {
            return nodesPruned;
        }

        public int cacheEntriesEvicted() {
            return cacheEntriesEvicted;
        }

        public int valueCacheCleared() {
            return valueCacheCleared;
        }
    }
}

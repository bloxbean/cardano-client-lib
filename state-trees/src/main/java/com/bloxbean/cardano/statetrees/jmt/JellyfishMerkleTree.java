package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.common.util.Bytes;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.mpf.MpfProofSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * In-memory Jellyfish Merkle Tree implementation with MPF-compatible commitments.
 *
 * <p>This implementation focuses on correctness and reference behaviour. It builds
 * a fully persistent tree snapshot for each committed version and can generate
 * MPF-style inclusion and non-inclusion proofs. Storage is currently in-memory;
 * persistence backends (e.g. RocksDB) can consume the emitted {@link CommitResult}
 * to durably store nodes and stale markers.</p>
 */
public final class JellyfishMerkleTree {

    private final CommitmentScheme commitments;
    private final HashFunction hashFn;
    private final NavigableMap<Long, VersionSnapshot> versions = new TreeMap<>();

    public JellyfishMerkleTree(CommitmentScheme commitments, HashFunction hashFn) {
        this.commitments = Objects.requireNonNull(commitments, "commitments");
        this.hashFn = Objects.requireNonNull(hashFn, "hashFn");
    }

    public synchronized Optional<Long> latestVersion() {
        return versions.isEmpty() ? Optional.empty() : Optional.of(versions.lastKey());
    }

    public synchronized byte[] latestRootHash() {
        return versions.isEmpty() ? commitments.nullHash().clone() : versions.lastEntry().getValue().rootHash.clone();
    }

    public synchronized byte[] rootHash(long version) {
        VersionSnapshot snapshot = snapshotAt(version);
        return snapshot == null ? commitments.nullHash().clone() : snapshot.rootHash.clone();
    }

    public synchronized CommitResult commit(long version, Map<byte[], byte[]> updates) {
        if (!versions.isEmpty() && version <= versions.lastKey()) {
            throw new IllegalArgumentException("version must be greater than latest committed version");
        }
        Objects.requireNonNull(updates, "updates");

        VersionSnapshot baseSnapshot = versions.isEmpty() ? null : versions.lastEntry().getValue();
        Map<String, ValueRecord> nextValues = baseSnapshot == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(baseSnapshot.values);

        List<CommitResult.ValueOperation> valueOps = new ArrayList<>(updates.size());

        for (Map.Entry<byte[], byte[]> entry : updates.entrySet()) {
            byte[] key = Objects.requireNonNull(entry.getKey(), "update key");
            byte[] keyHash = hashFn.digest(key);
            String keyHex = Bytes.toHex(keyHash);
            byte[] value = entry.getValue();
            if (value == null) {
                nextValues.remove(keyHex);
                valueOps.add(CommitResult.ValueOperation.delete(keyHash));
            } else {
                byte[] valueCopy = Arrays.copyOf(value, value.length);
                byte[] valueHash = hashFn.digest(valueCopy);
                nextValues.put(keyHex, new ValueRecord(keyHash, valueCopy, valueHash));
                valueOps.add(CommitResult.ValueOperation.put(keyHash, valueCopy));
            }
        }

        List<KeyEntry> entries = nextValues.values().stream()
                .map(KeyEntry::fromRecord)
                .sorted((a, b) -> lexCompare(a.keyHash, b.keyHash))
                .collect(Collectors.toList());

        Map<NodeKey, JmtNode> newNodes = new LinkedHashMap<>();
        TreeNode rootNode = entries.isEmpty()
                ? null
                : buildSubtree(entries, 0, NibblePath.EMPTY, version, newNodes);

        byte[] rootHash = rootNode == null ? commitments.nullHash() : rootNode.hash.clone();
        VersionSnapshot snapshot = new VersionSnapshot(version, makeImmutable(nextValues),
                Collections.unmodifiableMap(newNodes), rootNode, rootHash);
        versions.put(version, snapshot);

        List<NodeKey> staleNodes = baseSnapshot == null
                ? Collections.emptyList()
                : new ArrayList<>(baseSnapshot.nodes.keySet());

        return new CommitResult(version, rootHash.clone(), snapshot.nodes, staleNodes, valueOps);
    }

    public synchronized byte[] get(byte[] key) {
        return latestVersion().map(v -> get(key, v)).orElse(null);
    }

    public synchronized byte[] get(byte[] key, long version) {
        Objects.requireNonNull(key, "key");
        VersionSnapshot snapshot = snapshotAt(version);
        if (snapshot == null) return null;
        byte[] keyHash = hashFn.digest(key);
        ValueRecord record = snapshot.values.get(Bytes.toHex(keyHash));
        return record == null ? null : record.value.clone();
    }

    public synchronized Optional<JmtProof> getProof(byte[] key, long version) {
        Objects.requireNonNull(key, "key");
        VersionSnapshot snapshot = snapshotAt(version);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (snapshot.root == null) {
            List<JmtProof.BranchStep> emptySteps = Collections.emptyList();
            return Optional.of(JmtProof.nonInclusionEmpty(emptySteps));
        }

        byte[] keyHash = hashFn.digest(key);
        int[] nibbles = Nibbles.toNibbles(keyHash);
        List<JmtProof.BranchStep> steps = new ArrayList<>();
        TreeNode current = snapshot.root;
        int depth = 0;
        NibblePath pathPrefix = NibblePath.EMPTY;

        while (current instanceof InternalTreeNode) {
            InternalTreeNode internal = (InternalTreeNode) current;
            int nibble = nibbles[depth];

            TreeNode[] childNodes = internal.children;
            int neighborCount = 0;
            int neighborNibble = -1;
            TreeNode neighborNode = null;
            for (int idx = 0; idx < childNodes.length; idx++) {
                if (idx == nibble) continue;
                if (childNodes[idx] != null) {
                    neighborCount++;
                    neighborNibble = idx;
                    neighborNode = childNodes[idx];
                    if (neighborCount > 1) break;
                }
            }

            NibblePath forkPrefix = null;
            byte[] forkRoot = null;
            byte[] leafNeighborKey = null;
            byte[] leafNeighborValue = null;

            if (neighborCount == 1 && neighborNode != null) {
                if (neighborNode instanceof LeafTreeNode) {
                    LeafTreeNode ln = (LeafTreeNode) neighborNode;
                    leafNeighborKey = ln.keyHash.clone();
                    leafNeighborValue = ln.valueHash.clone();
                } else if (neighborNode instanceof InternalTreeNode) {
                    forkPrefix = neighborNode.path();
                    forkRoot = neighborNode.hash.clone();
                }
            }

            steps.add(new JmtProof.BranchStep(pathPrefix, cloneChildHashes(internal.childHashes), nibble,
                    neighborCount == 1, neighborNibble, forkPrefix, forkRoot, leafNeighborKey, leafNeighborValue));

            TreeNode next = internal.children[nibble];
            if (next == null) {
                return Optional.of(JmtProof.nonInclusionEmpty(steps));
            }
            pathPrefix = append(pathPrefix, nibble);
            current = next;
            depth++;
        }

        if (!(current instanceof LeafTreeNode)) {
            throw new IllegalStateException("Unexpected node type in proof generation");
        }

        LeafTreeNode leaf = (LeafTreeNode) current;
        if (Arrays.equals(leaf.keyHash, keyHash)) {
            ValueRecord record = snapshot.values.get(Bytes.toHex(keyHash));
            byte[] value = record == null ? null : record.value.clone();
            NibblePath suffix = leaf.fullPath.slice(depth, leaf.fullPath.length());
            return Optional.of(JmtProof.inclusion(steps, value, leaf.valueHash.clone(), suffix, leaf.keyHash.clone()));
        } else {
            NibblePath suffix = leaf.fullPath.slice(depth, leaf.fullPath.length());
            return Optional.of(JmtProof.nonInclusionDifferentLeaf(steps, leaf.keyHash.clone(), leaf.valueHash.clone(), suffix));
        }
    }

    public synchronized Optional<byte[]> getMpfProofCbor(byte[] key) {
        Objects.requireNonNull(key, "key");
        return latestVersion().flatMap(version -> getMpfProofCbor(key, version));
    }

    public synchronized Optional<byte[]> getMpfProofCbor(byte[] key, long version) {
        Objects.requireNonNull(key, "key");
        Optional<JmtProof> proof = getProof(key, version);
        return proof.map(p -> MpfProofSerializer.toCbor(p, hashFn, commitments));
    }

    private VersionSnapshot snapshotAt(long version) {
        Map.Entry<Long, VersionSnapshot> entry = versions.floorEntry(version);
        return entry == null ? null : entry.getValue();
    }

    private static Map<String, ValueRecord> makeImmutable(Map<String, ValueRecord> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private TreeNode buildSubtree(List<KeyEntry> entries, int depth, NibblePath prefix,
                                  long version, Map<NodeKey, JmtNode> outNodes) {
        if (entries.size() == 1) {
            KeyEntry entry = entries.get(0);
            NibblePath suffix = entry.fullPath.slice(depth, entry.fullPath.length());
            byte[] leafHash = commitments.commitLeaf(suffix, entry.valueHash);
            NodeKey nodeKey = NodeKey.of(entry.fullPath, version);
            outNodes.put(nodeKey, JmtLeafNode.of(entry.keyHash, entry.valueHash));
            return new LeafTreeNode(entry.fullPath, leafHash, entry.keyHash, entry.valueHash);
        }

        @SuppressWarnings("unchecked")
        List<KeyEntry>[] buckets = new List[16];
        for (KeyEntry entry : entries) {
            if (depth >= entry.nibbles.length) {
                throw new IllegalStateException("Depth exceeds key length");
            }
            int nib = entry.nibbles[depth];
            if (buckets[nib] == null) buckets[nib] = new ArrayList<>();
            buckets[nib].add(entry);
        }

        TreeNode[] children = new TreeNode[16];
        byte[][] childHashes = new byte[16][];
        int bitmap = 0;
        List<byte[]> compactHashes = new ArrayList<>();

        for (int nib = 0; nib < 16; nib++) {
            List<KeyEntry> bucket = buckets[nib];
            if (bucket == null) continue;
            TreeNode child = buildSubtree(bucket, depth + 1, append(prefix, nib), version, outNodes);
            children[nib] = child;
            childHashes[nib] = child.hash.clone();
            compactHashes.add(child.hash.clone());
            bitmap |= (1 << nib);
        }

        byte[][] compactArray = compactHashes.toArray(new byte[0][]);
        JmtInternalNode internalNode = JmtInternalNode.of(bitmap, compactArray, null);
        NodeKey nodeKey = NodeKey.of(prefix, version);
        outNodes.put(nodeKey, internalNode);
        byte[] branchHash = commitments.commitBranch(prefix, childHashes);
        return new InternalTreeNode(prefix, childHashes, children, branchHash);
    }

    private static NibblePath append(NibblePath prefix, int nibble) {
        int[] nibbles = prefix.getNibbles();
        int[] extended = Arrays.copyOf(nibbles, nibbles.length + 1);
        extended[extended.length - 1] = nibble & 0xF;
        return NibblePath.of(extended);
    }

    private static byte[][] cloneChildHashes(byte[][] childHashes) {
        byte[][] cloned = new byte[childHashes.length][];
        for (int i = 0; i < childHashes.length; i++) {
            cloned[i] = childHashes[i] == null ? null : childHashes[i].clone();
        }
        return cloned;
    }

    private static int lexCompare(byte[] a, byte[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int diff = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (diff != 0) return diff;
        }
        return Integer.compare(a.length, b.length);
    }

    private static final class VersionSnapshot {
        private final long version;
        private final Map<String, ValueRecord> values;
        private final Map<NodeKey, JmtNode> nodes;
        private final TreeNode root;
        private final byte[] rootHash;

        private VersionSnapshot(long version, Map<String, ValueRecord> values,
                                Map<NodeKey, JmtNode> nodes, TreeNode root, byte[] rootHash) {
            this.version = version;
            this.values = values;
            this.nodes = nodes;
            this.root = root;
            this.rootHash = rootHash;
        }
    }

    private static final class ValueRecord {
        private final byte[] keyHash;
        private final byte[] value;
        private final byte[] valueHash;

        private ValueRecord(byte[] keyHash, byte[] value, byte[] valueHash) {
            this.keyHash = keyHash;
            this.value = value;
            this.valueHash = valueHash;
        }
    }

    private static final class KeyEntry {
        private final byte[] keyHash;
        private final byte[] valueHash;
        private final int[] nibbles;
        private final NibblePath fullPath;

        private KeyEntry(byte[] keyHash, byte[] valueHash) {
            this.keyHash = keyHash;
            this.valueHash = valueHash;
            this.nibbles = Nibbles.toNibbles(keyHash);
            this.fullPath = NibblePath.of(nibbles);
        }

        private static KeyEntry fromRecord(ValueRecord record) {
            return new KeyEntry(record.keyHash.clone(), record.valueHash.clone());
        }
    }

    private abstract static class TreeNode {
        private final NibblePath path;
        private final byte[] hash;

        private TreeNode(NibblePath path, byte[] hash) {
            this.path = path;
            this.hash = hash;
        }

        private NibblePath path() {
            return path;
        }
    }

    private static final class InternalTreeNode extends TreeNode {
        private final byte[][] childHashes;
        private final TreeNode[] children;

        private InternalTreeNode(NibblePath path, byte[][] childHashes, TreeNode[] children, byte[] hash) {
            super(path, hash);
            this.childHashes = childHashes;
            this.children = children;
        }
    }

    private static final class LeafTreeNode extends TreeNode {
        private final NibblePath fullPath;
        private final byte[] keyHash;
        private final byte[] valueHash;

        private LeafTreeNode(NibblePath fullPath, byte[] hash, byte[] keyHash, byte[] valueHash) {
            super(fullPath, hash);
            this.fullPath = fullPath;
            this.keyHash = keyHash;
            this.valueHash = valueHash;
        }
    }

    public static final class CommitResult {
        private final long version;
        private final byte[] rootHash;
        private final Map<NodeKey, JmtNode> nodes;
        private final List<NodeKey> staleNodes;
        private final List<ValueOperation> valueOperations;

        private CommitResult(long version, byte[] rootHash, Map<NodeKey, JmtNode> nodes,
                             List<NodeKey> staleNodes, List<ValueOperation> valueOperations) {
            this.version = version;
            this.rootHash = rootHash;
            this.nodes = nodes;
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

        public static final class ValueOperation {
            public enum Type {PUT, DELETE}

            private final Type type;
            private final byte[] keyHash;
            private final byte[] value;

            private ValueOperation(Type type, byte[] keyHash, byte[] value) {
                this.type = type;
                this.keyHash = Arrays.copyOf(keyHash, keyHash.length);
                this.value = value == null ? null : Arrays.copyOf(value, value.length);
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
                return value == null ? null : Arrays.copyOf(value, value.length);
            }
        }

        static CommitResult streaming(long version,
                                      byte[] rootHash,
                                      Map<NodeKey, JmtNode> nodes,
                                      List<NodeKey> staleNodes,
                                      List<ValueOperation> valueOperations) {
            Map<NodeKey, JmtNode> immutableNodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
            return new CommitResult(version, rootHash, immutableNodes, staleNodes, valueOperations);
        }
    }
}

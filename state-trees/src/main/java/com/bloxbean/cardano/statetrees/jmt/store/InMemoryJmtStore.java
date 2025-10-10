package com.bloxbean.cardano.statetrees.jmt.store;

import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.jmt.JmtNode;
import com.bloxbean.cardano.statetrees.jmt.NodeKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Simple in-memory {@link JmtStore} backed by {@link java.util.Map} structures.
 *
 * <p>The implementation mirrors the behaviour expected from persistent stores
 * while remaining lightweight for unit tests and benchmarks. It keeps all data
 * in heap memory so it is not intended for production deployments.</p>
 */
public final class InMemoryJmtStore implements JmtStore {

    private final NavigableMap<NodeKey, JmtNode> nodes = new TreeMap<>();
    private final Map<ByteArrayWrapper, byte[]> values = new HashMap<>();
    private final Map<ByteArrayWrapper, java.util.NavigableMap<Long, byte[]>> valuesByKey = new HashMap<>();
    private final NavigableMap<Long, byte[]> roots = new TreeMap<>();
    private final NavigableMap<Long, List<NodeKey>> staleByVersion = new TreeMap<>();

    @Override
    public synchronized Optional<VersionedRoot> latestRoot() {
        Map.Entry<Long, byte[]> entry = roots.isEmpty() ? null : roots.lastEntry();
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new VersionedRoot(entry.getKey(), entry.getValue()));
    }

    @Override
    public synchronized Optional<byte[]> rootHash(long version) {
        byte[] hash = roots.get(version);
        return hash == null ? Optional.empty() : Optional.of(hash.clone());
    }

    @Override
    public synchronized Optional<NodeEntry> getNode(long version, NibblePath path) {
        Objects.requireNonNull(path, "path");
        NodeKey searchKey = NodeKey.of(path, version);
        Map.Entry<NodeKey, JmtNode> candidate = nodes.floorEntry(searchKey);
        if (candidate == null) {
            return Optional.empty();
        }
        if (!candidate.getKey().path().equals(path)) {
            return Optional.empty();
        }
        if (Long.compareUnsigned(candidate.getKey().version(), version) > 0) {
            return Optional.empty();
        }
        if (isStale(candidate.getKey(), version)) {
            return Optional.empty();
        }
        return Optional.of(new NodeEntry(candidate.getKey(), candidate.getValue()));
    }

    @Override
    public synchronized Optional<JmtNode> getNode(NodeKey nodeKey) {
        Objects.requireNonNull(nodeKey, "nodeKey");
        return Optional.ofNullable(nodes.get(nodeKey));
    }

    @Override
    public synchronized Optional<NodeEntry> floorNode(long version, NibblePath path) {
        Objects.requireNonNull(path, "path");
        NodeKey searchKey = NodeKey.of(path, version);
        Map.Entry<NodeKey, JmtNode> candidate = nodes.floorEntry(searchKey);
        while (candidate != null) {
            if (Long.compareUnsigned(candidate.getKey().version(), version) <= 0 &&
                    !isStale(candidate.getKey(), version)) {
                return Optional.of(new NodeEntry(candidate.getKey(), candidate.getValue()));
            }
            candidate = nodes.lowerEntry(candidate.getKey());
        }
        return Optional.empty();
    }

    @Override
    public synchronized Optional<NodeEntry> ceilingNode(long version, NibblePath path) {
        Objects.requireNonNull(path, "path");
        // We need the smallest node whose PATH is >= requested path and whose version <= requested version,
        // skipping nodes that are stale at the requested version. Since NodeKey ordering is (path, version),
        // we must handle the version constraint explicitly, potentially stepping back to an older version for
        // the same path before moving to the next path.

        // Start at the first entry with path >= requested path (ignoring version for the initial probe)
        NodeKey probe = NodeKey.of(path, 0L);
        Map.Entry<NodeKey, JmtNode> candidate = nodes.ceilingEntry(probe);
        while (candidate != null) {
            NodeKey candKey = candidate.getKey();
            // If the candidate's version is too new, try to find an older version for the same path
            if (Long.compareUnsigned(candKey.version(), version) > 0) {
                Map.Entry<NodeKey, JmtNode> older = nodes.floorEntry(NodeKey.of(candKey.path(), version));
                if (older != null && older.getKey().path().equals(candKey.path())) {
                    candKey = older.getKey();
                    candidate = older;
                } else {
                    // Jump to the first entry of the next path strictly greater than the current one
                    candidate = nodes.higherEntry(NodeKey.of(candKey.path(), Long.MAX_VALUE));
                    continue;
                }
            }

            if (!isStale(candKey, version)) {
                return Optional.of(new NodeEntry(candKey, candidate.getValue()));
            }

            // Move to the next entry for this path/version ordering
            candidate = nodes.higherEntry(candKey);
        }
        return Optional.empty();
    }

    @Override
    public synchronized Optional<byte[]> getValue(byte[] keyHash) {
        Objects.requireNonNull(keyHash, "keyHash");
        byte[] value = values.get(new ByteArrayWrapper(keyHash));
        return value == null ? Optional.empty() : Optional.of(value.clone());
    }

    @Override
    public synchronized Optional<byte[]> getValueAt(byte[] keyHash, long version) {
        Objects.requireNonNull(keyHash, "keyHash");
        ByteArrayWrapper key = new ByteArrayWrapper(keyHash);
        java.util.NavigableMap<Long, byte[]> history = valuesByKey.get(key);
        if (history == null || history.isEmpty()) return Optional.empty();
        java.util.Map.Entry<Long, byte[]> e = history.floorEntry(version);
        if (e == null) return Optional.empty();
        byte[] val = e.getValue();
        if (val == null) return Optional.empty(); // Tombstone (deleted)
        return Optional.of(val.clone());
    }

    private boolean leafExistsAt(byte[] keyHash, long version) {
        com.bloxbean.cardano.statetrees.common.NibblePath path = com.bloxbean.cardano.statetrees.common.NibblePath.of(
                com.bloxbean.cardano.statetrees.common.nibbles.Nibbles.toNibbles(keyHash));
        NodeKey search = NodeKey.of(path, version);
        java.util.Map.Entry<NodeKey, JmtNode> candidate = nodes.floorEntry(search);
        if (candidate == null) return false;
        if (!candidate.getKey().path().equals(path)) return false;
        if (isStale(candidate.getKey(), version)) return false;
        if (!(candidate.getValue() instanceof com.bloxbean.cardano.statetrees.jmt.JmtLeafNode)) return false;
        com.bloxbean.cardano.statetrees.jmt.JmtLeafNode leaf = (com.bloxbean.cardano.statetrees.jmt.JmtLeafNode) candidate.getValue();
        return java.util.Arrays.equals(leaf.keyHash(), keyHash);
    }

    @Override
    public synchronized CommitBatch beginCommit(long version, CommitConfig config) {
        Objects.requireNonNull(config, "config");
        return new InMemoryCommitBatch(version);
    }

    @Override
    public synchronized List<NodeKey> staleNodesUpTo(long versionInclusive) {
        if (staleByVersion.isEmpty()) {
            return Collections.emptyList();
        }
        NavigableMap<Long, List<NodeKey>> head = staleByVersion.headMap(versionInclusive, true);
        if (head.isEmpty()) {
            return Collections.emptyList();
        }
        List<NodeKey> result = new ArrayList<>();
        for (List<NodeKey> list : head.values()) {
            result.addAll(list);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized int pruneUpTo(long versionInclusive) {
        NavigableMap<Long, List<NodeKey>> head = staleByVersion.headMap(versionInclusive, true);
        if (head.isEmpty()) {
            return 0;
        }
        int pruned = 0;
        List<Long> versionsToRemove = new ArrayList<>(head.keySet());
        for (Long version : versionsToRemove) {
            List<NodeKey> list = staleByVersion.get(version);
            if (list == null) {
                continue;
            }
            for (NodeKey nodeKey : list) {
                if (nodes.remove(nodeKey) != null) {
                    pruned++;
                }
            }
            staleByVersion.remove(version);
        }
        return pruned;
    }

    @Override
    public synchronized void truncateAfter(long version) {
        // Remove nodes with version > target
        java.util.Iterator<NodeKey> nodeIterator = nodes.keySet().iterator();
        while (nodeIterator.hasNext()) {
            NodeKey nodeKey = nodeIterator.next();
            if (Long.compareUnsigned(nodeKey.version(), version) > 0) {
                nodeIterator.remove();
            }
        }

        // Adjust values history and current map
        valuesByKey.forEach((key, history) -> history.tailMap(Long.valueOf(version + 1)).clear());
        valuesByKey.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        values.clear();
        valuesByKey.forEach((key, history) -> {
            java.util.Map.Entry<Long, byte[]> latest = history.floorEntry(version);
            if (latest != null && latest.getValue() != null) {
                values.put(key, latest.getValue());
            }
        });

        // Roots
        roots.tailMap(Long.valueOf(version + 1)).clear();

        // Stale markers
        staleByVersion.tailMap(Long.valueOf(version + 1)).clear();
    }

    @Override
    public void close() {
        // Nothing to release.
    }

    private final class InMemoryCommitBatch implements CommitBatch {

        private final long version;
        private final Map<NodeKey, JmtNode> nodeUpdates = new LinkedHashMap<>();
        private final Map<ByteArrayWrapper, byte[]> valueUpdates = new LinkedHashMap<>();
        private final List<ByteArrayWrapper> valueDeletes = new ArrayList<>();
        private final List<NodeKey> staleNodes = new ArrayList<>();
        private byte[] rootHash;
        private boolean closed;

        private InMemoryCommitBatch(long version) {
            this.version = version;
        }

        @Override
        public void putNode(NodeKey nodeKey, JmtNode node) {
            ensureOpen();
            Objects.requireNonNull(nodeKey, "nodeKey");
            Objects.requireNonNull(node, "node");
            nodeUpdates.put(nodeKey, node);
        }

        @Override
        public void markStale(NodeKey nodeKey) {
            ensureOpen();
            Objects.requireNonNull(nodeKey, "nodeKey");
            staleNodes.add(nodeKey);
        }

        @Override
        public void putValue(byte[] keyHash, byte[] value) {
            ensureOpen();
            Objects.requireNonNull(keyHash, "keyHash");
            Objects.requireNonNull(value, "value");
            valueUpdates.put(new ByteArrayWrapper(keyHash), value.clone());
        }

        @Override
        public void deleteValue(byte[] keyHash) {
            ensureOpen();
            Objects.requireNonNull(keyHash, "keyHash");
            valueDeletes.add(new ByteArrayWrapper(keyHash));
        }

        @Override
        public void setRootHash(byte[] rootHash) {
            ensureOpen();
            this.rootHash = rootHash == null ? null : rootHash.clone();
        }

        @Override
        public void commit() {
            ensureOpen();
            apply();
            closed = true;
        }

        @Override
        public void close() {
            if (!closed) {
                apply();
                closed = true;
            }
        }

        private void apply() {
            synchronized (InMemoryJmtStore.this) {
                for (Map.Entry<NodeKey, JmtNode> entry : nodeUpdates.entrySet()) {
                    nodes.put(entry.getKey(), entry.getValue());
                }
                for (Map.Entry<ByteArrayWrapper, byte[]> entry : valueUpdates.entrySet()) {
                    byte[] val = entry.getValue().clone();
                    values.put(entry.getKey(), val);
                    valuesByKey.computeIfAbsent(entry.getKey(), k -> new java.util.TreeMap<>())
                            .put(version, val);
                }
                for (ByteArrayWrapper key : valueDeletes) {
                    values.remove(key);
                    valuesByKey.computeIfAbsent(key, k -> new java.util.TreeMap<>())
                            .put(version, null);
                }
                if (!staleNodes.isEmpty()) {
                    staleByVersion.computeIfAbsent(version, ignored -> new ArrayList<>())
                            .addAll(staleNodes);
                }
                if (rootHash != null) {
                    roots.put(version, rootHash.clone());
                }
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("CommitBatch already closed");
            }
        }
    }

    private static final class ByteArrayWrapper {
        private final byte[] bytes;
        private final int hash;

        private ByteArrayWrapper(byte[] bytes) {
            this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
            this.hash = java.util.Arrays.hashCode(this.bytes);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ByteArrayWrapper)) return false;
            ByteArrayWrapper other = (ByteArrayWrapper) obj;
            return java.util.Arrays.equals(bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private boolean isStale(NodeKey key, long version) {
        java.util.NavigableMap<Long, List<NodeKey>> head = staleByVersion.headMap(version, true);
        if (head.isEmpty()) return false;
        for (List<NodeKey> list : head.values()) {
            if (list.contains(key)) {
                return true;
            }
        }
        return false;
    }
}

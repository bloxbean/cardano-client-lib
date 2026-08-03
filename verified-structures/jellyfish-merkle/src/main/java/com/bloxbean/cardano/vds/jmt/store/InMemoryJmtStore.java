package com.bloxbean.cardano.vds.jmt.store;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.jmt.JmtNode;
import com.bloxbean.cardano.vds.jmt.NodeKey;

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

    private final JmtAccessCoordinator accessCoordinator = new JmtAccessCoordinator();
    private final NavigableMap<NodeKey, JmtNode> nodes = new TreeMap<>();
    private final Map<ByteArrayWrapper, byte[]> values = new HashMap<>();
    private final Map<ByteArrayWrapper, java.util.NavigableMap<Long, byte[]>> valuesByKey = new HashMap<>();
    private final NavigableMap<Long, byte[]> roots = new TreeMap<>();
    private final NavigableMap<Long, List<NodeKey>> staleByVersion = new TreeMap<>();
    private JmtFormatDescriptor formatDescriptor;
    private long pruneWatermark = -1;

    @Override
    public JmtAccessCoordinator accessCoordinator() {
        return accessCoordinator;
    }

    @Override
    public synchronized void ensureFormat(JmtFormatDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (formatDescriptor == null) {
            formatDescriptor = descriptor;
        } else if (!formatDescriptor.equals(descriptor)) {
            throw new JmtFormatMismatchException("In-memory JMT already uses " + formatDescriptor
                    + "; requested " + descriptor);
        }
    }

    @Override
    public synchronized Optional<JmtFormatDescriptor> formatDescriptor() {
        return Optional.ofNullable(formatDescriptor);
    }

    @Override
    public synchronized JmtStoreInspection inspect(int maxRecords) {
        if (maxRecords <= 0) {
            throw new IllegalArgumentException("maxRecords must be > 0");
        }
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireRead("inspect")) {
            List<VersionedRoot> inspectedRoots = new ArrayList<>();
            List<JmtStoreInspection.NodeRecord> inspectedNodes = new ArrayList<>();
            List<JmtStoreInspection.ValueRecord> inspectedValues = new ArrayList<>();
            List<JmtStoreInspection.StaleRecord> inspectedStale = new ArrayList<>();
            int count = 0;
            boolean truncated = false;

            for (Map.Entry<Long, byte[]> root : roots.entrySet()) {
                if (count++ >= maxRecords) {
                    truncated = true;
                    break;
                }
                inspectedRoots.add(new VersionedRoot(root.getKey(), root.getValue()));
            }
            if (!truncated) {
                for (Map.Entry<NodeKey, JmtNode> node : nodes.entrySet()) {
                    if (count++ >= maxRecords) {
                        truncated = true;
                        break;
                    }
                    inspectedNodes.add(new JmtStoreInspection.NodeRecord(
                            node.getKey(), node.getValue()));
                }
            }
            if (!truncated) {
                valuesLoop:
                for (Map.Entry<ByteArrayWrapper, NavigableMap<Long, byte[]>> history
                        : valuesByKey.entrySet()) {
                    for (Map.Entry<Long, byte[]> value : history.getValue().entrySet()) {
                        if (count++ >= maxRecords) {
                            truncated = true;
                            break valuesLoop;
                        }
                        inspectedValues.add(new JmtStoreInspection.ValueRecord(
                                history.getKey().bytes(), value.getKey(), value.getValue(),
                                value.getValue() == null));
                    }
                }
            }
            if (!truncated) {
                staleLoop:
                for (Map.Entry<Long, List<NodeKey>> stale : staleByVersion.entrySet()) {
                    for (NodeKey nodeKey : stale.getValue()) {
                        if (count++ >= maxRecords) {
                            truncated = true;
                            break staleLoop;
                        }
                        inspectedStale.add(new JmtStoreInspection.StaleRecord(
                                stale.getKey(), nodeKey));
                    }
                }
            }
            VersionedRoot inspectedLatest = roots.isEmpty() ? null
                    : new VersionedRoot(roots.lastKey(), roots.get(roots.lastKey()));
            return new JmtStoreInspection(inspectedRoots, inspectedLatest, inspectedNodes, inspectedValues,
                    inspectedStale, Collections.emptyList(), truncated);
        }
    }

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
        return Optional.of(new NodeEntry(candidate.getKey(), candidate.getValue()));
    }

    @Override
    public synchronized Optional<JmtNode> getNode(NodeKey nodeKey) {
        Objects.requireNonNull(nodeKey, "nodeKey");
        return Optional.ofNullable(nodes.get(nodeKey));
    }

    @Override
    public synchronized Optional<NodeEntry> floorNode(long version, NibblePath path) {
        return JmtStore.super.floorNode(version, path);
    }

    @Override
    public synchronized Optional<NodeEntry> ceilingNode(long version, NibblePath path) {
        return JmtStore.super.ceilingNode(version, path);
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

    @Override
    public synchronized CommitBatch beginCommit(long version, CommitConfig config) {
        Objects.requireNonNull(config, "config");
        JmtAccessLease lease = accessCoordinator.tryAcquireUpdate("commit", version);
        return new InMemoryCommitBatch(version, config, lease);
    }

    @Override
    public synchronized List<NodeKey> staleNodesUpTo(long versionInclusive) {
        requireNonNegativeVersion(versionInclusive);
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
        requireNonNegativeVersion(versionInclusive);
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireMaintenance(
                "pruneUpTo", versionInclusive)) {
            if (!roots.isEmpty() && versionInclusive > roots.lastKey()) {
                throw new IllegalArgumentException("prune horizon exceeds latest version "
                        + roots.lastKey());
            }
            int pruned = 0;
            NavigableMap<Long, List<NodeKey>> head = staleByVersion.headMap(
                    versionInclusive, true);
            List<Long> versionsToRemove = new ArrayList<>(head.keySet());
            for (Long staleVersion : versionsToRemove) {
                List<NodeKey> list = staleByVersion.get(staleVersion);
                if (list != null) {
                    for (NodeKey nodeKey : list) {
                        if (nodes.remove(nodeKey) != null) {
                            pruned++;
                        }
                    }
                }
                staleByVersion.remove(staleVersion);
            }
            for (NavigableMap<Long, byte[]> history : valuesByKey.values()) {
                NavigableMap<Long, byte[]> oldValues = history.headMap(versionInclusive, true);
                if (oldValues.size() > 1) {
                    Long sentinel = oldValues.lastKey();
                    List<Long> deletions = new ArrayList<>(oldValues.keySet());
                    deletions.remove(sentinel);
                    deletions.forEach(history::remove);
                    pruned += deletions.size();
                }
            }
            List<Long> oldRoots = new ArrayList<>(roots.headMap(versionInclusive, false).keySet());
            oldRoots.forEach(roots::remove);
            pruned += oldRoots.size();
            if (pruned > 0) {
                pruneWatermark = Math.max(pruneWatermark, versionInclusive);
            }
            return pruned;
        }
    }

    @Override
    public synchronized void truncateAfter(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireMaintenance(
                "truncateAfter", version)) {
            if (version < pruneWatermark) {
                throw new IllegalStateException("Cannot truncate to version " + version
                        + " below prune watermark " + pruneWatermark);
            }
            // Remove nodes with version > target
            java.util.Iterator<NodeKey> nodeIterator = nodes.keySet().iterator();
            while (nodeIterator.hasNext()) {
                NodeKey nodeKey = nodeIterator.next();
                if (Long.compareUnsigned(nodeKey.version(), version) > 0) {
                    nodeIterator.remove();
                }
            }

        // Adjust values history and current map
            valuesByKey.forEach((key, history) -> history.tailMap(version, false).clear());
            valuesByKey.entrySet().removeIf(entry -> entry.getValue().isEmpty());

            values.clear();
            valuesByKey.forEach((key, history) -> {
                java.util.Map.Entry<Long, byte[]> latest = history.floorEntry(version);
                if (latest != null && latest.getValue() != null) {
                    values.put(key, latest.getValue());
                }
            });

        // Roots
            roots.tailMap(version, false).clear();

        // Stale markers
            staleByVersion.tailMap(version, false).clear();
        }
    }

    @Override
    public void close() {
        // Nothing to release.
    }

    private final class InMemoryCommitBatch implements CommitBatch {

        private final long version;
        private final CommitConfig config;
        private final JmtAccessLease lease;
        private final Thread owner = Thread.currentThread();
        private final Map<NodeKey, JmtNode> nodeUpdates = new LinkedHashMap<>();
        private final Map<ByteArrayWrapper, byte[]> valueUpdates = new LinkedHashMap<>();
        private final List<ByteArrayWrapper> valueDeletes = new ArrayList<>();
        private final List<NodeKey> staleNodes = new ArrayList<>();
        private byte[] rootHash;
        private boolean closed;

        private InMemoryCommitBatch(long version, CommitConfig config, JmtAccessLease lease) {
            this.version = version;
            this.config = config;
            this.lease = lease;
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
            try {
                apply();
            } finally {
                closeInternal();
            }
        }

        @Override
        public void close() {
            // Abandoning a batch (close without commit) MUST discard staged writes, matching the
            // abort semantics of the persistent backends. Applying here would leak partial commits.
            closeInternal();
        }

        private void apply() {
            synchronized (InMemoryJmtStore.this) {
                requireRootHash(rootHash);
                Optional<VersionedRoot> latest = latestRoot();
                Optional<byte[]> committedRoot = rootHash(version);
                if (!config.shouldApply(version, rootHash, latest, committedRoot)) {
                    return;
                }
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
                    List<NodeKey> persisted = staleByVersion.computeIfAbsent(
                            version, ignored -> new ArrayList<>());
                    for (NodeKey staleNode : staleNodes) {
                        if (!persisted.contains(staleNode)) {
                            persisted.add(staleNode);
                        }
                    }
                }
                roots.put(version, rootHash.clone());
            }
        }

        private void ensureOpen() {
            ensureOwner();
            if (closed) {
                throw new IllegalStateException("CommitBatch already closed");
            }
        }

        private void closeInternal() {
            ensureOwner();
            if (closed) {
                return;
            }
            closed = true;
            lease.close();
        }

        private void ensureOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("CommitBatch must be used by its creating thread");
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

        private byte[] bytes() {
            return bytes.clone();
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

    private void requireRootHash(byte[] rootHash) {
        int expectedLength = formatDescriptor == null ? JmtFormatDescriptor.KEY_HASH_LENGTH
                : formatDescriptor.hashLength();
        if (rootHash == null || rootHash.length != expectedLength) {
            throw new IllegalStateException("Commit root hash must be exactly "
                    + expectedLength + " bytes");
        }
    }

    private static void requireNonNegativeVersion(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
    }
}

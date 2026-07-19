package com.bloxbean.cardano.vds.jmt.store;

import com.bloxbean.cardano.vds.jmt.JmtNode;
import com.bloxbean.cardano.vds.jmt.NodeKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, read-only snapshot of records used for store integrity checking.
 */
public final class JmtStoreInspection {

    private final List<JmtStore.VersionedRoot> roots;
    private final JmtStore.VersionedRoot latestRoot;
    private final List<NodeRecord> nodes;
    private final List<ValueRecord> values;
    private final List<StaleRecord> staleRecords;
    private final List<String> backendIssues;
    private final boolean truncated;

    public JmtStoreInspection(List<JmtStore.VersionedRoot> roots,
                              JmtStore.VersionedRoot latestRoot,
                              List<NodeRecord> nodes,
                              List<ValueRecord> values,
                              List<StaleRecord> staleRecords,
                              List<String> backendIssues,
                              boolean truncated) {
        this.roots = copyRoots(roots);
        this.latestRoot = latestRoot == null ? null
                : new JmtStore.VersionedRoot(latestRoot.version(), latestRoot.rootHash());
        this.nodes = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(nodes, "nodes")));
        this.values = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(values, "values")));
        this.staleRecords = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(staleRecords, "staleRecords")));
        this.backendIssues = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(backendIssues, "backendIssues")));
        this.truncated = truncated;
    }

    public List<JmtStore.VersionedRoot> roots() {
        return roots;
    }

    public java.util.Optional<JmtStore.VersionedRoot> latestRoot() {
        return latestRoot == null
                ? java.util.Optional.empty()
                : java.util.Optional.of(new JmtStore.VersionedRoot(
                        latestRoot.version(), latestRoot.rootHash()));
    }

    public List<NodeRecord> nodes() {
        return nodes;
    }

    public List<ValueRecord> values() {
        return values;
    }

    public List<StaleRecord> staleRecords() {
        return staleRecords;
    }

    public List<String> backendIssues() {
        return backendIssues;
    }

    public boolean truncated() {
        return truncated;
    }

    private static List<JmtStore.VersionedRoot> copyRoots(List<JmtStore.VersionedRoot> roots) {
        Objects.requireNonNull(roots, "roots");
        List<JmtStore.VersionedRoot> copy = new ArrayList<>(roots.size());
        for (JmtStore.VersionedRoot root : roots) {
            copy.add(new JmtStore.VersionedRoot(root.version(), root.rootHash()));
        }
        return Collections.unmodifiableList(copy);
    }

    public static final class NodeRecord {
        private final NodeKey nodeKey;
        private final JmtNode node;

        public NodeRecord(NodeKey nodeKey, JmtNode node) {
            this.nodeKey = Objects.requireNonNull(nodeKey, "nodeKey");
            this.node = Objects.requireNonNull(node, "node");
        }

        public NodeKey nodeKey() {
            return nodeKey;
        }

        public JmtNode node() {
            return node;
        }
    }

    public static final class ValueRecord {
        private final byte[] keyHash;
        private final long version;
        private final byte[] value;
        private final boolean tombstone;

        public ValueRecord(byte[] keyHash, long version, byte[] value, boolean tombstone) {
            this.keyHash = Objects.requireNonNull(keyHash, "keyHash").clone();
            this.version = version;
            this.value = value == null ? null : value.clone();
            this.tombstone = tombstone;
        }

        public byte[] keyHash() {
            return keyHash.clone();
        }

        public long version() {
            return version;
        }

        public byte[] value() {
            return value == null ? null : value.clone();
        }

        public boolean tombstone() {
            return tombstone;
        }
    }

    public static final class StaleRecord {
        private final long staleSince;
        private final NodeKey nodeKey;

        public StaleRecord(long staleSince, NodeKey nodeKey) {
            this.staleSince = staleSince;
            this.nodeKey = Objects.requireNonNull(nodeKey, "nodeKey");
        }

        public long staleSince() {
            return staleSince;
        }

        public NodeKey nodeKey() {
            return nodeKey;
        }
    }
}

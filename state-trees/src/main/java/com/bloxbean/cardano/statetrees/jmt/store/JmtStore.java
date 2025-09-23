package com.bloxbean.cardano.statetrees.jmt.store;

import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.jmt.JmtNode;
import com.bloxbean.cardano.statetrees.jmt.NodeKey;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for Jellyfish Merkle Tree nodes, values, and roots.
 *
 * <p>The {@code JmtStore} contract allows the tree core to stream node mutations
 * directly to an underlying persistence layer while looking up historical
 * versions on demand. Implementations are responsible for providing
 * efficient key lookups and atomic commit batching.</p>
 */
public interface JmtStore extends AutoCloseable {

    /**
     * Returns the latest persisted root if available.
     */
    Optional<VersionedRoot> latestRoot();

    /**
     * Returns the root hash for a specific version if present.
     */
    Optional<byte[]> rootHash(long version);

    /**
     * Fetches the newest node on {@code path} whose creation version is less
     * than or equal to {@code version}. The lookup is expressed in terms of the
     * logical nibble path rather than raw {@link NodeKey} bytes so stores can
     * provide floor lookups (e.g., via RocksDB iterators).
     */
    Optional<NodeEntry> getNode(long version, NibblePath path);

    /**
     * Loads a node by its exact {@link NodeKey}.
     */
    Optional<JmtNode> getNode(NodeKey nodeKey);

    /**
     * Returns the greatest node whose path is less than or equal to {@code path}
     * among nodes visible at {@code version}. Implementations should honour
     * stale markers when determining visibility.
     */
    Optional<NodeEntry> floorNode(long version, NibblePath path);

    /**
     * Loads the raw value associated with {@code keyHash} (32 bytes) if it is
     * stored separate from the tree nodes.
     */
    Optional<byte[]> getValue(byte[] keyHash);

    /**
     * Loads the value associated with {@code keyHash} as of the specified {@code version}.
     * Default implementation falls back to {@link #getValue(byte[])} (latest-only stores).
     * Implementations that support historical reads should override this method.
     */
    default Optional<byte[]> getValueAt(byte[] keyHash, long version) {
        return getValue(keyHash);
    }

    /**
     * Begins a staged commit for version {@code version}. The returned batch
     * accumulates node/value/root updates and must be {@link CommitBatch#commit()}
     * to make the changes durable.
     */
    CommitBatch beginCommit(long version, CommitConfig config);

    /**
     * Lists stale nodes whose deletion version is less than or equal to the
     * supplied version.
     */
    List<NodeKey> staleNodesUpTo(long versionInclusive);

    /**
     * Deletes stale nodes with {@code staleSince <= versionInclusive} and
     * returns the number of nodes removed.
     */
    int pruneUpTo(long versionInclusive);

    /**
     * Truncates the persisted state by removing nodes/values/roots whose version is greater than
     * {@code version}. Implementations that support rollback must override this method; default
     * behaviour is to throw {@link UnsupportedOperationException}.
     */
    default void truncateAfter(long versionExclusive) {
        throw new UnsupportedOperationException("truncateAfter not supported");
    }

    /**
     * Configuration for write batches. Implementations may ignore settings they
     * cannot honor, but should document any deviations.
     */
    final class CommitConfig {
        private final boolean enableNodeCacheWarmup;

        private CommitConfig(boolean enableNodeCacheWarmup) {
            this.enableNodeCacheWarmup = enableNodeCacheWarmup;
        }

        public static CommitConfig defaults() {
            return new CommitConfig(true);
        }

        public static CommitConfig of(boolean enableNodeCacheWarmup) {
            return new CommitConfig(enableNodeCacheWarmup);
        }

        public boolean enableNodeCacheWarmup() {
            return enableNodeCacheWarmup;
        }
    }

    /**
     * Versioned root metadata.
     */
    final class VersionedRoot {
        private final long version;
        private final byte[] rootHash;

        public VersionedRoot(long version, byte[] rootHash) {
            this.version = version;
            this.rootHash = rootHash.clone();
        }

        public long version() {
            return version;
        }

        public byte[] rootHash() {
            return rootHash.clone();
        }
    }

    /**
     * Node lookup result containing both the logical key and the node payload.
     */
    final class NodeEntry {
        private final NodeKey nodeKey;
        private final JmtNode node;

        public NodeEntry(NodeKey nodeKey, JmtNode node) {
            this.nodeKey = nodeKey;
            this.node = node;
        }

        public NodeKey nodeKey() {
            return nodeKey;
        }

        public JmtNode node() {
            return node;
        }
    }

    /**
     * Mutable batch operation for atomically persisting a commit.
     */
    interface CommitBatch extends AutoCloseable {

        void putNode(NodeKey nodeKey, JmtNode node);

        void markStale(NodeKey nodeKey);

        void putValue(byte[] keyHash, byte[] value);

        void deleteValue(byte[] keyHash);

        void setRootHash(byte[] rootHash);

        void commit();

        @Override
        void close();
    }
}

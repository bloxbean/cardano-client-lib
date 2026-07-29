package com.bloxbean.cardano.vds.jmt.store;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.jmt.JmtNode;
import com.bloxbean.cardano.vds.jmt.NodeKey;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
     * Returns the access coordinator shared by every tree/store wrapper for this logical namespace.
     */
    JmtAccessCoordinator accessCoordinator();

    /**
     * Installs or validates the stable cryptographic/storage descriptor for this namespace.
     */
    void ensureFormat(JmtFormatDescriptor descriptor);

    /**
     * Returns the installed format descriptor, if this namespace has been initialized.
     */
    Optional<JmtFormatDescriptor> formatDescriptor();

    /**
     * Returns a bounded, read-only record snapshot for integrity checking.
     *
     * @param maxRecords maximum total records to return
     */
    JmtStoreInspection inspect(int maxRecords);

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
     * Optional range operation. The stable JMT core does not require path-range reads. Backends
     * that cannot implement logical nibble ordering efficiently fail loudly.
     */
    default Optional<NodeEntry> floorNode(long version, NibblePath path) {
        throw new UnsupportedOperationException("floorNode is not supported by the stable JMT SPI");
    }

    /**
     * Returns the smallest node whose path is greater than or equal to {@code path}
     * among nodes visible at {@code version}. Backends that cannot implement logical nibble
     * ordering efficiently fail loudly.
     */
    default Optional<NodeEntry> ceilingNode(long version, NibblePath path) {
        throw new UnsupportedOperationException("ceilingNode is not supported by the stable JMT SPI");
    }

    /**
     * Loads the raw value associated with {@code keyHash} (32 bytes) if it is
     * stored separate from the tree nodes.
     */
    Optional<byte[]> getValue(byte[] keyHash);

    /**
     * Loads the value associated with {@code keyHash} as of the specified {@code version}.
     * Every store must implement this explicitly. Falling back to the latest value would silently
     * return data from the wrong authenticated version.
     */
    Optional<byte[]> getValueAt(byte[] keyHash, long version);

    /**
     * Begins a staged commit for version {@code version}. The returned batch
     * accumulates node/value/root updates and must be {@link CommitBatch#commit()}
     * to make the changes durable.
     *
     * <p>Store implementations must call {@link CommitConfig#shouldApply(long, byte[], Optional,
     * Optional)} against the latest and version-root records before applying staged mutations.
     * Persistent stores must perform that validation and latest-root publication in the same
     * atomic transaction or write batch.</p>
     */
    CommitBatch beginCommit(long version, CommitConfig config);

    /**
     * Lists stale nodes whose deletion version is less than or equal to the
     * supplied version.
     */
    List<NodeKey> staleNodesUpTo(long versionInclusive);

    /**
     * Deletes stale nodes with {@code staleSince <= versionInclusive} and prunes superseded value
     * history while retaining the value visible at the horizon. Root records below the horizon are
     * removed so callers cannot request proofs for structurally pruned versions. Returns the total
     * number of node, value, and root records removed. Negative or future horizons are rejected.
     */
    int pruneUpTo(long versionInclusive);

    /**
     * Truncates the persisted state by removing nodes/values/roots whose version is greater than
     * {@code version}. Versions are non-negative. Implementations that support rollback must override this method; default
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
        private final boolean checkExpectedLatest;
        private final VersionedRoot expectedLatest;

        private CommitConfig(boolean enableNodeCacheWarmup,
                             boolean checkExpectedLatest,
                             VersionedRoot expectedLatest) {
            this.enableNodeCacheWarmup = enableNodeCacheWarmup;
            this.checkExpectedLatest = checkExpectedLatest;
            this.expectedLatest = expectedLatest == null
                    ? null
                    : new VersionedRoot(expectedLatest.version(), expectedLatest.rootHash());
        }

        public static CommitConfig defaults() {
            return new CommitConfig(true, false, null);
        }

        public static CommitConfig of(boolean enableNodeCacheWarmup) {
            return new CommitConfig(enableNodeCacheWarmup, false, null);
        }

        /**
         * Creates a commit configuration that requires the persisted latest root to still match
         * the supplied base root. An empty optional means the store is expected to be empty.
         */
        public static CommitConfig expectingLatest(Optional<VersionedRoot> expectedLatest) {
            Optional<VersionedRoot> expected = Objects.requireNonNull(
                    expectedLatest, "expectedLatest");
            return new CommitConfig(true, true, expected.orElse(null));
        }

        public boolean enableNodeCacheWarmup() {
            return enableNodeCacheWarmup;
        }

        public boolean checksExpectedLatest() {
            return checkExpectedLatest;
        }

        public Optional<VersionedRoot> expectedLatest() {
            return expectedLatest == null
                    ? Optional.empty()
                    : Optional.of(new VersionedRoot(expectedLatest.version(), expectedLatest.rootHash()));
        }

        /**
         * Validates a store's latest pointer against the base observed before tree calculation.
         */
        public void verifyExpectedLatest(Optional<VersionedRoot> actualLatest) {
            Objects.requireNonNull(actualLatest, "actualLatest");
            if (!checkExpectedLatest) {
                return;
            }
            if (expectedLatest == null) {
                if (actualLatest.isPresent()) {
                    throw new JmtWriteConflictException("Expected an empty JMT store, but latest version is "
                            + actualLatest.get().version());
                }
                return;
            }
            if (actualLatest.isEmpty()) {
                throw new JmtWriteConflictException("Expected latest JMT version "
                        + expectedLatest.version() + ", but the store is empty");
            }
            VersionedRoot actual = actualLatest.get();
            if (actual.version() != expectedLatest.version()
                    || !Arrays.equals(actual.rootHash(), expectedLatest.rootHash())) {
                throw new JmtWriteConflictException("Expected latest JMT version "
                        + expectedLatest.version() + " and its observed root, but found version "
                        + actual.version() + " with a different base state");
            }
        }

        /**
         * Validates the immutable-version and latest-only replay rules at the storage boundary.
         *
         * <p>This check is required even when callers use the tree API, because {@link CommitBatch}
         * is a public SPI and may be used directly by recovery and migration tooling. A committed
         * latest version with the same root is a whole-batch no-op. Older versions and divergent
         * roots are rejected before any staged mutation is applied.</p>
         *
         * @return {@code true} when the staged batch is a new version and should be applied;
         *         {@code false} for an idempotent replay of the current latest version
         */
        public boolean shouldApply(long version,
                                   byte[] proposedRoot,
                                   Optional<VersionedRoot> actualLatest,
                                   Optional<byte[]> committedRoot) {
            if (version < 0) {
                throw new IllegalArgumentException("version must be >= 0");
            }
            Objects.requireNonNull(proposedRoot, "proposedRoot");
            Objects.requireNonNull(actualLatest, "actualLatest");
            Objects.requireNonNull(committedRoot, "committedRoot");
            verifyExpectedLatest(actualLatest);

            if (actualLatest.isPresent() && version < actualLatest.get().version()) {
                throw new JmtWriteConflictException("Cannot commit version " + version
                        + "; latest JMT version is " + actualLatest.get().version());
            }

            if (committedRoot.isPresent()) {
                if (!Arrays.equals(committedRoot.get(), proposedRoot)) {
                    throw new JmtWriteConflictException("Version " + version
                            + " is already committed with a different root hash");
                }
                if (actualLatest.isEmpty()
                        || actualLatest.get().version() != version
                        || !Arrays.equals(actualLatest.get().rootHash(), proposedRoot)) {
                    throw new JmtWriteConflictException("Version " + version
                            + " exists but is not the consistent latest JMT root");
                }
                return false;
            }

            if (actualLatest.isPresent() && version == actualLatest.get().version()) {
                throw new JmtWriteConflictException("Latest JMT version " + version
                        + " has no immutable root record");
            }
            return true;
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

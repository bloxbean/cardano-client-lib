package com.bloxbean.cardano.vds.jmt.integrity;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.jmt.JmtExtensionNode;
import com.bloxbean.cardano.vds.jmt.JmtInternalNode;
import com.bloxbean.cardano.vds.jmt.JmtLeafNode;
import com.bloxbean.cardano.vds.jmt.JmtNode;
import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.NodeKey;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessLease;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatDescriptor;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtStoreInspection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Recomputes and validates persisted JMT state without modifying it.
 */
public final class JmtIntegrityChecker {

    private static final int MAX_KEY_NIBBLES = JmtFormatDescriptor.KEY_HASH_LENGTH * 2;

    private final JmtStore store;
    private final JmtProfile profile;

    public JmtIntegrityChecker(JmtStore store, JmtProfile profile) {
        this.store = Objects.requireNonNull(store, "store");
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public JmtIntegrityReport check(JmtIntegrityMode mode) {
        return check(mode, Options.defaults());
    }

    public JmtIntegrityReport check(JmtIntegrityMode mode, Options options) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(options, "options");
        State state = new State(mode, options);

        try (JmtAccessLease ignored = store.accessCoordinator().tryAcquireRead("integrity-check")) {
            validateFormat(state);
            JmtStoreInspection inspection = store.inspect(options.maxRecords);
            state.truncated = inspection.truncated();
            if (inspection.truncated()) {
                state.error("INSPECTION_LIMIT", "Store inspection exceeded maxRecords="
                        + options.maxRecords, null, null);
            }
            for (String backendIssue : inspection.backendIssues()) {
                state.error("BACKEND_INDEX_OR_ENCODING", backendIssue, null, null);
            }

            validateBasicRecords(inspection, state);
            validateLatestPointer(inspection, state);

            if (mode == JmtIntegrityMode.FULL && !state.cancelled()) {
                validateFull(inspection, state);
            }
        } catch (CancelledException ignored) {
            state.cancelled = true;
        }

        return state.report();
    }

    private void validateFormat(State state) {
        Optional<JmtFormatDescriptor> persisted = store.formatDescriptor();
        if (persisted.isEmpty()) {
            state.error("FORMAT_MISSING", "JMT format descriptor is missing", null, null);
        } else if (!persisted.get().equals(profile.format())) {
            state.error("FORMAT_MISMATCH", "Persisted format " + persisted.get()
                    + " does not match checker profile " + profile.format(), null, null);
        }
    }

    private void validateBasicRecords(JmtStoreInspection inspection, State state) {
        Set<Long> rootVersions = new HashSet<>();
        for (JmtStore.VersionedRoot root : inspection.roots()) {
            state.checkCancelled();
            state.rootsChecked++;
            if (!rootVersions.add(root.version())) {
                state.error("DUPLICATE_ROOT_VERSION", "Duplicate root version", root.version(), null);
            }
            if (root.version() < 0) {
                state.error("NEGATIVE_VERSION", "Negative root version", root.version(), null);
            }
            if (root.rootHash().length != profile.format().hashLength()) {
                state.error("ROOT_HASH_LENGTH", "Root hash has length " + root.rootHash().length,
                        root.version(), null);
            }
        }

        int sampledNodes = 0;
        for (JmtStoreInspection.NodeRecord record : inspection.nodes()) {
            state.checkCancelled();
            state.nodesChecked++;
            if (state.mode == JmtIntegrityMode.QUICK && sampledNodes++ >= optionsSampleLimit(state)) {
                continue;
            }
            validateNodeShape(record, state);
        }

        for (JmtStoreInspection.ValueRecord record : inspection.values()) {
            state.checkCancelled();
            state.valuesChecked++;
            if (record.keyHash().length != JmtFormatDescriptor.KEY_HASH_LENGTH) {
                state.error("VALUE_KEY_HASH_LENGTH", "Value key hash has length "
                        + record.keyHash().length, record.version(), null);
            }
            if (record.version() < 0) {
                state.error("NEGATIVE_VERSION", "Negative value version", record.version(), null);
            }
            if (!record.tombstone() && record.value() == null) {
                state.error("NULL_VALUE", "Non-tombstone value is null", record.version(), null);
            }
        }
    }

    private int optionsSampleLimit(State state) {
        return state.options.quickNodeSample;
    }

    private void validateNodeShape(JmtStoreInspection.NodeRecord record, State state) {
        NodeKey key = record.nodeKey();
        if (key.version() < 0) {
            state.error("NEGATIVE_VERSION", "Negative node version", key.version(), key.path());
        }
        if (key.path().length() > MAX_KEY_NIBBLES) {
            state.error("NODE_DEPTH", "Node path exceeds key depth", key.version(), key.path());
        }
        JmtNode node = record.node();
        if (node instanceof JmtLeafNode) {
            JmtLeafNode leaf = (JmtLeafNode) node;
            requireHashLength("leaf key", leaf.keyHash(), key, state);
            requireHashLength("leaf value", leaf.valueHash(), key, state);
        } else if (node instanceof JmtInternalNode) {
            JmtInternalNode internal = (JmtInternalNode) node;
            if (internal.compressedPath() != null) {
                state.error("UNSUPPORTED_COMPRESSED_PATH",
                        "Stable v1 internal nodes do not use compressed paths",
                        key.version(), key.path());
            }
            for (byte[] childHash : internal.childHashes()) {
                requireHashLength("child", childHash, key, state);
            }
        } else if (node instanceof JmtExtensionNode) {
            JmtExtensionNode extension = (JmtExtensionNode) node;
            state.error("UNSUPPORTED_EXTENSION_NODE",
                    "Stable v1 tree does not emit extension nodes", key.version(), key.path());
            requireHashLength("extension child", extension.childHash(), key, state);
            if (extension.hpBytes().length == 0) {
                state.error("EMPTY_EXTENSION", "Extension path is empty", key.version(), key.path());
            }
        } else {
            state.error("UNKNOWN_NODE", "Unknown node type " + node.getClass().getName(),
                    key.version(), key.path());
        }
    }

    private void requireHashLength(String field, byte[] hash, NodeKey key, State state) {
        if (hash.length != profile.format().hashLength()) {
            state.error("NODE_HASH_LENGTH", field + " hash has length " + hash.length,
                    key.version(), key.path());
        }
    }

    private void validateLatestPointer(JmtStoreInspection inspection, State state) {
        Optional<JmtStore.VersionedRoot> latest = inspection.latestRoot();
        if (inspection.roots().isEmpty()) {
            if (latest.isPresent()) {
                state.error("LATEST_WITHOUT_ROOT", "Latest pointer exists without a root record",
                        latest.get().version(), null);
            }
            return;
        }
        JmtStore.VersionedRoot greatest = inspection.roots().stream()
                .max(Comparator.comparingLong(JmtStore.VersionedRoot::version))
                .orElseThrow();
        if (latest.isEmpty()) {
            state.error("LATEST_MISSING", "Root records exist but latest pointer is missing",
                    greatest.version(), null);
        } else if (latest.get().version() != greatest.version()
                || !Arrays.equals(latest.get().rootHash(), greatest.rootHash())) {
            state.error("LATEST_MISMATCH", "Latest pointer does not match greatest root record",
                    latest.get().version(), null);
        }
    }

    private void validateFull(JmtStoreInspection inspection, State state) {
        List<JmtStore.VersionedRoot> selectedRoots = selectRoots(inspection.roots(), state.options);
        Set<NodeKey> reachable = new HashSet<>();
        for (JmtStore.VersionedRoot root : selectedRoots) {
            state.checkCancelled();
            state.progress("root", root.version());
            Map<VisitKey, byte[]> cache = new HashMap<>();
            Set<VisitKey> visiting = new HashSet<>();
            byte[] computed = computeNode(root.version(), NibblePath.EMPTY, cache, visiting,
                    reachable, state);
            if (computed == null) {
                computed = profile.commitmentScheme().nullHash();
            }
            if (!Arrays.equals(computed, root.rootHash())) {
                state.error("ROOT_COMMITMENT_MISMATCH", "Recomputed root does not match persisted root",
                        root.version(), NibblePath.EMPTY);
            }
        }

        Set<NodeKey> staleNodes = new HashSet<>();
        Set<NodeKey> existingNodes = new HashSet<>();
        for (JmtStoreInspection.NodeRecord node : inspection.nodes()) {
            existingNodes.add(node.nodeKey());
        }
        for (JmtStoreInspection.StaleRecord stale : inspection.staleRecords()) {
            staleNodes.add(stale.nodeKey());
            if (stale.staleSince() < stale.nodeKey().version()) {
                state.error("STALE_VERSION_ORDER", "Node is stale before it was created",
                        stale.staleSince(), stale.nodeKey().path());
            }
            if (!existingNodes.contains(stale.nodeKey())) {
                state.error("STALE_NODE_MISSING", "Stale marker references a missing node",
                        stale.staleSince(), stale.nodeKey().path());
            }
        }
        for (NodeKey nodeKey : existingNodes) {
            if (!reachable.contains(nodeKey) && !staleNodes.contains(nodeKey)) {
                state.warning("ORPHAN_NODE", "Node is neither reachable from selected roots nor stale",
                        nodeKey.version(), nodeKey.path());
            }
        }
    }

    private List<JmtStore.VersionedRoot> selectRoots(List<JmtStore.VersionedRoot> roots,
                                                     Options options) {
        List<JmtStore.VersionedRoot> selected = new ArrayList<>();
        if (roots.isEmpty()) {
            return selected;
        }
        if (!options.checkAllVersions && options.fromVersion == null && options.toVersion == null) {
            selected.add(roots.stream().max(Comparator.comparingLong(
                    JmtStore.VersionedRoot::version)).orElseThrow());
            return selected;
        }
        for (JmtStore.VersionedRoot root : roots) {
            if ((options.fromVersion == null || root.version() >= options.fromVersion)
                    && (options.toVersion == null || root.version() <= options.toVersion)) {
                selected.add(root);
            }
        }
        return selected;
    }

    private byte[] computeNode(long version,
                               NibblePath path,
                               Map<VisitKey, byte[]> cache,
                               Set<VisitKey> visiting,
                               Set<NodeKey> reachable,
                               State state) {
        state.checkCancelled();
        VisitKey visitKey = new VisitKey(version, path);
        byte[] cached = cache.get(visitKey);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(visitKey)) {
            state.error("NODE_CYCLE", "Cycle detected while traversing nodes", version, path);
            return profile.commitmentScheme().nullHash();
        }

        Optional<JmtStore.NodeEntry> entryOptional;
        try {
            entryOptional = store.getNode(version, path);
        } catch (RuntimeException e) {
            state.error("NODE_READ_FAILED", e.getMessage(), version, path);
            visiting.remove(visitKey);
            return profile.commitmentScheme().nullHash();
        }
        if (entryOptional.isEmpty()) {
            if (path.length() != 0) {
                state.error("MISSING_CHILD", "Referenced child node is missing", version, path);
            }
            visiting.remove(visitKey);
            return null;
        }

        JmtStore.NodeEntry entry = entryOptional.get();
        reachable.add(entry.nodeKey());
        byte[] computed;
        JmtNode node = entry.node();
        if (node instanceof JmtLeafNode) {
            computed = computeLeaf(version, path, (JmtLeafNode) node, state);
        } else if (node instanceof JmtInternalNode) {
            computed = computeInternal(version, path, (JmtInternalNode) node,
                    cache, visiting, reachable, state);
        } else if (node instanceof JmtExtensionNode) {
            computed = computeExtension(version, path, (JmtExtensionNode) node,
                    cache, visiting, reachable, state);
        } else {
            state.error("UNKNOWN_NODE", "Unknown node type " + node.getClass().getName(),
                    version, path);
            computed = profile.commitmentScheme().nullHash();
        }
        visiting.remove(visitKey);
        cache.put(visitKey, computed);
        return computed;
    }

    private byte[] computeLeaf(long version, NibblePath path, JmtLeafNode leaf, State state) {
        int[] keyNibbles = Nibbles.toNibbles(leaf.keyHash());
        int[] pathNibbles = path.getNibbles();
        if (pathNibbles.length > keyNibbles.length) {
            state.error("LEAF_PATH_DEPTH", "Leaf path exceeds key hash", version, path);
        } else {
            for (int i = 0; i < pathNibbles.length; i++) {
                if (pathNibbles[i] != keyNibbles[i]) {
                    state.error("LEAF_PATH_MISMATCH", "Leaf key does not match its storage path",
                            version, path);
                    break;
                }
            }
        }

        Optional<byte[]> value = store.getValueAt(leaf.keyHash(), version);
        if (value.isEmpty()) {
            state.error("LEAF_VALUE_MISSING", "Leaf has no value at this version", version, path);
        } else {
            byte[] valueHash = profile.hashFunction().digest(value.get());
            if (!Arrays.equals(valueHash, leaf.valueHash())) {
                state.error("LEAF_VALUE_HASH_MISMATCH", "Stored value does not match leaf value hash",
                        version, path);
            }
        }
        return profile.commitmentScheme().commitLeaf(leaf.keyHash(), leaf.valueHash());
    }

    private byte[] computeInternal(long version,
                                   NibblePath path,
                                   JmtInternalNode internal,
                                   Map<VisitKey, byte[]> cache,
                                   Set<VisitKey> visiting,
                                   Set<NodeKey> reachable,
                                   State state) {
        byte[][] fullHashes = new byte[16][];
        byte[][] compressed = internal.childHashes();
        int compressedIndex = 0;
        for (int nibble = 0; nibble < 16; nibble++) {
            if ((internal.bitmap() & (1 << nibble)) == 0) {
                continue;
            }
            byte[] persistedChildHash = compressed[compressedIndex++];
            NibblePath childPath = append(path, nibble);
            byte[] childHash = computeNode(version, childPath, cache, visiting, reachable, state);
            if (childHash == null) {
                childHash = profile.commitmentScheme().nullHash();
            }
            if (!Arrays.equals(childHash, persistedChildHash)) {
                state.error("CHILD_COMMITMENT_MISMATCH", "Child commitment does not match child node",
                        version, childPath);
            }
            fullHashes[nibble] = persistedChildHash;
        }
        NibblePath compressedPath = internal.compressedPath() == null
                ? NibblePath.EMPTY
                : NibblePath.of(Nibbles.toNibbles(internal.compressedPath()));
        return profile.commitmentScheme().commitBranch(compressedPath, fullHashes);
    }

    private byte[] computeExtension(long version,
                                    NibblePath path,
                                    JmtExtensionNode extension,
                                    Map<VisitKey, byte[]> cache,
                                    Set<VisitKey> visiting,
                                    Set<NodeKey> reachable,
                                    State state) {
        Nibbles.HP hp = Nibbles.unpackHP(extension.hpBytes());
        if (hp.isLeaf || hp.nibbles.length == 0) {
            state.error("INVALID_EXTENSION_PATH", "Extension HP path is empty or marked as a leaf",
                    version, path);
        }
        NibblePath childPath = append(path, hp.nibbles);
        byte[] childHash = computeNode(version, childPath, cache, visiting, reachable, state);
        if (childHash == null) {
            childHash = profile.commitmentScheme().nullHash();
        }
        if (!Arrays.equals(childHash, extension.childHash())) {
            state.error("CHILD_COMMITMENT_MISMATCH", "Extension commitment does not match child node",
                    version, childPath);
        }
        return profile.hashFunction().digest(Bytes.concat(
                new byte[]{0x02}, extension.hpBytes(), extension.childHash()));
    }

    private static NibblePath append(NibblePath path, int nibble) {
        return append(path, new int[]{nibble});
    }

    private static NibblePath append(NibblePath path, int[] suffix) {
        int[] prefix = path.getNibbles();
        int[] combined = Arrays.copyOf(prefix, prefix.length + suffix.length);
        System.arraycopy(suffix, 0, combined, prefix.length, suffix.length);
        return NibblePath.fromRaw(combined);
    }

    public static final class Options {
        private final int maxRecords;
        private final int quickNodeSample;
        private final boolean checkAllVersions;
        private final Long fromVersion;
        private final Long toVersion;
        private final BooleanSupplier cancellation;
        private final BiConsumer<String, Long> progress;

        private Options(Builder builder) {
            if (builder.maxRecords <= 0) {
                throw new IllegalArgumentException("maxRecords must be > 0");
            }
            if (builder.quickNodeSample < 0) {
                throw new IllegalArgumentException("quickNodeSample must be >= 0");
            }
            if (builder.fromVersion != null && builder.toVersion != null
                    && builder.fromVersion > builder.toVersion) {
                throw new IllegalArgumentException("fromVersion must be <= toVersion");
            }
            this.maxRecords = builder.maxRecords;
            this.quickNodeSample = builder.quickNodeSample;
            this.checkAllVersions = builder.checkAllVersions;
            this.fromVersion = builder.fromVersion;
            this.toVersion = builder.toVersion;
            this.cancellation = builder.cancellation;
            this.progress = builder.progress;
        }

        public static Options defaults() {
            return builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private int maxRecords = 1_000_000;
            private int quickNodeSample = 256;
            private boolean checkAllVersions;
            private Long fromVersion;
            private Long toVersion;
            private BooleanSupplier cancellation = () -> false;
            private BiConsumer<String, Long> progress = (phase, count) -> { };

            public Builder maxRecords(int maxRecords) {
                this.maxRecords = maxRecords;
                return this;
            }

            public Builder quickNodeSample(int quickNodeSample) {
                this.quickNodeSample = quickNodeSample;
                return this;
            }

            public Builder allVersions(boolean checkAllVersions) {
                this.checkAllVersions = checkAllVersions;
                return this;
            }

            public Builder versionRange(Long fromVersion, Long toVersion) {
                this.fromVersion = fromVersion;
                this.toVersion = toVersion;
                return this;
            }

            public Builder cancellation(BooleanSupplier cancellation) {
                this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
                return this;
            }

            public Builder progress(BiConsumer<String, Long> progress) {
                this.progress = Objects.requireNonNull(progress, "progress");
                return this;
            }

            public Options build() {
                return new Options(this);
            }
        }
    }

    private static final class VisitKey {
        private final long version;
        private final NibblePath path;

        private VisitKey(long version, NibblePath path) {
            this.version = version;
            this.path = path;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof VisitKey)) {
                return false;
            }
            VisitKey other = (VisitKey) obj;
            return version == other.version && path.equals(other.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(version, path);
        }
    }

    private static final class State {
        private final JmtIntegrityMode mode;
        private final Options options;
        private final List<JmtIntegrityIssue> issues = new ArrayList<>();
        private long rootsChecked;
        private long nodesChecked;
        private long valuesChecked;
        private boolean truncated;
        private boolean cancelled;

        private State(JmtIntegrityMode mode, Options options) {
            this.mode = mode;
            this.options = options;
        }

        private void checkCancelled() {
            if (options.cancellation.getAsBoolean()) {
                cancelled = true;
                throw new CancelledException();
            }
        }

        private boolean cancelled() {
            return cancelled;
        }

        private void progress(String phase, long count) {
            options.progress.accept(phase, count);
        }

        private void warning(String code, String message, Long version, NibblePath path) {
            issue(JmtIntegrityIssue.Severity.WARNING, code, message, version, path);
        }

        private void error(String code, String message, Long version, NibblePath path) {
            issue(JmtIntegrityIssue.Severity.ERROR, code, message, version, path);
        }

        private void issue(JmtIntegrityIssue.Severity severity,
                           String code,
                           String message,
                           Long version,
                           NibblePath path) {
            issues.add(new JmtIntegrityIssue(severity, code,
                    message == null ? "No diagnostic message" : message,
                    version, path == null ? null : path.toString()));
        }

        private JmtIntegrityReport report() {
            return new JmtIntegrityReport(mode, issues, rootsChecked, nodesChecked, valuesChecked,
                    truncated, cancelled);
        }
    }

    private static final class CancelledException extends RuntimeException {
    }
}

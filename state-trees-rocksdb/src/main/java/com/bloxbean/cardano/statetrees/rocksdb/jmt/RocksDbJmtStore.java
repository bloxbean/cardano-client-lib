package com.bloxbean.cardano.statetrees.rocksdb.jmt;

import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.JmtEncoding;
import com.bloxbean.cardano.statetrees.jmt.JmtNode;
import com.bloxbean.cardano.statetrees.jmt.NodeKey;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.rocksdb.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbJmtSchema.*;

/**
 * RocksDB-backed implementation of {@link JmtStore}.
 */
public final class RocksDbJmtStore implements JmtStore {

    private final RocksDB db;
    private final ColumnFamilyHandle cfNodes;
    private final ColumnFamilyHandle cfValues;
    private final ColumnFamilyHandle cfRoots;
    private final ColumnFamilyHandle cfStale;
    private final DBOptions options;
    private final List<AutoCloseable> closeables;

    public RocksDbJmtStore(String dbPath) {
        try {
            RocksDB.loadLibrary();
            File directory = new File(dbPath);
            if (!directory.exists()) directory.mkdirs();

            List<byte[]> existing = RocksDB.listColumnFamilies(new org.rocksdb.Options().setCreateIfMissing(true), dbPath);
            ColumnFamilyOptions cfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);

            List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
            Map<String, Integer> indexes = new HashMap<>();

            for (byte[] name : existing) {
                String cfName = new String(name, StandardCharsets.UTF_8);
                ColumnFamilyDescriptor descriptor = new ColumnFamilyDescriptor(name, cfOptions);
                indexes.put(cfName, cfDescriptors.size());
                cfDescriptors.add(descriptor);
            }

            ensureDescriptor(new String(RocksDB.DEFAULT_COLUMN_FAMILY, StandardCharsets.UTF_8),
                    RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions, cfDescriptors, indexes);
            ensureDescriptor(CF_NODES, CF_NODES.getBytes(), cfOptions, cfDescriptors, indexes);
            ensureDescriptor(CF_VALUES, CF_VALUES.getBytes(), cfOptions, cfDescriptors, indexes);
            ensureDescriptor(CF_ROOTS, CF_ROOTS.getBytes(), cfOptions, cfDescriptors, indexes);
            ensureDescriptor(CF_STALE, CF_STALE.getBytes(), cfOptions, cfDescriptors, indexes);

            List<ColumnFamilyHandle> handles = new ArrayList<>();
            this.options = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
            this.db = RocksDB.open(options, dbPath, cfDescriptors, handles);

            this.cfNodes = handles.get(indexes.get(CF_NODES));
            this.cfValues = handles.get(indexes.get(CF_VALUES));
            this.cfRoots = handles.get(indexes.get(CF_ROOTS));
            this.cfStale = handles.get(indexes.get(CF_STALE));

            this.closeables = new ArrayList<>();
            this.closeables.add(cfOptions);
            this.closeables.addAll(handles);
            this.closeables.add(options);
            this.closeables.add(db);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to initialise RocksDbJmtStore", e);
        }
    }

    private static void ensureDescriptor(String key, byte[] nameBytes, ColumnFamilyOptions cfOptions,
                                         List<ColumnFamilyDescriptor> descriptors,
                                         Map<String, Integer> indexes) {
        if (!indexes.containsKey(key)) {
            indexes.put(key, descriptors.size());
            descriptors.add(new ColumnFamilyDescriptor(nameBytes, cfOptions));
        }
    }

    @Override
    public Optional<VersionedRoot> latestRoot() {
        try {
            byte[] root = db.get(cfRoots, LATEST_ROOT_KEY);
            if (root == null) {
                return Optional.empty();
            }
            byte[] versionBytes = db.get(cfRoots, LATEST_VERSION_KEY);
            long version = versionBytes == null ? -1 : ByteBuffer.wrap(versionBytes).getLong();
            if (version < 0) {
                // Fallback to scanning the last entry if version metadata is missing.
                try (ReadOptions readOptions = new ReadOptions();
                     RocksIterator iterator = db.newIterator(cfRoots, readOptions)) {
                    iterator.seekToLast();
                    while (iterator.isValid()) {
                        byte[] key = iterator.key();
                        if (Arrays.equals(key, LATEST_ROOT_KEY) || Arrays.equals(key, LATEST_VERSION_KEY)) {
                            iterator.prev();
                            continue;
                        }
                        version = ByteBuffer.wrap(key).getLong();
                        root = iterator.value();
                        break;
                    }
                }
            }
            if (version < 0) {
                return Optional.empty();
            }
            return Optional.of(new VersionedRoot(version, root));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to read latest JMT root", e);
        }
    }

    @Override
    public Optional<byte[]> rootHash(long version) {
        try {
            byte[] root = db.get(cfRoots, versionKey(version));
            return root == null ? Optional.empty() : Optional.of(root.clone());
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to read JMT root for version " + version, e);
        }
    }

    @Override
    public Optional<NodeEntry> getNode(long version, NibblePath path) {
        Objects.requireNonNull(path, "path");
        byte[] searchKey = NodeKey.of(path, version).toBytes();
        try (ReadOptions options = new ReadOptions();
             RocksIterator iterator = db.newIterator(cfNodes, options)) {
            iterator.seekForPrev(searchKey);
            while (iterator.isValid()) {
                byte[] keyBytes = iterator.key();
                NodeKey nodeKey = NodeKey.fromBytes(keyBytes);
                int cmp = comparePath(nodeKey.path(), path);
                if (cmp < 0) {
                    break;
                }
                if (cmp == 0 && Long.compareUnsigned(nodeKey.version(), version) <= 0) {
                    byte[] value = iterator.value();
                    JmtNode node = JmtEncoding.decode(value);
                    return Optional.of(new NodeEntry(nodeKey, node));
                }
                iterator.prev();
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<JmtNode> getNode(NodeKey nodeKey) {
        Objects.requireNonNull(nodeKey, "nodeKey");
        try {
            byte[] nodeBytes = db.get(cfNodes, nodeKey.toBytes());
            return nodeBytes == null ? Optional.empty() : Optional.of(JmtEncoding.decode(nodeBytes));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to load JMT node", e);
        }
    }

    @Override
    public Optional<NodeEntry> floorNode(long version, NibblePath path) {
        Objects.requireNonNull(path, "path");
        byte[] searchKey = NodeKey.of(path, version).toBytes();
        try (ReadOptions options = new ReadOptions();
             RocksIterator iterator = db.newIterator(cfNodes, options)) {
            iterator.seekForPrev(searchKey);
            while (iterator.isValid()) {
                byte[] keyBytes = iterator.key();
                NodeKey nodeKey = NodeKey.fromBytes(keyBytes);
                if (comparePath(nodeKey.path(), path) <= 0 &&
                    Long.compareUnsigned(nodeKey.version(), version) <= 0) {
                    JmtNode node = JmtEncoding.decode(iterator.value());
                    return Optional.of(new NodeEntry(nodeKey, node));
                }
                iterator.prev();
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<byte[]> getValue(byte[] keyHash) {
        Objects.requireNonNull(keyHash, "keyHash");
        try {
            byte[] value = db.get(cfValues, keyHash);
            return value == null ? Optional.empty() : Optional.of(value.clone());
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to load JMT value", e);
        }
    }

    @Override
    public CommitBatch beginCommit(long version, CommitConfig config) {
        Objects.requireNonNull(config, "config");
        return new RocksCommitBatch(version);
    }

    public void applyCommit(JellyfishMerkleTree.CommitResult result) {
        Objects.requireNonNull(result, "result");
        try (CommitBatch batch = beginCommit(result.version(), CommitConfig.defaults())) {
            for (Map.Entry<NodeKey, JmtNode> entry : result.nodes().entrySet()) {
                batch.putNode(entry.getKey(), entry.getValue());
            }
            for (NodeKey stale : result.staleNodes()) {
                batch.markStale(stale);
            }
            for (JellyfishMerkleTree.CommitResult.ValueOperation op : result.valueOperations()) {
                switch (op.type()) {
                    case PUT:
                        batch.putValue(op.keyHash(), op.value());
                        break;
                    case DELETE:
                        batch.deleteValue(op.keyHash());
                        break;
                    default:
                        throw new IllegalStateException("Unhandled value operation: " + op.type());
                }
            }
            batch.setRootHash(result.rootHash());
            batch.commit();
        }
    }

    @Override
    public List<NodeKey> staleNodesUpTo(long versionInclusive) {
        List<NodeKey> results = new ArrayList<>();
        try (ReadOptions readOptions = new ReadOptions();
             RocksIterator iterator = db.newIterator(cfStale, readOptions)) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                byte[] key = iterator.key();
                long staleSince = ByteBuffer.wrap(key, 0, 8).getLong();
                if (staleSince > versionInclusive) break;
                byte[] nodeKeyBytes = Arrays.copyOfRange(key, 8, key.length);
                results.add(NodeKey.fromBytes(nodeKeyBytes));
            }
        }
        return Collections.unmodifiableList(results);
    }

    @Override
    public int pruneUpTo(long versionInclusive) {
        int pruned = 0;
        try (WriteBatch batch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions();
             ReadOptions readOptions = new ReadOptions();
             RocksIterator iterator = db.newIterator(cfStale, readOptions)) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                byte[] key = iterator.key();
                long staleSince = ByteBuffer.wrap(key, 0, 8).getLong();
                if (staleSince > versionInclusive) {
                    break;
                }
                byte[] nodeKeyBytes = Arrays.copyOfRange(key, 8, key.length);
                batch.delete(cfNodes, nodeKeyBytes);
                batch.delete(cfStale, key);
                pruned++;
            }
            if (pruned > 0) {
                db.write(writeOptions, batch);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to prune JMT nodes", e);
        }
        return pruned;
    }

    @Override
    public void close() {
        Collections.reverse(closeables);
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Best effort; cleanup failures are non-fatal.
            }
        }
    }

    private int comparePath(NibblePath a, NibblePath b) {
        int[] an = a.getNibbles();
        int[] bn = b.getNibbles();
        int len = Math.min(an.length, bn.length);
        for (int i = 0; i < len; i++) {
            int diff = Integer.compare(an[i], bn[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return Integer.compare(an.length, bn.length);
    }

    private static byte[] staleKey(long version, NodeKey nodeKey) {
        byte[] nodeBytes = nodeKey.toBytes();
        byte[] key = new byte[8 + nodeBytes.length];
        ByteBuffer.wrap(key).putLong(version).put(nodeBytes);
        return key;
    }

    private static byte[] versionKey(long version) {
        return ByteBuffer.allocate(Long.BYTES).putLong(version).array();
    }

    private final class RocksCommitBatch implements CommitBatch {

        private final long version;
        private final WriteBatch batch = new WriteBatch();
        private final WriteOptions writeOptions = new WriteOptions();
        private boolean committed;
        private byte[] rootHash;

        private RocksCommitBatch(long version) {
            this.version = version;
        }

        @Override
        public void putNode(NodeKey nodeKey, JmtNode node) {
            Objects.requireNonNull(nodeKey, "nodeKey");
            Objects.requireNonNull(node, "node");
            try {
                batch.put(cfNodes, nodeKey.toBytes(), node.encode());
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to stage node write", e);
            }
        }

        @Override
        public void markStale(NodeKey nodeKey) {
            Objects.requireNonNull(nodeKey, "nodeKey");
            try {
                batch.put(cfStale, staleKey(version, nodeKey), new byte[0]);
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to stage stale marker", e);
            }
        }

        @Override
        public void putValue(byte[] keyHash, byte[] value) {
            Objects.requireNonNull(keyHash, "keyHash");
            Objects.requireNonNull(value, "value");
            try {
                batch.put(cfValues, keyHash, value.clone());
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to stage value write", e);
            }
        }

        @Override
        public void deleteValue(byte[] keyHash) {
            Objects.requireNonNull(keyHash, "keyHash");
            try {
                batch.delete(cfValues, keyHash);
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to stage value delete", e);
            }
        }

        @Override
        public void setRootHash(byte[] rootHash) {
            this.rootHash = rootHash == null ? null : rootHash.clone();
        }

        @Override
        public void commit() {
            if (committed) {
                throw new IllegalStateException("Commit already applied");
            }
            try {
                if (rootHash != null) {
                    batch.put(cfRoots, versionKey(version), rootHash);
                    batch.put(cfRoots, LATEST_ROOT_KEY, rootHash);
                    batch.put(cfRoots, LATEST_VERSION_KEY, versionKey(version));
                }
                db.write(writeOptions, batch);
                committed = true;
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to flush JMT batch", e);
            } finally {
                closeInternal();
            }
        }

        @Override
        public void close() {
            if (!committed) {
                closeInternal();
            }
        }

        private void closeInternal() {
            try {
                batch.close();
            } catch (Exception ignored) {
            }
            try {
                writeOptions.close();
            } catch (Exception ignored) {
            }
            committed = true;
        }
    }
}

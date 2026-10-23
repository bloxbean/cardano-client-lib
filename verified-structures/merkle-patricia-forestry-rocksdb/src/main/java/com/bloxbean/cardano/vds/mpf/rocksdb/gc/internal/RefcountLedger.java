package com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.mpf.internal.NodeRefParser;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Reference counts of MPF nodes, staged into one {@link WriteBatch}.
 *
 * <p>A node's count is the number of recorded versions whose trie contains it. Counts live in the nodes column
 * family under {@code [namespace][0xF0][node hash]} as an 8-byte big-endian long.</p>
 *
 * <p>When a ledger is created it replays the records currently in the batch ({@link WriteBatch#iterate}) for its
 * column family and namespace, last write wins, and layers database reads under them. So it sees counts and node
 * deletions staged by earlier calls into the same batch, and stays correct when the batch was cleared, rolled back
 * to a save point, or written and reused. A node whose deletion is staged in the batch is written back when an
 * increment counts it again. Nodes staged in the batch are read from it, so the
 * trie nodes of a root may be staged in the same batch. Afterwards the ledger tracks its own writes in memory, so
 * use one ledger for one operation (or one GC run) on a batch nobody else writes to meanwhile.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>{@link #increment} adds one to every node reachable from a root. The root node must be readable through
 *       the node reader (committed, or staged in the caller's batch); otherwise it throws, because counting a
 *       root whose nodes are not visible would only count the root and later free live nodes.</li>
 *   <li>{@link #decrement} subtracts one along the same walk and deletes a node only on a 1 to 0 transition. A
 *       node without a count is untracked: it is neither deleted nor descended into (a leak, never a loss). A node
 *       in the optional protected set is never deleted: its count stays at 1 (a leak, never a loss).</li>
 *   <li>The all-zero root (the empty trie) has no node and is a no-op.</li>
 * </ul>
 *
 * <p>Internal API: it may change without notice.</p>
 */
public final class RefcountLedger {

    private static final Logger log = LoggerFactory.getLogger(RefcountLedger.class);

    /** First byte (after the namespace byte) of a refcount key. */
    public static final byte REFCOUNT_TAG = (byte) 0xF0;

    /** Reads an encoded node by its hash, or returns {@code null}. */
    @FunctionalInterface
    public interface NodeReader {
        byte[] read(byte[] hash) throws RocksDBException;
    }

    /** Nodes that must never be deleted. */
    @FunctionalInterface
    public interface Protection {
        boolean isProtected(byte[] hash) throws RocksDBException;
    }

    private static final byte[] TOMBSTONE = new byte[0]; // compared by identity

    private final RocksDB db;
    private final ColumnFamilyHandle cfNodes;
    private final KeyPrefixer keyPrefixer;
    private final WriteBatch batch;
    private final ReadOptions readOptions;
    private final NodeReader nodeReader;
    /** Pending writes of this column family and namespace: prefixed key hex to value, or {@link #TOMBSTONE}. */
    private final Map<String, byte[]> pending;
    /** Latest bytes put for each key in the batch, kept even when a later record deletes the key. */
    private final Map<String, byte[]> lastPut;
    private Protection protection;
    private long untracked;
    private long protectedNodes;

    /**
     * @param db          the database
     * @param cfNodes     the nodes column family (refcounts live there too)
     * @param keyPrefixer the namespace prefixer
     * @param batch       the batch that receives refcount writes and node deletes; its current records are replayed
     * @param readOptions options for reading counts (e.g. a snapshot), or {@code null}
     * @param nodeReader  how to read nodes while walking a trie
     * @throws RocksDBException if the batch cannot be replayed
     */
    public RefcountLedger(RocksDB db, ColumnFamilyHandle cfNodes, KeyPrefixer keyPrefixer, WriteBatch batch,
                          ReadOptions readOptions, NodeReader nodeReader) throws RocksDBException {
        this.db = db;
        this.cfNodes = cfNodes;
        this.keyPrefixer = keyPrefixer;
        this.batch = batch;
        this.readOptions = readOptions;
        this.nodeReader = nodeReader;
        this.pending = new HashMap<>();
        this.lastPut = new HashMap<>();
        replay(batch, cfNodes.getID(), keyPrefixer, pending, lastPut);
    }

    /**
     * Collects the puts and deletes the batch holds for one column family and namespace (last write wins), and the
     * latest bytes put per key. Merge, range-delete and blob-index records on this column family that may touch the
     * namespace cannot be interpreted, so they are rejected before anything is staged.
     */
    private static void replay(WriteBatch batch, int cfId, KeyPrefixer keyPrefixer, Map<String, byte[]> pending,
                               Map<String, byte[]> lastPut) throws RocksDBException {
        if (batch.count() == 0) return;
        byte[] nsStart = keyPrefixer.namespaceStart();
        byte[] nsEnd = (keyPrefixer.getNamespaceId() & 0xFF) == 0xFF ? null
                : new byte[]{(byte) ((keyPrefixer.getNamespaceId() & 0xFF) + 1)};
        String[] unsupported = {null};
        try (WriteBatch.Handler handler = new WriteBatch.Handler() {
            private void record(int cf, byte[] key, byte[] value) {
                if (cf != cfId || !keyPrefixer.hasCorrectPrefix(key)) return;
                String hex = HexUtil.encodeHexString(key);
                pending.put(hex, value);
                if (value != TOMBSTONE) lastPut.put(hex, value);
            }

            private void unsupportedKey(String kind, int cf, byte[] key) {
                if (cf == cfId && keyPrefixer.hasCorrectPrefix(key) && unsupported[0] == null) unsupported[0] = kind;
            }

            private void unsupportedRange(int cf, byte[] begin, byte[] end) {
                if (cf != cfId || unsupported[0] != null) return;
                // [begin, end) overlaps [nsStart, nsEnd) unless it ends at or before nsStart or starts at or after nsEnd
                boolean before = Arrays.compareUnsigned(end, nsStart) <= 0;
                boolean after = nsEnd != null && Arrays.compareUnsigned(begin, nsEnd) >= 0;
                if (!before && !after) unsupported[0] = "deleteRange";
            }

            @Override public void put(int cf, byte[] key, byte[] value) { record(cf, key, value); }
            @Override public void put(byte[] key, byte[] value) { record(0, key, value); }
            @Override public void delete(int cf, byte[] key) { record(cf, key, TOMBSTONE); }
            @Override public void delete(byte[] key) { record(0, key, TOMBSTONE); }
            @Override public void singleDelete(int cf, byte[] key) { record(cf, key, TOMBSTONE); }
            @Override public void singleDelete(byte[] key) { record(0, key, TOMBSTONE); }
            @Override public void merge(int cf, byte[] key, byte[] value) { unsupportedKey("merge", cf, key); }
            @Override public void merge(byte[] key, byte[] value) { unsupportedKey("merge", 0, key); }
            @Override public void deleteRange(int cf, byte[] begin, byte[] end) { unsupportedRange(cf, begin, end); }
            @Override public void deleteRange(byte[] begin, byte[] end) { unsupportedRange(0, begin, end); }
            @Override public void logData(byte[] blob) { }
            @Override public void putBlobIndex(int cf, byte[] key, byte[] value) { unsupportedKey("putBlobIndex", cf, key); }
            @Override public void markBeginPrepare() { }
            @Override public void markEndPrepare(byte[] xid) { }
            @Override public void markNoop(boolean emptyBatch) { }
            @Override public void markRollback(byte[] xid) { }
            @Override public void markCommit(byte[] xid) { }
            @Override public void markCommitWithTimestamp(byte[] xid, byte[] ts) { }
        }) {
            batch.iterate(handler);
        }
        if (unsupported[0] != null) {
            throw new IllegalStateException("The WriteBatch holds a " + unsupported[0] + " record on the nodes column "
                    + "family of namespace " + keyPrefixer + ", which refcount bookkeeping cannot interpret. Stage "
                    + "refcount updates in a batch without merge / deleteRange / blob-index records for this namespace.");
        }
    }

    /**
     * Sets the nodes {@link #decrement} must never delete.
     *
     * @param protection the protected set, or {@code null}
     * @return this ledger
     */
    public RefcountLedger withProtection(Protection protection) {
        this.protection = protection;
        return this;
    }

    /**
     * Returns the refcount key of a node, without the namespace byte.
     *
     * @param nodeHash the node hash
     * @return {@code 0xF0 || nodeHash}
     */
    public static byte[] refKey(byte[] nodeHash) {
        byte[] out = new byte[1 + nodeHash.length];
        out[0] = REFCOUNT_TAG;
        System.arraycopy(nodeHash, 0, out, 1, nodeHash.length);
        return out;
    }

    /**
     * Returns the current count of a node, including counts staged in the batch.
     *
     * @param hash the node hash
     * @return the count, 0 when untracked
     * @throws RocksDBException if the read fails
     */
    public long count(byte[] hash) throws RocksDBException {
        byte[] key = keyPrefixer.prefix(refKey(hash));
        byte[] v = pending.get(HexUtil.encodeHexString(key));
        if (v == null) v = readOptions != null ? db.get(cfNodes, readOptions, key) : db.get(cfNodes, key);
        if (v == null || v.length != 8) return 0L;
        return ByteBuffer.wrap(v).getLong();
    }

    private void setCount(byte[] hash, long value) throws RocksDBException {
        byte[] key = keyPrefixer.prefix(refKey(hash));
        if (value <= 0) {
            batch.delete(cfNodes, key);
            pending.put(HexUtil.encodeHexString(key), TOMBSTONE);
        } else {
            byte[] v = ByteBuffer.allocate(8).putLong(value).array();
            batch.put(cfNodes, key, v);
            pending.put(HexUtil.encodeHexString(key), v);
        }
    }

    /**
     * Reads a node: a put staged in the batch first, then the node reader, then (for a node whose deletion is
     * staged) the last bytes the batch put for it, so a staged-only node deleted earlier in the batch can be restored.
     */
    private byte[] readNode(byte[] hash) throws RocksDBException {
        String hex = HexUtil.encodeHexString(keyPrefixer.prefix(hash));
        byte[] staged = pending.get(hex);
        if (staged != null && staged != TOMBSTONE) return staged;
        byte[] enc = nodeReader.read(hash);
        return enc != null ? enc : lastPut.get(hex);
    }

    private boolean nodeDeletionPending(byte[] hash) {
        return pending.get(HexUtil.encodeHexString(keyPrefixer.prefix(hash))) == TOMBSTONE;
    }

    private void deleteNode(byte[] hash, byte[] enc) throws RocksDBException {
        byte[] key = keyPrefixer.prefix(hash);
        String hex = HexUtil.encodeHexString(key);
        batch.delete(cfNodes, key);
        pending.put(hex, TOMBSTONE);
        if (enc != null) lastPut.put(hex, enc);
    }

    private void restoreNode(byte[] hash, byte[] enc) throws RocksDBException {
        byte[] key = keyPrefixer.prefix(hash);
        String hex = HexUtil.encodeHexString(key);
        batch.put(cfNodes, key, enc);
        pending.put(hex, enc);
        lastPut.put(hex, enc);
    }

    /**
     * Adds one to the count of every node reachable from {@code root}.
     *
     * @param root the root hash
     * @return the number of nodes counted
     * @throws IllegalStateException if the root node is not readable
     * @throws RocksDBException      if a read or write fails
     */
    public long increment(byte[] root) throws RocksDBException {
        if (root == null || root.length == 0 || GcRoots.isZeroRoot(root)) return 0;
        if (readNode(root) == null) {
            throw new IllegalStateException("Root node " + HexUtil.encodeHexString(root) + " is not in the store. "
                    + "Commit the trie nodes (or call this inside the same nodeStore.withBatch / RocksDbMptSession.write "
                    + "that stages them, through RocksDbStateTrees.putRootWithRefcount) before recording refcounts.");
        }
        long touched = 0;
        long missing = 0;
        Deque<byte[]> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            byte[] h = queue.removeFirst();
            if (!seen.add(HexUtil.encodeHexString(h))) continue;
            byte[] enc = readNode(h);
            if (enc == null) {
                if (nodeDeletionPending(h)) {
                    throw new IllegalStateException("Node " + HexUtil.encodeHexString(h) + " of root "
                            + HexUtil.encodeHexString(root) + " is deleted in this batch and its bytes are neither "
                            + "committed nor staged, so it cannot be restored");
                }
                missing++;
                continue;
            }
            if (nodeDeletionPending(h)) {
                restoreNode(h, enc); // a decrement staged its deletion in this batch; it is live again
            }
            setCount(h, count(h) + 1);
            touched++;
            queue.addAll(NodeRefParser.childRefs(enc));
        }
        if (missing > 0) {
            log.warn("Root {} references {} node(s) missing from the store; they were not counted",
                    HexUtil.encodeHexString(root), missing);
        }
        return touched;
    }

    /**
     * Subtracts one from the count of every tracked node reachable from {@code root} and stages the deletion of
     * nodes whose count goes from 1 to 0, unless they are protected.
     *
     * @param root the root hash
     * @return {@code [nodes decremented, nodes deleted]}
     * @throws RocksDBException if a read or write fails
     */
    public long[] decrement(byte[] root) throws RocksDBException {
        if (root == null || root.length == 0 || GcRoots.isZeroRoot(root)) return new long[]{0, 0};
        long touched = 0, deleted = 0;
        Deque<byte[]> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            byte[] h = queue.removeFirst();
            if (!seen.add(HexUtil.encodeHexString(h))) continue;
            long cnt = count(h);
            if (cnt <= 0) {
                untracked++;
                continue; // never counted: leave it and its subtree alone
            }
            byte[] enc = readNode(h);
            if (cnt == 1) {
                if (protection != null && protection.isProtected(h)) {
                    protectedNodes++; // still reachable from a kept root: keep it and its count (a leak at worst)
                } else {
                    setCount(h, 0);
                    deleteNode(h, enc);
                    deleted++;
                }
            } else {
                setCount(h, cnt - 1);
            }
            touched++;
            if (enc != null) queue.addAll(NodeRefParser.childRefs(enc));
        }
        return new long[]{touched, deleted};
    }

    /**
     * Returns how many untracked nodes {@link #decrement} skipped so far.
     *
     * @return the number of skipped untracked nodes
     */
    public long untracked() {
        return untracked;
    }

    /**
     * Returns how many nodes {@link #decrement} kept because they are protected although their count reached 1.
     *
     * @return the number of protected nodes
     */
    public long protectedNodes() {
        return protectedNodes;
    }
}

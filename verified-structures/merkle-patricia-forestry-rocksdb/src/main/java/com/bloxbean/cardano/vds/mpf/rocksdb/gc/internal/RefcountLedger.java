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
 * <p>Every ledger that writes to the same {@link WriteBatch} instance for the same database, column family and
 * namespace shares one {@link State}: the counts staged so far and the nodes whose deletion is staged. So any mix
 * of increments and decrements staged in one batch sees each other (read-your-writes), and a node whose deletion
 * was staged by a decrement is written back when a later increment in the same batch counts it again. The state is
 * kept as long as the batch object is reachable; do not reuse a batch after writing it.</p>
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

    /** Counts and node deletions staged in one batch for one database / column family / namespace. */
    static final class State {
        final Map<String, Long> counts = new HashMap<>();
        final Set<String> pendingNodeDeletes = new HashSet<>();
    }

    private static final class Scope {
        final RocksDB db;
        final int cfId;
        final byte namespace;

        Scope(RocksDB db, int cfId, byte namespace) {
            this.db = db;
            this.cfId = cfId;
            this.namespace = namespace;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Scope)) return false;
            Scope s = (Scope) o;
            return s.db == db && s.cfId == cfId && s.namespace == namespace;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * System.identityHashCode(db) + cfId) + namespace;
        }
    }

    // WriteBatch does not override equals/hashCode, so this is keyed by batch identity; entries go away with the batch.
    private static final Map<WriteBatch, Map<Scope, State>> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private final RocksDB db;
    private final ColumnFamilyHandle cfNodes;
    private final KeyPrefixer keyPrefixer;
    private final WriteBatch batch;
    private final ReadOptions readOptions;
    private final NodeReader nodeReader;
    private final State state;
    private Protection protection;
    private long untracked;
    private long protectedNodes;

    /**
     * @param db          the database
     * @param cfNodes     the nodes column family (refcounts live there too)
     * @param keyPrefixer the namespace prefixer
     * @param batch       the batch that receives refcount writes and node deletes
     * @param readOptions options for reading counts (e.g. a snapshot), or {@code null}
     * @param nodeReader  how to read nodes while walking a trie
     */
    public RefcountLedger(RocksDB db, ColumnFamilyHandle cfNodes, KeyPrefixer keyPrefixer, WriteBatch batch,
                          ReadOptions readOptions, NodeReader nodeReader) {
        this.db = db;
        this.cfNodes = cfNodes;
        this.keyPrefixer = keyPrefixer;
        this.batch = batch;
        this.readOptions = readOptions;
        this.nodeReader = nodeReader;
        Scope scope = new Scope(db, cfNodes.getID(), keyPrefixer.getNamespaceId());
        synchronized (STATES) {
            this.state = STATES.computeIfAbsent(batch, b -> new HashMap<>()).computeIfAbsent(scope, s -> new State());
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
     * Returns the current count of a node, including counts staged in the same batch.
     *
     * @param hash the node hash
     * @return the count, 0 when untracked
     * @throws RocksDBException if the read fails
     */
    public long count(byte[] hash) throws RocksDBException {
        Long staged = state.counts.get(HexUtil.encodeHexString(hash));
        if (staged != null) return staged;
        byte[] key = keyPrefixer.prefix(refKey(hash));
        byte[] v = readOptions != null ? db.get(cfNodes, readOptions, key) : db.get(cfNodes, key);
        if (v == null || v.length != 8) return 0L;
        return ByteBuffer.wrap(v).getLong();
    }

    private void setCount(byte[] hash, long value) throws RocksDBException {
        state.counts.put(HexUtil.encodeHexString(hash), value);
        byte[] key = keyPrefixer.prefix(refKey(hash));
        if (value <= 0) {
            batch.delete(cfNodes, key);
        } else {
            batch.put(cfNodes, key, ByteBuffer.allocate(8).putLong(value).array());
        }
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
        if (nodeReader.read(root) == null) {
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
            String hex = HexUtil.encodeHexString(h);
            if (!seen.add(hex)) continue;
            byte[] enc = nodeReader.read(h);
            if (enc == null) {
                missing++;
                continue;
            }
            if (state.pendingNodeDeletes.remove(hex)) {
                // A decrement earlier in this batch staged its deletion; it is live again.
                batch.put(cfNodes, keyPrefixer.prefix(h), enc);
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
            String hex = HexUtil.encodeHexString(h);
            if (!seen.add(hex)) continue;
            long cnt = count(h);
            if (cnt <= 0) {
                untracked++;
                continue; // never counted: leave it and its subtree alone
            }
            byte[] enc = nodeReader.read(h);
            if (cnt == 1) {
                if (protection != null && protection.isProtected(h)) {
                    protectedNodes++; // still reachable from a kept root: keep it and its count (a leak at worst)
                } else {
                    setCount(h, 0);
                    batch.delete(cfNodes, keyPrefixer.prefix(h));
                    state.pendingNodeDeletes.add(hex);
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

package com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal;

import com.bloxbean.cardano.vds.mpf.internal.NodeRefParser;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/**
 * Set of node hashes kept in a temporary column family, so marking large tries does not need memory.
 *
 * <p>The column family is named {@value #CF_PREFIX}{@code <nanoTime>} and dropped by {@link #close()}. A run that
 * crashes leaves it behind; {@link #dropStale(RocksDB, List, Collection)} removes such leftovers when a database is
 * opened. Only names matching exactly {@code __vds_gc_marks_<digits>}, or the 0.8.0-pre6 name {@code marks_<digits>},
 * are treated as leftovers, so application column families are never touched.</p>
 *
 * <p>Internal API: it may change without notice.</p>
 */
public final class DiskMarkSet implements AutoCloseable {

    /** Name prefix of the temporary column families. */
    public static final String CF_PREFIX = "__vds_gc_marks_";

    private static final java.util.regex.Pattern CURRENT_NAME = java.util.regex.Pattern.compile("__vds_gc_marks_[0-9]+");
    private static final java.util.regex.Pattern LEGACY_NAME = java.util.regex.Pattern.compile("marks_[0-9]+");

    private static final byte[] ONE = new byte[]{1};

    private final RocksDB db;
    private final ColumnFamilyOptions cfOptions;
    private final ColumnFamilyHandle cfMarks;

    private DiskMarkSet(RocksDB db, ColumnFamilyOptions cfOptions, ColumnFamilyHandle cfMarks) {
        this.db = db;
        this.cfOptions = cfOptions;
        this.cfMarks = cfMarks;
    }

    /**
     * Creates an empty mark set in a new, uniquely named column family.
     *
     * @param db the database
     * @return the mark set; close it to drop the column family
     * @throws RocksDBException if the column family cannot be created
     */
    public static DiskMarkSet create(RocksDB db) throws RocksDBException {
        ColumnFamilyOptions options = new ColumnFamilyOptions();
        try {
            String name = CF_PREFIX + Long.toUnsignedString(System.nanoTime());
            ColumnFamilyHandle handle = db.createColumnFamily(
                    new ColumnFamilyDescriptor(name.getBytes(StandardCharsets.UTF_8), options));
            return new DiskMarkSet(db, options, handle);
        } catch (RocksDBException | RuntimeException e) {
            options.close();
            throw e;
        }
    }

    /**
     * Marks every node reachable from {@code roots}, reading nodes from the live database (marking a superset of
     * what any snapshot holds is always the safe direction).
     *
     * @param cfNodes     the nodes column family
     * @param keyPrefixer the namespace prefixer
     * @param roots       the roots to walk from
     * @return the number of hashes marked (including roots whose node is absent)
     * @throws RocksDBException if a read or write fails
     */
    public long markReachable(ColumnFamilyHandle cfNodes, KeyPrefixer keyPrefixer, Collection<byte[]> roots)
            throws RocksDBException {
        Deque<byte[]> q = new ArrayDeque<>(roots);
        long marked = 0;
        while (!q.isEmpty()) {
            byte[] h = q.removeFirst();
            if (contains(h)) continue;
            db.put(cfMarks, h, ONE);
            marked++;
            byte[] enc = db.get(cfNodes, keyPrefixer.prefix(h));
            if (enc == null) continue;
            for (byte[] ch : NodeRefParser.childRefs(enc)) q.addLast(ch);
        }
        return marked;
    }

    /**
     * Returns whether {@code hash} is marked.
     *
     * @param hash a node hash (without namespace prefix)
     * @return {@code true} if marked
     * @throws RocksDBException if the read fails
     */
    public boolean contains(byte[] hash) throws RocksDBException {
        return db.get(cfMarks, hash) != null;
    }

    /** Drops the column family and releases its handle and options. */
    @Override
    public void close() {
        try {
            db.dropColumnFamily(cfMarks);
        } catch (Exception ignored) {
        }
        try {
            cfMarks.close();
        } catch (Exception ignored) {
        }
        cfOptions.close();
    }

    /**
     * Returns whether a column family name is a mark-set column family: exactly {@code __vds_gc_marks_<digits>}, or
     * {@code marks_<digits>} as created by 0.8.0-pre6.
     *
     * @param cfName the column family name
     * @return {@code true} for a mark-set name
     */
    public static boolean isMarkSetColumnFamily(byte[] cfName) {
        return isMarkSetColumnFamily(new String(cfName, StandardCharsets.UTF_8));
    }

    /**
     * Same as {@link #isMarkSetColumnFamily(byte[])} for a name given as a string.
     *
     * @param cfName the column family name
     * @return {@code true} for a mark-set name
     */
    public static boolean isMarkSetColumnFamily(String cfName) {
        return CURRENT_NAME.matcher(cfName).matches() || LEGACY_NAME.matcher(cfName).matches();
    }

    /**
     * Drops mark-set column families left behind by crashed GC runs. Call only right after opening the database,
     * before any GC can run on it. Handles of dropped column families are closed and removed from {@code handles};
     * handles in {@code keep} (e.g. the column families a store resolved for itself) are never dropped.
     *
     * @param db      the freshly opened database
     * @param handles the handles returned by {@code RocksDB.open}
     * @param keep    handles that must not be dropped, compared by identity; may be empty
     * @return the number of column families dropped
     */
    public static int dropStale(RocksDB db, List<ColumnFamilyHandle> handles, Collection<ColumnFamilyHandle> keep) {
        int[] dropped = {0};
        handles.removeIf(h -> {
            for (ColumnFamilyHandle k : keep) if (k == h) return false;
            try {
                if (!isMarkSetColumnFamily(h.getName())) return false;
                db.dropColumnFamily(h);
                h.close();
                dropped[0]++;
                return true;
            } catch (RocksDBException e) {
                return false; // leave it; it is only wasted space
            }
        });
        return dropped[0];
    }
}

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
 * <p>The column family is named {@value #CF_PREFIX}{@code <nanoTime>}, holds an ownership marker written right
 * after it is created, and is dropped by {@link #close()}. A run that crashes leaves it behind;
 * {@link #dropStale(RocksDB, List, Collection)} removes such leftovers when a database is opened, but only a column
 * family named exactly {@code __vds_gc_marks_<digits>} that holds the marker (or nothing at all). Column families
 * named like the 0.8.0-pre6 mark sets ({@code marks_<digits>}) carry no ownership information, so they are never
 * dropped automatically; {@link #dropLegacy} drops the ones a caller names explicitly.</p>
 *
 * <p>Internal API: it may change without notice.</p>
 */
public final class DiskMarkSet implements AutoCloseable {

    /** Name prefix of the temporary column families. */
    public static final String CF_PREFIX = "__vds_gc_marks_";

    private static final java.util.regex.Pattern CURRENT_NAME = java.util.regex.Pattern.compile("__vds_gc_marks_[0-9]+");
    // 0.8.0-pre6 named the CF "marks_" + System.nanoTime(): ten or more digits in practice.
    private static final java.util.regex.Pattern LEGACY_NAME = java.util.regex.Pattern.compile("marks_[0-9]{10,}");
    // Ownership marker: not a node hash (those are digest-length), so it never collides with a mark.
    private static final byte[] OWNER_KEY = "__vds_gc_mark_set_owner__".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OWNER_VALUE = "cardano-client-lib verified-structures GC".getBytes(StandardCharsets.UTF_8);
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiskMarkSet.class);

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
            try {
                markOwned(db, handle);
            } catch (RocksDBException | RuntimeException e) {
                db.dropColumnFamily(handle);
                handle.close();
                throw e;
            }
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
     * Writes the ownership marker that lets {@link #dropStale} recognise a leftover mark set. {@link #create} does
     * this; it is public for tests that simulate a crashed GC run.
     *
     * @param db the database
     * @param cf a column family named {@code __vds_gc_marks_<digits>}
     * @throws RocksDBException if the write fails
     */
    public static void markOwned(RocksDB db, ColumnFamilyHandle cf) throws RocksDBException {
        db.put(cf, OWNER_KEY, OWNER_VALUE);
    }

    /**
     * Returns whether a column family name is a mark-set column family name: exactly {@code __vds_gc_marks_<digits>},
     * or {@code marks_<at least 10 digits>} as created by 0.8.0-pre6. Stores refuse to use such names for their own
     * column families. A name alone is never enough to drop a column family (see {@link #dropStale} and
     * {@link #dropLegacy}).
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
     * Drops mark-set column families left behind by crashed GC runs: column families named exactly
     * {@code __vds_gc_marks_<digits>} that hold the ownership marker {@link #create} writes, or nothing at all. Call
     * only right after opening the database, before any GC can run on it. Handles of dropped column families are
     * closed and removed from {@code handles}; handles in {@code keep} (e.g. the column families a store resolved for
     * itself) are never dropped. Column families with a 0.8.0-pre6 mark-set name ({@code marks_<digits>}) are left
     * alone with a warning; see {@link #dropLegacy}.
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
                String name = new String(h.getName(), StandardCharsets.UTF_8);
                if (LEGACY_NAME.matcher(name).matches()) {
                    log.warn("Column family {} is named like a 0.8.0-pre6 GC mark set but may hold application data; "
                            + "leaving it. If it is a leftover of a crashed GC run, drop it with "
                            + "GcManager.dropLegacyGcColumnFamilies.", name);
                    return false;
                }
                if (!CURRENT_NAME.matcher(name).matches()) return false;
                if (!ownedOrEmpty(db, h)) {
                    log.warn("Column family {} has a reserved GC name but no GC ownership marker; leaving it", name);
                    return false;
                }
                drop(db, h);
                dropped[0]++;
                return true;
            } catch (RocksDBException e) {
                return false; // leave it; it is only wasted space
            }
        });
        return dropped[0];
    }

    /**
     * Drops the 0.8.0-pre6 mark-set column families the caller names. Only names in {@code names} that also match
     * {@code marks_<at least 10 digits>} are dropped; the caller confirms they are GC leftovers, since pre6 wrote no
     * ownership information. Handles of dropped column families are closed and removed from {@code handles}.
     *
     * @param db      the database, with no GC running on it
     * @param handles the handles returned by {@code RocksDB.open}
     * @param names   the column family names to drop
     * @return the number of column families dropped
     * @throws IllegalArgumentException if a name is not a 0.8.0-pre6 mark-set name
     */
    public static int dropLegacy(RocksDB db, List<ColumnFamilyHandle> handles, Collection<String> names) {
        for (String n : names) {
            if (!LEGACY_NAME.matcher(n).matches()) {
                throw new IllegalArgumentException("Not a 0.8.0-pre6 GC mark-set column family name: " + n);
            }
        }
        int[] dropped = {0};
        handles.removeIf(h -> {
            try {
                String name = new String(h.getName(), StandardCharsets.UTF_8);
                if (!names.contains(name)) return false;
                drop(db, h);
                dropped[0]++;
                return true;
            } catch (RocksDBException e) {
                throw new IllegalStateException("Failed to drop column family", e);
            }
        });
        return dropped[0];
    }

    private static void drop(RocksDB db, ColumnFamilyHandle h) throws RocksDBException {
        String name = new String(h.getName(), StandardCharsets.UTF_8);
        db.dropColumnFamily(h);
        h.close();
        log.info("Dropped leftover GC mark-set column family {}", name);
    }

    /** Holds the ownership marker, or nothing at all (crashed between creating the column family and marking it). */
    private static boolean ownedOrEmpty(RocksDB db, ColumnFamilyHandle h) throws RocksDBException {
        if (java.util.Arrays.equals(OWNER_VALUE, db.get(h, OWNER_KEY))) return true;
        try (org.rocksdb.RocksIterator it = db.newIterator(h)) {
            it.seekToFirst();
            return !it.isValid();
        }
    }
}

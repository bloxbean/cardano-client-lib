package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.mpf.internal.NodeRefParser;
import com.bloxbean.cardano.vds.mpf.rdbms.gc.RdbmsGcOptions;
import com.bloxbean.cardano.vds.mpf.rdbms.gc.RdbmsGcReport;
import com.bloxbean.cardano.vds.rdbms.common.KeyCodec;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Mark-sweep garbage collection for the RDBMS MPF backend.
 *
 * <p>This GC strategy works in two phases:</p>
 * <ol>
 *   <li><b>Mark:</b> BFS from retained roots, collecting all reachable node hashes in memory</li>
 *   <li><b>Sweep:</b> Delete all nodes not in the reachable set</li>
 * </ol>
 *
 * <p>This is the RDBMS equivalent of {@code OnDiskMarkSweepStrategy} for RocksDB.
 * The reachable set is held in memory as a {@code Set<ByteBuffer>}.</p>
 *
 * @since 0.8.0
 */
class RdbmsMarkSweepGc {

    private final RdbmsNodeStore nodeStore;
    private final RdbmsRootsIndex rootsIndex;

    RdbmsMarkSweepGc(RdbmsNodeStore nodeStore, RdbmsRootsIndex rootsIndex) {
        this.nodeStore = nodeStore;
        this.rootsIndex = rootsIndex;
    }

    /**
     * Runs mark-sweep GC, keeping only nodes reachable from the given roots.
     *
     * @param roots   the root hashes to keep (all reachable nodes are preserved)
     * @param options GC options (dry run, batch size, progress callback)
     * @return a report with GC statistics
     */
    RdbmsGcReport run(Collection<byte[]> roots, RdbmsGcOptions options) {
        long start = System.currentTimeMillis();

        // Phase 1: Mark — BFS from roots to find all reachable nodes
        Set<ByteBuffer> reachable = mark(roots);

        // Phase 2: Sweep — delete unreachable nodes
        long[] sweepResult = sweep(reachable, options);

        RdbmsGcReport report = new RdbmsGcReport();
        report.marked = reachable.size();
        report.total = sweepResult[0];
        report.deleted = sweepResult[1];
        report.durationMillis = System.currentTimeMillis() - start;
        return report;
    }

    /**
     * BFS traversal from roots, collecting all reachable node hashes.
     */
    private Set<ByteBuffer> mark(Collection<byte[]> roots) {
        Set<ByteBuffer> reachable = new HashSet<>();
        Deque<byte[]> queue = new ArrayDeque<>();

        for (byte[] root : roots) {
            if (root != null && root.length == 32) {
                queue.add(root);
            }
        }

        while (!queue.isEmpty()) {
            byte[] hash = queue.removeFirst();
            ByteBuffer key = ByteBuffer.wrap(hash);
            if (!reachable.add(key)) continue; // already visited

            byte[] encoded = nodeStore.get(hash);
            if (encoded == null) continue;

            for (byte[] childHash : NodeRefParser.childRefs(encoded)) {
                queue.addLast(childHash);
            }
        }
        return reachable;
    }

    /**
     * Deletes all nodes not in the reachable set.
     *
     * <p>Scans the entire nodes table for this namespace and deletes
     * unreachable nodes in batches.</p>
     *
     * @return array of [total_nodes, deleted_nodes]
     */
    private long[] sweep(Set<ByteBuffer> reachable, RdbmsGcOptions options) {
        DataSource ds = nodeStore.dataSource();
        byte ns = nodeStore.keyPrefix();
        String nodesTable = nodeStore.schema().nodesTable();
        KeyCodec codec = nodeStore.keyCodec();

        long total = 0;
        long deleted = 0;

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            // Scan all node hashes for this namespace
            String selectSql = "SELECT node_hash FROM " + nodesTable + " WHERE namespace = ?";
            List<byte[]> toDelete = new ArrayList<>();

            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setInt(1, ns & 0xFF);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    while (rs.next()) {
                        total++;
                        byte[] hash = codec.getKey(rs, "node_hash");
                        ByteBuffer key = ByteBuffer.wrap(hash);
                        if (!reachable.contains(key)) {
                            toDelete.add(hash);
                        }
                    }
                }
            }

            // Delete unreachable nodes in batches
            if (!options.dryRun && !toDelete.isEmpty()) {
                String deleteSql = "DELETE FROM " + nodesTable +
                                   " WHERE namespace = ? AND node_hash = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    int batchCount = 0;
                    for (byte[] hash : toDelete) {
                        deleteStmt.setInt(1, ns & 0xFF);
                        codec.setKey(deleteStmt, 2, hash);
                        deleteStmt.addBatch();
                        batchCount++;
                        deleted++;

                        if (batchCount % options.deleteBatchSize == 0) {
                            deleteStmt.executeBatch();
                            conn.commit();
                            if (options.progress != null) {
                                options.progress.accept(deleted);
                            }
                        }
                    }
                    // Execute remaining deletes
                    if (batchCount % options.deleteBatchSize != 0) {
                        deleteStmt.executeBatch();
                        conn.commit();
                    }
                }
            } else {
                deleted = toDelete.size();
                conn.commit();
            }
        } catch (SQLException e) {
            throw new RuntimeException("GC sweep failed", e);
        }

        return new long[]{total, deleted};
    }
}

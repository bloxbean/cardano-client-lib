package com.bloxbean.cardano.vds.mpf.rocksdb.gc;

import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbRootsIndex;

/**
 * Manager for garbage collection operations on RocksDB-backed Merkle Patricia Tries.
 *
 * <p>The GcManager orchestrates the execution of various garbage collection strategies
 * to reclaim storage space from unreferenced trie nodes. It provides both synchronous
 * and asynchronous execution modes:</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Pluggable GC strategies (reference counting, mark-sweep, etc.)</li>
 *   <li>Configurable retention policies for historical state preservation</li>
 *   <li>Synchronous and asynchronous execution modes</li>
 *   <li>Comprehensive reporting of GC results</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * GcManager gcManager = new GcManager(nodeStore, rootsIndex);
 *
 * // Define retention policy (e.g., keep last 100 versions)
 * RetentionPolicy policy = RetentionPolicy.keepLastVersions(100);
 *
 * // Configure GC options
 * GcOptions options = GcOptions.builder()
 *     .setBatchSize(1000)
 *     .setReportProgress(true)
 *     .build();
 *
 * // Run synchronous GC
 * GcReport report = gcManager.runSync(new RefcountGcStrategy(), policy, options);
 * System.out.println("Reclaimed " + report.getDeletedNodeCount() + " nodes");
 *
 * // Or run asynchronously
 * gcManager.runAsync(strategy, policy, options, report -> {
 *     System.out.println("GC completed: " + report);
 * });
 * }</pre>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Multiple GC operations
 * can be executed concurrently, though individual strategies may have their
 * own concurrency characteristics.</p>
 *
 * @see GcStrategy
 * @see RetentionPolicy
 * @see GcOptions
 * @since 0.8.0
 */
public final class GcManager {
    private final RocksDbNodeStore nodeStore;
    private final RocksDbRootsIndex rootsIndex;

    /**
     * Creates a new GcManager for the specified storage components.
     *
     * @param nodeStore  the node storage to perform GC on
     * @param rootsIndex the roots index for determining reachable nodes
     */
    public GcManager(RocksDbNodeStore nodeStore, RocksDbRootsIndex rootsIndex) {
        this.nodeStore = nodeStore;
        this.rootsIndex = rootsIndex;
    }

    /**
     * Executes garbage collection synchronously using the specified strategy.
     *
     * <p>This method blocks until the GC operation completes and returns
     * a detailed report of the work performed.</p>
     *
     * @param strategy        the GC strategy to use
     * @param retentionPolicy the policy determining which roots to preserve
     * @param gcOptions       configuration options for the GC operation
     * @return a detailed report of the GC operation results
     * @throws Exception if the GC operation fails
     */
    public GcReport runSync(GcStrategy strategy, RetentionPolicy retentionPolicy, GcOptions gcOptions) throws Exception {
        return strategy.run(nodeStore, rootsIndex, retentionPolicy, gcOptions);
    }

    /**
     * Executes garbage collection asynchronously using the specified strategy.
     *
     * <p>This method immediately returns and executes the GC operation on a
     * background daemon thread. The completion callback is invoked when the
     * operation finishes (successfully or with an exception).</p>
     *
     * @param strategy           the GC strategy to use
     * @param retentionPolicy    the policy determining which roots to preserve
     * @param gcOptions          configuration options for the GC operation
     * @param completionCallback callback invoked when GC completes (may be null)
     */
    public void runAsync(GcStrategy strategy, RetentionPolicy retentionPolicy, GcOptions gcOptions,
                         java.util.function.Consumer<GcReport> completionCallback) {
        Thread gcThread = new Thread(() -> {
            GcReport report;
            try {
                report = strategy.run(nodeStore, rootsIndex, retentionPolicy, gcOptions);
            } catch (Exception e) {
                throw new RuntimeException("Garbage collection failed", e);
            }
            if (completionCallback != null) completionCallback.accept(report);
        }, "rocksdb-mpt-gc");
        gcThread.setDaemon(true);
        gcThread.start();
    }
}


package com.bloxbean.cardano.vds.mpf.rocksdb.gc;

import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbRootsIndex;

public interface GcStrategy {
    /**
     * Runs one garbage collection over the store's namespace.
     *
     * @param store   the node store whose namespace is collected
     * @param index   the namespace's roots index; {@code null} only where the strategy documents it
     *                ({@code OnDiskMarkSweepStrategy}, where the policy's roots are then the only roots kept). The
     *                built-in {@link RetentionPolicy} factories read the index, so they need one.
     * @param policy  the roots to keep
     * @param options run options
     * @return what the run marked and deleted
     * @throws Exception if the run fails
     */
    GcReport run(RocksDbNodeStore store, RocksDbRootsIndex index, RetentionPolicy policy, GcOptions options) throws Exception;
}


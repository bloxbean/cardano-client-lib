package com.bloxbean.cardano.vds.mpf.rocksdb.gc;

import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbRootsIndex;

public interface GcStrategy {
    GcReport run(RocksDbNodeStore store, RocksDbRootsIndex index, RetentionPolicy policy, GcOptions options) throws Exception;
}


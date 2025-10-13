package com.bloxbean.cardano.vds.mpt.rocksdb.gc;

import com.bloxbean.cardano.vds.mpt.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.vds.mpt.rocksdb.RocksDbRootsIndex;

public interface GcStrategy {
    GcReport run(RocksDbNodeStore store, RocksDbRootsIndex index, RetentionPolicy policy, GcOptions options) throws Exception;
}


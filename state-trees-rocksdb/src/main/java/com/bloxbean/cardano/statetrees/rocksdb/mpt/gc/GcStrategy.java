package com.bloxbean.cardano.statetrees.rocksdb.mpt.gc;

import com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbNodeStore;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbRootsIndex;

public interface GcStrategy {
    GcReport run(RocksDbNodeStore store, RocksDbRootsIndex index, RetentionPolicy policy, GcOptions options) throws Exception;
}


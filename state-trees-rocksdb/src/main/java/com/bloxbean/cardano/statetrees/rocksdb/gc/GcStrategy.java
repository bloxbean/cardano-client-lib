package com.bloxbean.cardano.statetrees.rocksdb.gc;

import com.bloxbean.cardano.statetrees.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.statetrees.rocksdb.RocksDbRootsIndex;

public interface GcStrategy {
    GcReport run(RocksDbNodeStore store, RocksDbRootsIndex index, RetentionPolicy policy, GcOptions options) throws Exception;
}


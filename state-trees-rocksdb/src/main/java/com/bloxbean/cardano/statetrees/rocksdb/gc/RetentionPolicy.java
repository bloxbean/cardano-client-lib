package com.bloxbean.cardano.statetrees.rocksdb.gc;

import com.bloxbean.cardano.statetrees.rocksdb.RocksDbRootsIndex;

import java.util.Collection;
import java.util.NavigableMap;

public interface RetentionPolicy {
    Collection<byte[]> resolveRoots(RocksDbRootsIndex index);

    static RetentionPolicy keepLatestN(int n) {
        return index -> {
            NavigableMap<Long, byte[]> all = index.listAll();
            java.util.List<byte[]> out = new java.util.ArrayList<>();
            int kept = 0;
            for (var e : all.descendingMap().entrySet()) {
                out.add(e.getValue());
                if (++kept >= n) break;
            }
            return out;
        };
    }

    static RetentionPolicy keepVersions(java.util.Collection<Long> versions) {
        return index -> {
            java.util.List<byte[]> out = new java.util.ArrayList<>();
            for (Long v : versions) {
                byte[] r = index.get(v);
                if (r != null) out.add(r);
            }
            return out;
        };
    }

    static RetentionPolicy keepRange(long fromInclusive, long toInclusive) {
        return index -> new java.util.ArrayList<>(index.listRange(fromInclusive, toInclusive).values());
    }
}


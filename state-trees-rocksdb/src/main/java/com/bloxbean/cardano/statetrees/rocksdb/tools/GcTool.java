package com.bloxbean.cardano.statetrees.rocksdb.tools;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.rocksdb.RocksDbStateTrees;
import com.bloxbean.cardano.statetrees.rocksdb.gc.*;
import org.rocksdb.*;

import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Random;

/**
 * Lightweight CLI for generating data and running GC strategies against a RocksDB MPT store.
 * Not a polished CLI; intended for local experiments.
 * <p>
 * Usage examples:
 * java ... GcTool generate /tmp/mpt 10000 5
 * java ... GcTool stats /tmp/mpt
 * java ... GcTool gc-refcount /tmp/mpt keep-latest 1
 * java ... GcTool gc-marksweep /tmp/mpt keep-latest 1
 */
public class GcTool {
    private static final HashFunction HF = Blake2b256::digest;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: GcTool <cmd> <dbPath> [...]");
            return;
        }
        String cmd = args[0];
        String dbPath = args[1];

        switch (cmd) {
            case "generate": {
                int numKeys = Integer.parseInt(args[2]);
                int numVersions = Integer.parseInt(args[3]);
                generate(dbPath, numKeys, numVersions);
                break;
            }
            case "stats": {
                stats(dbPath);
                break;
            }
            case "gc-refcount": {
                int latest = Integer.parseInt(args[3]);
                runGc(dbPath, new RefcountGcStrategy(), RetentionPolicy.keepLatestN(latest));
                break;
            }
            case "gc-marksweep": {
                int latest = Integer.parseInt(args[3]);
                runGc(dbPath, new OnDiskMarkSweepStrategy(), RetentionPolicy.keepLatestN(latest));
                break;
            }
            default:
                System.out.println("Unknown cmd: " + cmd);
        }
    }

    static void generate(String dbPath, int numKeys, int numVersions) throws Exception {
        RocksDB.loadLibrary();
        try (RocksDbStateTrees st = new RocksDbStateTrees(dbPath)) {
            MerklePatriciaTrie trie = new MerklePatriciaTrie(st.nodeStore(), HF);
            Random rnd = new SecureRandom();

            // Use nodes CF for refcounts (stored with a prefix); avoid creating a separate CF
            ColumnFamilyHandle refsCF = st.nodeStore().nodesHandle();

            byte[][] keys = new byte[numKeys][];
            for (int i = 0; i < numKeys; i++) {
                byte[] k = new byte[16];
                rnd.nextBytes(k);
                keys[i] = k;
            }

            for (int v = 0; v < numVersions; v++) {
                try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
                    st.nodeStore().withBatch(wb, () -> {
                        // Update ~10% of keys per version
                        int updates = Math.max(1, numKeys / 10);
                        for (int i = 0; i < updates; i++) {
                            int idx = rnd.nextInt(numKeys);
                            byte[] value = new byte[16];
                            rnd.nextBytes(value);
                            trie.put(keys[idx], value);
                        }
                        st.rootsIndex().withBatch(wb, () -> {
                            long ver = st.rootsIndex().nextVersion();
                            st.rootsIndex().put(ver, trie.getRootHash());
                            return null;
                        });
                        // Increment refcounts for the new root (refs stored in nodes CF)
                        RefcountGcStrategy.incrementAll(st.db(), st.nodeStore().nodesHandle(), refsCF, trie.getRootHash(), wb);
                        return null;
                    });
                    st.db().write(wo, wb);
                }
            }

            System.out.println("Generated versions: " + st.rootsIndex().lastVersion());
        }
        // Print stats after closing the generator handle to avoid RocksDB lock conflicts
        stats(dbPath);
    }

    static void runGc(String dbPath, GcStrategy strategy, RetentionPolicy policy) throws Exception {
        RocksDB.loadLibrary();
        try (RocksDbStateTrees st = new RocksDbStateTrees(dbPath)) {
            GcManager manager = new GcManager(st.nodeStore(), st.rootsIndex());
            GcOptions opts = new GcOptions();
            opts.deleteBatchSize = 20_000;
            opts.progress = n -> System.out.println("deleted=" + n);
            long before = countNodes(st);
            GcReport r = manager.runSync(strategy, policy, opts);
            long after = countNodes(st);
            System.out.println("GC report: marked=" + r.marked + " deleted=" + r.deleted + " total=" + r.total + " ms=" + r.durationMillis);
            System.out.println("Nodes before=" + before + " after=" + after);
        }
    }

    static void stats(String dbPath) throws Exception {
        RocksDB.loadLibrary();
        try (RocksDbStateTrees st = new RocksDbStateTrees(dbPath)) {
            long nodes = countNodes(st);
            long versions = st.rootsIndex().listAll().size();
            System.out.println("nodes=" + nodes + " versions=" + versions + " latestRoot=" + HexUtil.encodeHexString(st.rootsIndex().latest()));
        }
    }

    static long countNodes(RocksDbStateTrees st) {
        long cnt = 0;
        try (ReadOptions ro = new ReadOptions(); RocksIterator it = st.db().newIterator(st.nodeStore().nodesHandle(), ro)) {
            for (it.seekToFirst(); it.isValid(); it.next()) cnt++;
        }
        return cnt;
    }
}

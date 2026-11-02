package com.bloxbean.cardano.vds.tools.mpf;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbMptSession;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbStateTrees;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.*;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.*;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import org.rocksdb.*;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Lightweight CLI for generating data and running GC strategies against a RocksDB MPT store.
 * Not a polished CLI; intended for local experiments.
 *
 * <p>Usage examples:</p>
 * <pre>
 * # Via Gradle
 * ./gradlew :verified-structures:load-tools:run --args="gc generate /tmp/mpt 10000 5"
 * ./gradlew :verified-structures:load-tools:run --args="gc stats /tmp/mpt"
 * ./gradlew :verified-structures:load-tools:run --args="gc gc-refcount /tmp/mpt keep-latest 1"
 * ./gradlew :verified-structures:load-tools:run --args="gc gc-marksweep /tmp/mpt keep-latest 1"
 *
 * # Via fat JAR (build first: ./gradlew :verified-structures:load-tools:shadowJar)
 * java -jar cardano-client-vds-load-tools-VERSION-all.jar gc generate /tmp/mpt 10000 5
 * java -jar cardano-client-vds-load-tools-VERSION-all.jar gc stats /tmp/mpt
 * </pre>
 */
public class GcTool {
    private static final HashFunction HF = Blake2b256::digest;

    private static final String USAGE = String.join("\n",
            "Usage: gc <command> <dbPath> [args]",
            "  generate <dbPath> <keyPoolSize> <versions>   write versions (each updates ~10% of a random key pool)",
            "  stats <dbPath>                               node count, version count, latest root",
            "  gc-refcount <dbPath> keep-latest <n>         refcount GC keeping the latest n versions",
            "  gc-marksweep <dbPath> keep-latest <n>        mark-sweep GC keeping the latest n versions");

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || "--help".equals(args[0]) || "-h".equals(args[0]) || "help".equals(args[0])) {
            System.out.println(USAGE);
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
                System.out.println("Unknown command: " + cmd);
                System.out.println(USAGE);
        }
    }

    static void generate(String dbPath, int numKeys, int numVersions) throws Exception {
        RocksDB.loadLibrary();
        try (RocksDbStateTrees st = new RocksDbStateTrees(dbPath)) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            Random rnd = new SecureRandom();

            byte[][] keys = new byte[numKeys][];
            for (int i = 0; i < numKeys; i++) {
                byte[] k = new byte[16];
                rnd.nextBytes(k);
                keys[i] = k;
            }

            RocksDbMptSession session = RocksDbMptSession.of(st);
            for (int v = 0; v < numVersions; v++) {
                // Commit the trie nodes and record the root with its refcounts in one batch. The refcounts
                // see the staged nodes because putRootWithRefcount runs inside the session batch.
                session.write(() -> {
                    // Update ~10% of keys per version
                    int updates = Math.max(1, numKeys / 10);
                    for (int i = 0; i < updates; i++) {
                        int idx = rnd.nextInt(numKeys);
                        byte[] value = new byte[16];
                        rnd.nextBytes(value);
                        trie.put(keys[idx], value);
                    }
                    st.putRootWithRefcount(trie.getRootHash());
                });
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
            byte[] latest = st.rootsIndex().latest();
            System.out.println("nodes=" + nodes + " versions=" + versions + " latestRoot="
                    + (latest == null ? "none" : HexUtil.encodeHexString(latest)));
        }
    }

    static long countNodes(RocksDbStateTrees st) {
        long cnt = 0;
        // Count node keys of this namespace only (prefix byte + 32-byte hash); refcount entries are skipped.
        KeyPrefixer prefixer = st.nodeStore().keyPrefixer();
        try (ReadOptions ro = prefixer.createNamespaceScanReadOptions(); RocksIterator it = st.db().newIterator(st.nodeStore().nodesHandle(), ro)) {
            for (it.seek(prefixer.namespaceStart()); it.isValid(); it.next()) {
                if (!prefixer.hasCorrectPrefix(it.key())) break;
                if (it.key().length == 33) cnt++;
            }
        }
        return cnt;
    }
}

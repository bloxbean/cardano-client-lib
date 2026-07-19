package com.bloxbean.cardano.vds.jmt.rocksdb;

import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Child-JVM entry point used by {@link RocksDbJmtCrashRecoveryTest}. */
public final class RocksDbJmtCrashWorker {

    private RocksDbJmtCrashWorker() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected database path and scenario");
        }
        RocksDbJmtStore.Options options = RocksDbJmtStore.Options.production();
        try (RocksDbJmtStore store = RocksDbJmtStore.open(args[0], options)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            switch (args[1]) {
                case "staged-abort":
                    stagedAbort(store, Long.parseLong(args[2]));
                    return;
                case "after-commit":
                    put(tree, Long.parseLong(args[2]));
                    halt();
                    return;
                case "repeated-commits":
                    repeatedCommits(tree, Long.parseLong(args[2]), Long.parseLong(args[3]));
                    return;
                case "prune-race":
                    haltSoon();
                    store.pruneUpTo(Long.parseLong(args[2]));
                    halt();
                    return;
                case "truncate-race":
                    haltSoon();
                    store.truncateAfter(Long.parseLong(args[2]));
                    halt();
                    return;
                default:
                    throw new IllegalArgumentException("Unknown crash scenario: " + args[1]);
            }
        }
    }

    private static void stagedAbort(RocksDbJmtStore store, long version) {
        byte[] keyHash = Blake2b256.digest(bytes("uncommitted-key"));
        try (JmtStore.CommitBatch batch = store.beginCommit(
                version, JmtStore.CommitConfig.defaults())) {
            batch.putValue(keyHash, bytes("uncommitted-value"));
            batch.setRootHash(Blake2b256.digest(bytes("uncommitted-root")));
            halt();
        }
    }

    private static void repeatedCommits(JellyfishMerkleTree tree, long from, long to) {
        for (long version = from; version <= to; version++) {
            put(tree, version);
            if (version == from + ((to - from) / 2)) {
                halt();
            }
        }
        halt();
    }

    private static void put(JellyfishMerkleTree tree, long version) {
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(bytes("key-" + version), bytes("value-" + version));
        updates.put(bytes("shared-key"), bytes("shared-value-" + version));
        tree.put(version, updates);
    }

    private static void haltSoon() {
        Thread killer = new Thread(() -> {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            halt();
        }, "jmt-crash-injector");
        killer.setDaemon(true);
        killer.start();
    }

    @SuppressWarnings("java:S1147")
    private static void halt() {
        Runtime.getRuntime().halt(0); // Deliberate abrupt process termination for crash testing.
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

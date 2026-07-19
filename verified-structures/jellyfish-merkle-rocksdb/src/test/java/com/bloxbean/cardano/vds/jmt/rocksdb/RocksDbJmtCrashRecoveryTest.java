package com.bloxbean.cardano.vds.jmt.rocksdb;

import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.JmtProof;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityChecker;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityMode;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocksDbJmtCrashRecoveryTest {

    private static final Duration CHILD_TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path tempDir;

    @Test
    void abruptTerminationBeforeAndAfterCommitIsAtomic() throws Exception {
        Path database = tempDir.resolve("commit-crashes");
        seed(database, 0, 4);

        runWorker(database, "staged-abort", "5");
        try (RocksDbJmtStore store = open(database)) {
            assertEquals(4, store.latestRoot().orElseThrow().version());
            assertTrue(store.getValue(JmtProfile.classicBlake2b256V1().hashFunction()
                    .digest(bytes("uncommitted-key"))).isEmpty());
            assertHealthy(store);
        }

        runWorker(database, "after-commit", "5");
        try (RocksDbJmtStore store = open(database)) {
            assertEquals(5, store.latestRoot().orElseThrow().version());
            assertHealthy(store);
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            assertEquals(JmtProof.ProofType.INCLUSION,
                    tree.getProof(bytes("key-5"), 5).orElseThrow().type());
        }
    }

    @Test
    void repeatedCommitCrashLeavesOnlyCompleteVersion() throws Exception {
        Path database = tempDir.resolve("repeated-commits");
        seed(database, 0, 2);

        runWorker(database, "repeated-commits", "3", "12");
        try (RocksDbJmtStore store = open(database)) {
            long latest = store.latestRoot().orElseThrow().version();
            assertTrue(latest >= 3 && latest <= 12);
            assertHealthy(store);
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            assertEquals(JmtProof.ProofType.INCLUSION,
                    tree.getProof(bytes("key-" + latest), latest).orElseThrow().type());
        }
    }

    @Test
    void terminationRacingPruneAndTruncateNeverProducesMixedState() throws Exception {
        Path pruneDatabase = tempDir.resolve("prune-crash");
        seed(pruneDatabase, 0, 40);
        runWorker(pruneDatabase, "prune-race", "30");
        try (RocksDbJmtStore store = open(pruneDatabase)) {
            assertEquals(40, store.latestRoot().orElseThrow().version());
            assertHealthy(store);
        }

        Path truncateDatabase = tempDir.resolve("truncate-crash");
        seed(truncateDatabase, 0, 40);
        runWorker(truncateDatabase, "truncate-race", "25");
        try (RocksDbJmtStore store = open(truncateDatabase)) {
            long latest = store.latestRoot().orElseThrow().version();
            assertTrue(latest == 25 || latest == 40,
                    "truncate must be entirely absent or entirely committed");
            assertHealthy(store);
        }
    }

    @Test
    void productionOptionsRequireWalSyncAndRollbackIndexes() {
        RocksDbJmtStore.Options production = RocksDbJmtStore.Options.production();
        assertTrue(production.enableRollbackIndex());
        assertFalse(production.disableWalForBatches());
        assertTrue(production.syncOnCommit());
        assertTrue(production.syncOnPrune());
        assertTrue(production.syncOnTruncate());
        assertTrue(production.isProductionDurable());

        RocksDbJmtStore.Options benchmarkOnly = RocksDbJmtStore.Options.builder()
                .disableWalForBatches(true)
                .syncOnCommit(false)
                .build();
        assertFalse(benchmarkOnly.isProductionDurable());
    }

    private void seed(Path database, long firstVersion, long lastVersion) {
        try (RocksDbJmtStore store = open(database)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            for (long version = firstVersion; version <= lastVersion; version++) {
                Map<byte[], byte[]> updates = new LinkedHashMap<>();
                updates.put(bytes("seed-key-" + version), bytes("seed-value-" + version));
                updates.put(bytes("shared-key"), bytes("seed-shared-" + version));
                tree.put(version, updates);
            }
        }
    }

    private RocksDbJmtStore open(Path database) {
        return RocksDbJmtStore.open(database.toString(), RocksDbJmtStore.Options.production());
    }

    private void assertHealthy(RocksDbJmtStore store) {
        JmtIntegrityReport report = new JmtIntegrityChecker(
                store, JmtProfile.classicBlake2b256V1()).check(JmtIntegrityMode.FULL);
        assertTrue(report.healthy(), () -> "Integrity issues after crash: " + report.issues());
    }

    private void runWorker(Path database, String... arguments) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String[] command = new String[5 + arguments.length];
        command[0] = java;
        command[1] = "-cp";
        command[2] = System.getProperty("java.class.path");
        command[3] = RocksDbJmtCrashWorker.class.getName();
        command[4] = database.toString();
        System.arraycopy(arguments, 0, command, 5, arguments.length);

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean exited = process.waitFor(CHILD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!exited) {
            process.destroyForcibly();
            throw new AssertionError("Crash worker timed out");
        }
        byte[] output = readOutput(process);
        assertEquals(0, process.exitValue(), () -> "Crash worker failed: "
                + new String(output, StandardCharsets.UTF_8));
    }

    private static byte[] readOutput(Process process) throws IOException {
        return process.getInputStream().readAllBytes();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

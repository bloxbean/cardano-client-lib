package com.bloxbean.cardano.vds.tools.jmt;

import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmtIntegrityCliTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsSuccessForHealthyProductionStore() {
        Path dbPath = tempDir.resolve("healthy");
        try (RocksDbJmtStore store = RocksDbJmtStore.open(
                dbPath.toString(), RocksDbJmtStore.Options.production())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            tree.put(1, Map.of(bytes("alice"), bytes("100")));
        }

        CapturedOutput output = run("--rocksdb=" + dbPath, "--mode=full", "--all-versions");

        assertEquals(0, output.exitCode);
        assertTrue(output.standardOut.contains("JMT integrity: HEALTHY"));
        assertEquals("", output.standardError);
    }

    @Test
    void failsWithoutCreatingANewDatabase() {
        Path missing = tempDir.resolve("missing");

        CapturedOutput output = run("--rocksdb=" + missing);

        assertEquals(1, output.exitCode);
        assertTrue(output.standardError.contains("Not an existing RocksDB database"));
        assertTrue(java.nio.file.Files.notExists(missing));
    }

    @Test
    void rejectsConflictingVersionSelectors() {
        CapturedOutput output = run("--rocksdb=x", "--all-versions", "--from-version=1");

        assertEquals(2, output.exitCode);
        assertTrue(output.standardError.contains("cannot be combined"));
    }

    private CapturedOutput run(String... args) {
        ByteArrayOutputStream standardOut = new ByteArrayOutputStream();
        ByteArrayOutputStream standardError = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream out = new PrintStream(standardOut, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(standardError, true, StandardCharsets.UTF_8)) {
            exitCode = JmtIntegrityCli.run(args, out, err);
        }
        return new CapturedOutput(exitCode,
                standardOut.toString(StandardCharsets.UTF_8),
                standardError.toString(StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class CapturedOutput {
        private final int exitCode;
        private final String standardOut;
        private final String standardError;

        private CapturedOutput(int exitCode, String standardOut, String standardError) {
            this.exitCode = exitCode;
            this.standardOut = standardOut;
            this.standardError = standardError;
        }
    }
}

package com.bloxbean.cardano.vds.jmt.rocksdb;

import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Coverage-guided differential operation traces across in-memory and RocksDB stores. */
class RocksDbJmtDifferentialFuzzTest {

    private static final byte[][] KEYS = {
            new byte[]{0x00},
            new byte[]{0x01},
            new byte[]{0x10},
            new byte[]{0x20},
            new byte[]{(byte) 0xFF}
    };

    @FuzzTest(maxDuration = "10s")
    void operationTraceProducesIdenticalRootsAndProofs(byte[] trace) throws IOException {
        Path database = Files.createTempDirectory("jmt-differential-fuzz-");
        try (InMemoryJmtStore memory = new InMemoryJmtStore();
             RocksDbJmtStore rocks = RocksDbJmtStore.open(
                     database.toString(), RocksDbJmtStore.Options.production())) {
            JellyfishMerkleTree memoryTree = new JellyfishMerkleTree(memory);
            JellyfishMerkleTree rocksTree = new JellyfishMerkleTree(rocks);
            int operations = Math.min(trace.length / 3, 16);
            for (int index = 0; index < operations; index++) {
                byte[] key = KEYS[(trace[index * 3] & 0xFF) % KEYS.length];
                byte[] value = new byte[]{trace[index * 3 + 1], trace[index * 3 + 2]};
                JellyfishMerkleTree.CommitResult memoryResult = memoryTree.put(
                        index, Map.of(key, value));
                JellyfishMerkleTree.CommitResult rocksResult = rocksTree.put(
                        index, Map.of(key, value));
                assertArrayEquals(memoryResult.rootHash(), rocksResult.rootHash());
                assertEquals(memoryTree.getProof(key, index).orElseThrow().type(),
                        rocksTree.getProof(key, index).orElseThrow().type());
                assertArrayEquals(memoryTree.getProofWire(key, index).orElseThrow(),
                        rocksTree.getProofWire(key, index).orElseThrow());
            }
        } finally {
            deleteRecursively(database);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            Path[] ordered = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for (Path path : ordered) {
                Files.deleteIfExists(path);
            }
        }
    }
}

package com.bloxbean.cardano.client.tools.chainparse;

import com.bloxbean.cardano.client.transaction.raw.RawBlock;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChainParseCheckTest {
    @TempDir
    Path dir;

    @Test
    void resumesOnlyWithTheSameIdentity() throws Exception {
        Map<String, String> identity = Map.of("network", "preprod", "cclVersion", "a", "cclJarsSha256", "01");
        State state = new State();
        state.identity = identity;
        state.processedBlocks = 1000;
        Path file = dir.resolve("state.json");
        state.save(file);

        assertEquals(1000, State.resume(file, identity).processedBlocks);
        assertThrows(IllegalStateException.class, () -> State.resume(file, Map.of("network", "preprod",
                "cclVersion", "a", "cclJarsSha256", "02")));

        state.identity = null; // a state saved before identities
        state.save(file);
        assertThrows(IllegalStateException.class, () -> State.resume(file, identity));
    }

    @Test
    void cclJarsHashCoversTheirContents() throws Exception {
        Path ccl = Files.write(dir.resolve("cardano-client-plutus-1.jar"), new byte[]{1});
        Path other = Files.write(dir.resolve("yaci-core-1.jar"), new byte[]{1});
        String classPath = ccl + File.pathSeparator + other;
        String before = ChainParseCheck.cclJarsSha256(classPath);

        Files.write(other, new byte[]{2});
        assertEquals(before, ChainParseCheck.cclJarsSha256(classPath));
        Files.write(ccl, new byte[]{2}); // a republished SNAPSHOT
        assertNotEquals(before, ChainParseCheck.cclJarsSha256(classPath));
    }

    @Test
    void maxBlocksLimitsTheBatches() throws Exception {
        assertEquals(1, dispatch(300, 1));
        assertEquals(257, dispatch(300, 257)); // one full batch of 256, then 1
    }

    // Runs the dispatcher over `queued` empty Conway blocks (slots 1, 2, ...) with a later tip; the processed block count.
    private long dispatch(int queued, long maxBlocks) throws Exception {
        Path run = Files.createTempDirectory(dir, "run");
        State state = new State();
        state.identity = Map.of();
        Issues issues = new Issues(state, run.resolve("issues.jsonl"), run.resolve("failed-blocks"));
        ThreadLocal<Checker> checkers = ThreadLocal.withInitial(() -> new Checker(state, issues, null, slot -> 0));
        BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(512);
        String bodyHash = HexUtil.encodeHexString(RawBlock.of(block(1, "00".repeat(32))).bodyHash());
        for (int i = 1; i <= queued; i++)
            queue.add(block(i, bodyHash));
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            String outcome = ChainParseCheck.dispatch(state, queue, new Point(9999, "00"), Long.MAX_VALUE, maxBlocks,
                    checkers, pool, new AtomicLong(), run);
            assertEquals("stopped at --max-blocks", outcome);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(Map.of(), state.issueCounts);
        assertEquals(state.processedBlocks, state.lastBlockNo);
        assertEquals(state.processedBlocks, State.resume(run.resolve("state.json"), Map.of()).processedBlocks);
        return state.processedBlocks;
    }

    // A Conway block (era 7) numbered n at slot n: a Praos header body of 10 items, empty body and signature.
    private static byte[] block(int n, String bodyHash) {
        return HexUtil.decodeHexString("820785828a" + String.format("19%04x19%04x", n, n) + "0000000000"
                + "5820" + bodyHash + "0000408080a080");
    }
}

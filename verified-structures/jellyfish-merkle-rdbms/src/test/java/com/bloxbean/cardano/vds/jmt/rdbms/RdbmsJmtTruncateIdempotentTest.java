package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RDBMS rollback ({@code truncateAfter}) and idempotent commit replay.
 */
class RdbmsJmtTruncateIdempotentTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    private DbConfig dbConfig;
    private RdbmsJmtStore store;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:test_jmt_trunc_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        dbConfig = DbConfig.builder().simpleJdbcUrl(jdbcUrl).build();
        try (Connection conn = dbConfig.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            String schema = new String(
                    getClass().getResourceAsStream("/ddl/jmt/h2/schema.sql").readAllBytes(),
                    StandardCharsets.UTF_8);
            stmt.execute(schema);
        }
        store = new RdbmsJmtStore(dbConfig);
    }

    @AfterEach
    void tearDown() {
        if (store != null) store.close();
        if (dbConfig != null) dbConfig.close();
    }

    private static byte[] b(String v) {
        return v.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void truncateAfterRemovesFutureVersionsAndRepointsLatest() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);
        byte[] root2 = null;
        for (long v = 1; v <= 5; v++) {
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(b("key-" + v), b("val-" + v));
            JellyfishMerkleTree.CommitResult r = tree.put(v, updates);
            if (v == 2) root2 = r.rootHash();
        }

        store.truncateAfter(2);

        for (long v = 3; v <= 5; v++) {
            assertTrue(store.rootHash(v).isEmpty(), "root " + v + " should be gone");
            assertTrue(store.getValue(HASH.digest(b("key-" + v))).isEmpty(), "value key-" + v + " should be gone");
        }
        assertArrayEquals(root2, store.rootHash(2).orElseThrow());
        assertArrayEquals(root2, store.latestRoot().orElseThrow().rootHash());
        assertEquals(2L, store.latestRoot().orElseThrow().version());
        assertArrayEquals(b("val-1"), store.getValue(HASH.digest(b("key-1"))).orElseThrow());
        assertArrayEquals(b("val-2"), store.getValue(HASH.digest(b("key-2"))).orElseThrow());

        // Tree remains usable after rollback.
        Map<byte[], byte[]> resume = new LinkedHashMap<>();
        resume.put(b("key-3b"), b("val-3b"));
        JellyfishMerkleTree.CommitResult r3 = tree.put(3, resume);
        assertArrayEquals(r3.rootHash(), store.rootHash(3).orElseThrow());
    }

    @Test
    void replayingSameVersionCommitIsIdempotent() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(b("alice"), b("100"));
        updates.put(b("bob"), b("200"));

        byte[] root1 = tree.put(1L, updates).rootHash();

        // Replay the identical commit at the same version — must not throw on PK conflicts.
        byte[] root1Again = assertDoesNotThrow(() -> tree.put(1L, updates).rootHash());
        assertArrayEquals(root1, root1Again, "replayed commit must produce the same root");

        assertArrayEquals(b("100"), store.getValue(HASH.digest(b("alice"))).orElseThrow());
        assertArrayEquals(b("200"), store.getValue(HASH.digest(b("bob"))).orElseThrow());
    }

    @Test
    void divergentReplayOfSameVersionIsRejected() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);
        Map<byte[], byte[]> a = new LinkedHashMap<>();
        a.put(b("alice"), b("100"));
        byte[] root1 = tree.put(1L, a).rootHash();

        // Re-commit version 1 with DIFFERENT content → different root → must be rejected loudly,
        // and the prior committed state must remain intact (transaction rolled back).
        Map<byte[], byte[]> b2 = new LinkedHashMap<>();
        b2.put(b("alice"), b("999"));
        assertThrows(RuntimeException.class, () -> tree.put(1L, b2));

        assertArrayEquals(root1, store.rootHash(1L).orElseThrow(), "original root must be unchanged");
        assertArrayEquals(b("100"), store.getValue(HASH.digest(b("alice"))).orElseThrow(),
                "original value must survive the rejected divergent replay");
    }

    @Test
    void replayingOlderVersionDoesNotRegressLatestPointer() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);
        for (long v = 1; v <= 3; v++) {
            Map<byte[], byte[]> u = new LinkedHashMap<>();
            u.put(b("k-" + v), b("v-" + v));
            tree.put(v, u);
        }
        assertEquals(3L, store.latestRoot().orElseThrow().version());

        // Crash-recovery style replay of an already-committed OLDER version (identical content).
        Map<byte[], byte[]> u1 = new LinkedHashMap<>();
        u1.put(b("k-1"), b("v-1"));
        assertDoesNotThrow(() -> tree.put(1L, u1));

        assertEquals(3L, store.latestRoot().orElseThrow().version(),
                "latest pointer must not regress to the replayed older version");
    }
}

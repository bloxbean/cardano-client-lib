package com.bloxbean.cardano.statetrees.rdbms.mpt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.common.util.Bytes;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;
import com.bloxbean.cardano.statetrees.mpt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.mpt.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.statetrees.mpt.mpf.MpfProofVerifier;
import com.bloxbean.cardano.statetrees.rdbms.common.DbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Property-style randomized tests for MPT in MPF mode backed by RDBMS.
 *
 * The test builds multiple roots by applying random put/delete updates and validates:
 *  - Proofs verify for random keys against each saved root
 *  - Proofs continue to verify after reopening database (persistence)
 *  - At least one multi-level proof exists at the final root
 */
class RdbmsMptPropertyTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new MpfCommitmentScheme(HASH);
    private static final Random RNG = new Random(0xBEEF);

    private DbConfig dbConfig;
    private String jdbcUrl;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        // Use H2 in-memory database for testing
        jdbcUrl = "jdbc:h2:mem:test_mpt_property_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        dbConfig = DbConfig.builder()
            .simpleJdbcUrl(jdbcUrl)
            .build();

        // Create schema
        createSchema(dbConfig);
    }

    @AfterEach
    void tearDown() {
        // H2 in-memory database will be automatically closed
    }

    private void createSchema(DbConfig config) throws Exception {
        try (Connection conn = config.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {

            // Read schema from resources
            String schema = new String(
                getClass().getResourceAsStream("/schema/mpt/h2/V1__mpt_base_schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );

            // H2 can execute the entire script at once
            stmt.execute(schema);
        }
    }

    @Test
    void randomizedProofs_persist_and_multilevel() throws Exception {
        int keyCount = 140;
        int versions = 12;
        int maxUpdatesPerVersion = 10;
        int queries = 50;

        List<byte[]> keyPool = generateKeys(keyCount);
        List<byte[]> roots = new ArrayList<>();

        // Phase 1: build multiple roots with random updates
        try (RdbmsNodeStore store = new RdbmsNodeStore(dbConfig)) {
            SecureTrie trie = new SecureTrie(store, HASH);

            for (int v = 1; v <= versions; v++) {
                Map<byte[], byte[]> updates = randomUpdates(keyPool, maxUpdatesPerVersion);
                applyUpdates(trie, updates);
                roots.add(Objects.requireNonNullElse(trie.getRootHash(), new byte[32]));

                // spot-check some proofs immediately at this root
                assertRandomProofs(trie, roots.get(v - 1), keyPool, Math.min(10, queries));
            }

            // Full check at final root
            byte[] latestRoot = roots.get(roots.size() - 1);
            trie.setRootHash(latestRoot);
            assertRandomProofs(trie, latestRoot, keyPool, queries);
            assertTrue(existsMultiLevelProof(trie, latestRoot, keyPool), "expected a multi-level proof at final root");
        }

        // Phase 2: reopen and verify again at final root
        try (RdbmsNodeStore store = new RdbmsNodeStore(dbConfig)) {
            byte[] latestRoot = roots.get(roots.size() - 1);
            SecureTrie trie = new SecureTrie(store, HASH, latestRoot);
            assertRandomProofs(trie, latestRoot, keyPool, queries);
        }
    }

    @Test
    void randomizedProofs_withDeletes_sqlite() throws Exception {
        File sqliteFile = tempDir.resolve("mpt-property.sqlite").toFile();
        String sqliteUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
        DbConfig sqliteConfig = DbConfig.builder()
            .simpleJdbcUrl(sqliteUrl)
            .build();

        // Create schema for SQLite
        try (Connection conn = sqliteConfig.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            String schema = new String(
                getClass().getResourceAsStream("/schema/mpt/sqlite/V1__mpt_base_schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );
            String[] statements = schema.split(";");
            for (String sql : statements) {
                String cleaned = sql.replaceAll("--[^\n]*", "").trim();
                if (!cleaned.isEmpty()) {
                    stmt.execute(cleaned);
                }
            }
        }

        int keyCount = 100;
        int versions = 10;
        int maxUpdatesPerVersion = 8;
        int queries = 30;

        List<byte[]> keyPool = generateKeys(keyCount);
        List<byte[]> roots = new ArrayList<>();

        try (RdbmsNodeStore store = new RdbmsNodeStore(sqliteConfig)) {
            SecureTrie trie = new SecureTrie(store, HASH);

            for (int v = 1; v <= versions; v++) {
                Map<byte[], byte[]> updates = randomUpdates(keyPool, maxUpdatesPerVersion);
                applyUpdates(trie, updates);
                roots.add(Objects.requireNonNullElse(trie.getRootHash(), new byte[32]));

                // Verify immediately
                assertRandomProofs(trie, roots.get(v - 1), keyPool, Math.min(5, queries));
            }

            // Full check at final root
            byte[] latestRoot = roots.get(roots.size() - 1);
            trie.setRootHash(latestRoot);
            assertRandomProofs(trie, latestRoot, keyPool, queries);
            assertTrue(existsMultiLevelProof(trie, latestRoot, keyPool),
                    "expected a multi-level proof at final root with SQLite backend");
        }
    }

    private static void applyUpdates(SecureTrie trie, Map<byte[], byte[]> updates) {
        for (Map.Entry<byte[], byte[]> e : updates.entrySet()) {
            if (e.getValue() == null) {
                trie.delete(e.getKey());
            } else {
                trie.put(e.getKey(), e.getValue());
            }
        }
    }

    private static List<byte[]> generateKeys(int count) {
        List<byte[]> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String s = "k-" + i + "-" + RNG.nextInt(1_000_000);
            keys.add(s.getBytes(StandardCharsets.UTF_8));
        }
        return keys;
    }

    private static Map<byte[], byte[]> randomUpdates(List<byte[]> keyPool, int maxUpdates) {
        int n = 1 + RNG.nextInt(Math.max(1, maxUpdates));
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            boolean delete = RNG.nextDouble() < 0.15; // 15% deletes
            if (delete) {
                updates.put(key, null);
            } else {
                String val = "v-" + RNG.nextInt(10_000);
                updates.put(key, val.getBytes(StandardCharsets.UTF_8));
            }
        }
        return updates;
    }

    private static String buildMismatchMessage(String label,
                                               boolean including,
                                               byte[] key,
                                               byte[] root,
                                               byte[] expected,
                                               byte[] wire) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" proof mismatch")
          .append(" including=").append(including)
          .append(" key=").append(Bytes.toHex(key))
          .append(" keyHash=").append(Bytes.toHex(HASH.digest(key)))
          .append(" root=").append(Bytes.toHex(root == null ? new byte[0] : root))
          .append(" expected=").append(Bytes.toHex(expected == null ? new byte[0] : expected))
          .append("\n    wire=").append(Bytes.toHex(wire));
        return sb.toString();
    }

    private static void assertRandomProofs(SecureTrie trie, byte[] root, List<byte[]> keyPool, int queries) {
        trie.setRootHash(root);
        for (int i = 0; i < queries; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            byte[] expected = trie.get(key);
            boolean including = expected != null;
            byte[] wire = trie.getProofWire(key).orElseThrow(() -> new AssertionError("no proof for key"));

            // Use public API to verify proof
            boolean verified = MpfProofVerifier.verify(root, key, expected, including, wire, HASH, COMMITMENTS);
            if (!verified) {
                fail(buildMismatchMessage("RDBMS MPF proof verification failed", including, key, root, expected, wire));
            }

            // Also test using trie's built-in verification
            boolean ok = trie.verifyProofWire(root, key, expected, including, wire);
            if (!ok) {
                fail(buildMismatchMessage("RDBMS trie verify failed", including, key, root, expected, wire));
            }
        }
    }

    private static boolean existsMultiLevelProof(SecureTrie trie, byte[] root, List<byte[]> keyPool) {
        // A multi-level proof is indicated by a longer wire-format proof
        // Heuristic: proofs with length > 100 bytes typically have multiple levels
        int checks = Math.min(keyPool.size(), 80);
        for (int i = 0; i < checks; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            byte[] wire = trie.getProofWire(key).orElse(null);
            if (wire == null) continue;
            // Multi-level proofs are typically longer than single-level proofs
            if (wire.length > 100) return true;
        }
        return false;
    }
}

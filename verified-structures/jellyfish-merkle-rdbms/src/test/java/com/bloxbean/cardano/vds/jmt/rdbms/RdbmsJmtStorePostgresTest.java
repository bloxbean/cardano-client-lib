package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.JmtProofVerifier;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL-specific tests for {@link RdbmsJmtStore} using a locally running PostgreSQL instance.
 *
 * <p>The tests require access to a PostgreSQL server. By default they connect to:
 * <pre>
 *   host:     localhost
 *   port:     54333
 *   database: yaci_store
 *   user:     yaci
 *   password: dbpass
 *   schema:   jmt_test
 * </pre>
 *
 * <p>Override these defaults with system properties or environment variables:
 * <ul>
 *   <li>{@code -Dpostgres.host} / {@code POSTGRES_HOST}</li>
 *   <li>{@code -Dpostgres.port} / {@code POSTGRES_PORT}</li>
 *   <li>{@code -Dpostgres.database} / {@code POSTGRES_DATABASE}</li>
 *   <li>{@code -Dpostgres.user} / {@code POSTGRES_USER}</li>
 *   <li>{@code -Dpostgres.password} / {@code POSTGRES_PASSWORD}</li>
 *   <li>{@code -Dpostgres.schema} / {@code POSTGRES_SCHEMA}</li>
 * </ul>
 *
 * <p><b>Note:</b> These tests are only enabled when the {@code ENABLE_POSTGRES_TESTS}
 * environment variable is set to {@code true}. This prevents CI build failures when
 * PostgreSQL is not available.
 */
@EnabledIfEnvironmentVariable(named = "ENABLE_POSTGRES_TESTS", matches = "true")
class RdbmsJmtStorePostgresTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);
    // Using fixed seed (0xCAFEBABE1234L) intentionally for reproducible test data generation
    private static final long PROPERTY_SEED = 0xCAFEBABE1234L; // NOSONAR - deterministic testing requires fixed seed

    private static final String PG_HOST = System.getProperty(
        "postgres.host", System.getenv().getOrDefault("POSTGRES_HOST", "localhost"));
    private static final int PG_PORT = Integer.parseInt(System.getProperty(
        "postgres.port", System.getenv().getOrDefault("POSTGRES_PORT", "54333")));
    private static final String PG_DATABASE = System.getProperty(
        "postgres.database", System.getenv().getOrDefault("POSTGRES_DATABASE", "yaci_store"));
    private static final String PG_USER = System.getProperty(
        "postgres.user", System.getenv().getOrDefault("POSTGRES_USER", "yaci"));
    private static final String PG_PASSWORD = System.getProperty(
        "postgres.password", System.getenv().getOrDefault("POSTGRES_PASSWORD", "dbpass"));
    private static final String PG_SCHEMA = System.getProperty(
        "postgres.schema", System.getenv().getOrDefault("POSTGRES_SCHEMA", "jmt_test"));

    private static DataSource dataSource;
    private static DbConfig dbConfig;

    @BeforeAll
    static void setUpClass() throws Exception {
        String baseJdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", PG_HOST, PG_PORT, PG_DATABASE);

        // Prepare clean schema and bootstrap tables
        try (Connection conn = DriverManager.getConnection(baseJdbcUrl, PG_USER, PG_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS " + PG_SCHEMA + " CASCADE"); // NOSONAR - PG_SCHEMA is static constant
            stmt.execute("CREATE SCHEMA " + PG_SCHEMA); // NOSONAR - PG_SCHEMA is static constant
            stmt.execute("SET search_path TO " + PG_SCHEMA); // NOSONAR - PG_SCHEMA is static constant
            executeSchema(stmt);
        }

        String jdbcUrlWithSchema = baseJdbcUrl + "?currentSchema=" + PG_SCHEMA;
        dbConfig = DbConfig.builder()
            .jdbcUrl(jdbcUrlWithSchema, PG_USER, PG_PASSWORD)
            .build();
        dataSource = dbConfig.dataSource();
    }

    @AfterAll
    static void tearDownClass() throws Exception {
        String baseJdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", PG_HOST, PG_PORT, PG_DATABASE);
        try (Connection conn = DriverManager.getConnection(baseJdbcUrl, PG_USER, PG_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS " + PG_SCHEMA + " CASCADE"); // NOSONAR - PG_SCHEMA is static constant
        }
    }

    private static void executeSchema(Statement stmt) throws Exception {
        String schemaSql = new String(
            RdbmsJmtStorePostgresTest.class.getResourceAsStream(
                "/schema/jmt/postgres/V1__jmt_base_schema.sql"
            ).readAllBytes(),
            StandardCharsets.UTF_8
        );
        stmt.execute(schemaSql);
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE jmt_stale, jmt_values, jmt_nodes, jmt_roots, jmt_latest CASCADE");
        }
    }

    @Test
    void postgresBasicOperations() {
        try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            updates.put(bytes("bob"), bytes("200"));

            JellyfishMerkleTree.CommitResult v1 = tree.put(1, updates);

            assertArrayEquals(v1.rootHash(), store.rootHash(1).orElseThrow(),
                "PostgreSQL should persist root hash correctly");

            byte[] aliceHash = HASH.digest(bytes("alice"));
            assertArrayEquals(bytes("100"), store.getValue(aliceHash).orElseThrow(),
                "PostgreSQL should persist values correctly");
        }
    }

    @Test
    void postgresNamespaceIsolation() {
        try (RdbmsJmtStore store1 = new RdbmsJmtStore(dbConfig, (byte) 0x01);
             RdbmsJmtStore store2 = new RdbmsJmtStore(dbConfig, (byte) 0x02)) {

            JellyfishMerkleTree tree1 = new JellyfishMerkleTree(store1, COMMITMENTS, HASH);
            JellyfishMerkleTree tree2 = new JellyfishMerkleTree(store2, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates1 = new LinkedHashMap<>();
            updates1.put(bytes("alice"), bytes("100"));
            JellyfishMerkleTree.CommitResult v1 = tree1.put(1, updates1);

            Map<byte[], byte[]> updates2 = new LinkedHashMap<>();
            updates2.put(bytes("alice"), bytes("200"));
            JellyfishMerkleTree.CommitResult v2 = tree2.put(1, updates2);

            assertFalse(Arrays.equals(v1.rootHash(), v2.rootHash()),
                "PostgreSQL namespaces should be isolated");

            byte[] aliceHash = HASH.digest(bytes("alice"));
            assertArrayEquals(bytes("100"), store1.getValue(aliceHash).orElseThrow());
            assertArrayEquals(bytes("200"), store2.getValue(aliceHash).orElseThrow());
        }
    }

    @Test
    void postgresPruningWorks() {
        try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> v1 = new LinkedHashMap<>();
            v1.put(bytes("alice"), bytes("100"));
            tree.put(1, v1);

            Map<byte[], byte[]> v2 = new LinkedHashMap<>();
            v2.put(bytes("alice"), bytes("150"));
            tree.put(2, v2);

            Map<byte[], byte[]> v3 = new LinkedHashMap<>();
            v3.put(bytes("bob"), bytes("200"));
            tree.put(3, v3);

            assertFalse(store.staleNodesUpTo(3).isEmpty(),
                "PostgreSQL should track stale nodes");

            int pruned = store.pruneUpTo(3);
            assertTrue(pruned > 0, "PostgreSQL pruning should remove nodes");
            assertTrue(store.staleNodesUpTo(3).isEmpty(),
                "PostgreSQL should clear stale markers after pruning");
        }
    }

    @Test
    void postgresTransactionRollback() throws Exception {
        try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    throw new RuntimeException("Simulated failure");
                } catch (RuntimeException e) {
                    conn.rollback();
                }
            }

            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("test"), bytes("value"));
            updates.put(bytes("test1"), bytes("value2"));

            JellyfishMerkleTree.CommitResult result = tree.put(1, updates);
            assertNotNull(result.rootHash(),
                "PostgreSQL store should remain usable after rollback");
        }
    }

    @Test
    void postgresPropertyStyleRandomized() throws Exception {
        Random keyRng = new Random(PROPERTY_SEED);
        List<byte[]> keyPool = generateKeys(150, keyRng);
        int versions = 30;
        int maxUpdatesPerVersion = 12;
        int queriesPerVersion = 20;

        try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);
            Random updateRng = new Random(PROPERTY_SEED ^ 0x55AAFF00L);

            List<JellyfishMerkleTree.CommitResult> snapshots = new ArrayList<>();

            for (long v = 0; v < versions; v++) {
                Map<byte[], byte[]> updates = randomUpdates(keyPool, maxUpdatesPerVersion, updateRng);
                JellyfishMerkleTree.CommitResult result = tree.put(v, updates);
                snapshots.add(result);

                assertArrayEquals(result.rootHash(), store.rootHash(result.version()).orElseThrow(),
                    "Root hash should persist for version " + v);

                assertRandomQueries(tree, store, result.version(), keyPool,
                    Math.min(queriesPerVersion, 10), PROPERTY_SEED ^ v);
            }

            JellyfishMerkleTree.CommitResult latest = snapshots.get(snapshots.size() - 1);
            assertRandomQueries(tree, store, latest.version(), keyPool, queriesPerVersion, PROPERTY_SEED ^ 0xABCDEF);

            for (int i = 0; i < Math.min(5, snapshots.size()); i++) {
                JellyfishMerkleTree.CommitResult snapshot = snapshots.get(i);
                assertRandomQueries(tree, store, snapshot.version(), keyPool,
                    Math.min(queriesPerVersion, 15), PROPERTY_SEED ^ (snapshot.version() + 0x1234));
            }
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static List<byte[]> generateKeys(int count, Random rng) {
        List<byte[]> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String s = "pg-key-" + i + "-" + rng.nextInt(1_000_000);
            keys.add(bytes(s));
        }
        return keys;
    }

    private static Map<byte[], byte[]> randomUpdates(List<byte[]> keyPool, int maxUpdates, Random rng) {
        int n = 1 + rng.nextInt(Math.max(1, maxUpdates));
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            byte[] key = keyPool.get(rng.nextInt(keyPool.size()));
            String val = "v-" + rng.nextInt(100_000);
            updates.put(key, bytes(val));
        }
        return updates;
    }

    private void assertRandomQueries(JellyfishMerkleTree tree,
                                     RdbmsJmtStore store,
                                     long version,
                                     List<byte[]> keyPool,
                                     int queries,
                                     long seed) {
        byte[] root = store.rootHash(version).orElseThrow(
            () -> new AssertionError("Missing root for version " + version));

        Random rng = new Random(seed);
        for (int i = 0; i < queries; i++) {
            byte[] key = keyPool.get(rng.nextInt(keyPool.size()));
            byte[] keyHash = HASH.digest(key);

            Optional<byte[]> treeValue = tree.get(key, version);
            Optional<byte[]> storeValue = store.getValueAt(keyHash, version);

            assertEquals(storeValue.isPresent(), treeValue.isPresent(),
                "Value presence mismatch at version " + version);
            treeValue.ifPresent(val ->
                assertArrayEquals(val, storeValue.orElseThrow(),
                    "Stored value mismatch at version " + version));

            Optional<com.bloxbean.cardano.vds.jmt.JmtProof> proofOpt = tree.getProof(key, version);
            assertTrue(proofOpt.isPresent(), "Expected proof for key at version " + version);
            com.bloxbean.cardano.vds.jmt.JmtProof proof = proofOpt.get();
            byte[] valueBytes = treeValue.orElse(null);
            assertTrue(JmtProofVerifier.verify(root, key, valueBytes, proof, HASH, COMMITMENTS),
                "Proof verification failed at version " + version);
        }
    }
}

package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProof;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test to investigate why tree.get() returns value but getProof() returns NON_INCLUSION.
 */
class RdbmsJmtDebugTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    private DbConfig dbConfig;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:test_jmt_debug_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        dbConfig = DbConfig.builder()
            .simpleJdbcUrl(jdbcUrl)
            .build();

        // Create schema
        try (Connection conn = dbConfig.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            String schema = new String(
                getClass().getResourceAsStream("/schema/jmt/h2/V1__jmt_base_schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );
            stmt.execute(schema);
        }
    }

    @Test
    void debug_simple_insert_and_query() throws Exception {
        try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            // Version 0: Insert key1
            byte[] key1 = "key1".getBytes(StandardCharsets.UTF_8);
            byte[] value1 = "value1".getBytes(StandardCharsets.UTF_8);

            Map<byte[], byte[]> updates0 = new LinkedHashMap<>();
            updates0.put(key1, value1);

            JellyfishMerkleTree.CommitResult result0 = tree.put(0L, updates0);

            System.out.println("Version 0 committed:");
            System.out.println("  Root hash: " + Bytes.toHex(result0.rootHash()));
            System.out.println("  Nodes: " + result0.nodes().size());
            System.out.println("  Stale nodes: " + result0.staleNodes().size());

            // Query at version 0
            byte[] keyHash1 = HASH.digest(key1);
            Optional<byte[]> valueFromGet = tree.get(key1, 0L);
            Optional<byte[]> valueFromStore = store.getValueAt(keyHash1, 0L);
            Optional<JmtProof> proof = tree.getProof(key1, 0L);

            System.out.println("\nQuery at version 0:");
            System.out.println("  Key: " + Bytes.toHex(key1));
            System.out.println("  Key hash: " + Bytes.toHex(keyHash1));
            System.out.println("  tree.get() returned: " + (valueFromGet.isPresent() ? Bytes.toHex(valueFromGet.get()) : "<empty>"));
            System.out.println("  store.getValueAt() returned: " + (valueFromStore.isPresent() ? Bytes.toHex(valueFromStore.get()) : "<empty>"));
            System.out.println("  Proof type: " + (proof.isPresent() ? proof.get().type() : "<no proof>"));

            if (proof.isPresent() && proof.get().value() != null) {
                System.out.println("  Proof value: " + Bytes.toHex(proof.get().value()));
            }

            // Assertions
            assertTrue(valueFromGet.isPresent(), "tree.get() should return value");
            assertTrue(valueFromStore.isPresent(), "store.getValueAt() should return value");
            assertTrue(proof.isPresent(), "Proof should be present");

            if (valueFromGet.isPresent() && proof.isPresent()) {
                assertEquals(JmtProof.ProofType.INCLUSION, proof.get().type(),
                    "Proof type should be INCLUSION when value exists");
                assertArrayEquals(value1, proof.get().value(),
                    "Proof value should match inserted value");
            }
        }
    }

    @Test
    void debug_multiple_versions() throws Exception {
        try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            byte[] key1 = "key1".getBytes(StandardCharsets.UTF_8);
            byte[] value1v0 = "value1_v0".getBytes(StandardCharsets.UTF_8);
            byte[] value1v1 = "value1_v1".getBytes(StandardCharsets.UTF_8);

            byte[] key2 = "key2".getBytes(StandardCharsets.UTF_8);
            byte[] value2v1 = "value2_v1".getBytes(StandardCharsets.UTF_8);

            // Version 0: Insert key1
            Map<byte[], byte[]> updates0 = new LinkedHashMap<>();
            updates0.put(key1, value1v0);
            tree.put(0L, updates0);

            System.out.println("Version 0: Inserted key1=" + Bytes.toHex(key1));

            // Version 1: Update key1, insert key2
            Map<byte[], byte[]> updates1 = new LinkedHashMap<>();
            updates1.put(key1, value1v1);
            updates1.put(key2, value2v1);
            tree.put(1L, updates1);

            System.out.println("Version 1: Updated key1, inserted key2=" + Bytes.toHex(key2));

            // Query key1 at version 0
            System.out.println("\n=== Query key1 at version 0 ===");
            testQuery(tree, store, key1, 0L, value1v0);

            // Query key1 at version 1
            System.out.println("\n=== Query key1 at version 1 ===");
            testQuery(tree, store, key1, 1L, value1v1);

            // Query key2 at version 0 (should not exist)
            System.out.println("\n=== Query key2 at version 0 (should not exist) ===");
            testQuery(tree, store, key2, 0L, null);

            // Query key2 at version 1
            System.out.println("\n=== Query key2 at version 1 ===");
            testQuery(tree, store, key2, 1L, value2v1);
        }
    }

    @Test
    void debug_randomized_like_property_test() throws Exception {
        // Reproduce the exact scenario from the property test
        try (RdbmsJmtStore store = new RdbmsJmtStore(dbConfig)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            // Generate keys the same way as property test
            java.util.Random rng = new java.util.Random(0xCAFE);
            java.util.List<byte[]> keyPool = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) { // Small pool for debugging
                String s = "jmt-key-" + i + "-" + rng.nextInt(1_000_000);
                keyPool.add(s.getBytes(StandardCharsets.UTF_8));
            }

            // Version 0: Random updates
            int n = 1 + rng.nextInt(3); // 1-3 updates
            Map<byte[], byte[]> updates0 = new LinkedHashMap<>();
            System.out.println("\n=== Version 0: Inserting " + n + " keys ===");
            for (int i = 0; i < n; i++) {
                byte[] key = keyPool.get(rng.nextInt(keyPool.size()));
                String val = "v-" + rng.nextInt(100_000);
                byte[] value = val.getBytes(StandardCharsets.UTF_8);
                updates0.put(key, value);
                System.out.println("  Insert: " + Bytes.toHex(key) + " -> " + val);
            }

            JellyfishMerkleTree.CommitResult result0 = tree.put(0L, updates0);
            System.out.println("Committed version 0, root=" + Bytes.toHex(result0.rootHash()));

            // Query random keys at version 0 (same as property test)
            System.out.println("\n=== Querying random keys at version 0 ===");
            for (int i = 0; i < 5; i++) {
                byte[] key = keyPool.get(rng.nextInt(keyPool.size()));
                System.out.println("\nQuery " + i + ": key=" + Bytes.toHex(key));

                byte[] keyHash = HASH.digest(key);
                Optional<byte[]> valueFromGet = tree.get(key, 0L);
                Optional<byte[]> valueFromStore = store.getValueAt(keyHash, 0L);
                Optional<JmtProof> proof = tree.getProof(key, 0L);

                System.out.println("  tree.get() returned: " + (valueFromGet.isPresent() ? "PRESENT" : "EMPTY"));
                System.out.println("  store.getValueAt() returned: " + (valueFromStore.isPresent() ? "PRESENT" : "EMPTY"));
                System.out.println("  Proof type: " + (proof.isPresent() ? proof.get().type() : "<no proof>"));

                // This should match!
                assertEquals(valueFromGet.isPresent(), valueFromStore.isPresent(),
                    "tree.get() and store.getValueAt() must agree at query " + i);

                assertTrue(proof.isPresent(), "Proof should always exist");

                if (valueFromGet.isPresent()) {
                    assertEquals(JmtProof.ProofType.INCLUSION, proof.get().type(),
                        "Proof type should be INCLUSION when value exists at query " + i);
                } else {
                    assertTrue(proof.get().type() == JmtProof.ProofType.NON_INCLUSION_EMPTY ||
                              proof.get().type() == JmtProof.ProofType.NON_INCLUSION_DIFFERENT_LEAF,
                        "Proof should be non-inclusion when value doesn't exist at query " + i);
                }
            }
        }
    }

    private void testQuery(JellyfishMerkleTree tree, RdbmsJmtStore store, byte[] key, long version, byte[] expectedValue) {
        byte[] keyHash = HASH.digest(key);
        Optional<byte[]> valueFromGet = tree.get(key, version);
        Optional<byte[]> valueFromStore = store.getValueAt(keyHash, version);
        Optional<JmtProof> proof = tree.getProof(key, version);

        System.out.println("  Key: " + new String(key, StandardCharsets.UTF_8) + " (" + Bytes.toHex(key) + ")");
        System.out.println("  Key hash: " + Bytes.toHex(keyHash));
        System.out.println("  Expected value: " + (expectedValue == null ? "<null>" : new String(expectedValue, StandardCharsets.UTF_8)));
        System.out.println("  tree.get() returned: " + (valueFromGet.isPresent() ? new String(valueFromGet.get(), StandardCharsets.UTF_8) : "<empty>"));
        System.out.println("  store.getValueAt() returned: " + (valueFromStore.isPresent() ? new String(valueFromStore.get(), StandardCharsets.UTF_8) : "<empty>"));
        System.out.println("  Proof type: " + (proof.isPresent() ? proof.get().type() : "<no proof>"));

        if (proof.isPresent() && proof.get().value() != null) {
            System.out.println("  Proof value: " + new String(proof.get().value(), StandardCharsets.UTF_8));
        }

        // Verify consistency
        assertEquals(valueFromGet.isPresent(), valueFromStore.isPresent(),
            "tree.get() and store.getValueAt() should agree on value presence");

        if (valueFromGet.isPresent() && valueFromStore.isPresent()) {
            assertArrayEquals(valueFromGet.get(), valueFromStore.get(),
                "tree.get() and store.getValueAt() should return same value");
        }

        assertTrue(proof.isPresent(), "Proof should always be present");

        if (expectedValue != null) {
            assertTrue(valueFromGet.isPresent(), "Value should exist");
            assertArrayEquals(expectedValue, valueFromGet.get(), "Value should match expected");
            assertEquals(JmtProof.ProofType.INCLUSION, proof.get().type(),
                "Proof should be INCLUSION when value exists");
            assertArrayEquals(expectedValue, proof.get().value(),
                "Proof value should match expected");
        } else {
            assertFalse(valueFromGet.isPresent(), "Value should not exist");
            assertTrue(proof.get().type() == JmtProof.ProofType.NON_INCLUSION_EMPTY ||
                      proof.get().type() == JmtProof.ProofType.NON_INCLUSION_DIFFERENT_LEAF,
                "Proof should be non-inclusion when value doesn't exist");
        }
    }
}

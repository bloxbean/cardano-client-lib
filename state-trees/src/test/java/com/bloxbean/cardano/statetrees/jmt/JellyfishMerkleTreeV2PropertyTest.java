package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for JellyfishMerkleTreeV2.
 * Tests random sequences of operations to ensure correctness.
 */
class JellyfishMerkleTreeV2PropertyTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @Test
    void randomized_commits_match_reference_implementation() {
        long seed = 0x1C0FFEEBEEF5EEDL;
        Random random = new Random(seed);

        // Reference implementation
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        // V2 implementation
        try (JmtStore backingStore = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(backingStore, COMMITMENTS, HASH);

            Map<String, byte[]> keyBytes = new HashMap<>();      // hex -> key bytes
            Map<String, byte[]> state = new HashMap<>();         // hex -> latest value (null = absent)

            int maxVersions = 100;
            for (int version = 1; version <= maxVersions; version++) {
                // Generate random mutations
                List<Mutation> mutations = buildRandomMutations(random, state, keyBytes);

                // Apply to both implementations
                JellyfishMerkleTree.CommitResult refResult = reference.commit(version, toUpdateMap(mutations));
                JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(version, toUpdateMap(mutations));

                // Verify root hashes match
                if (!Arrays.equals(refResult.rootHash(), v2Result.rootHash())) {
                    System.err.println("Root mismatch at version " + version);
                    System.err.println("Reference=" + HexUtil.encodeHexString(refResult.rootHash()));
                    System.err.println("V2=" + HexUtil.encodeHexString(v2Result.rootHash()));
                    for (Mutation m : mutations) {
                        System.err.println("Mutation key=" + HexUtil.encodeHexString(m.key) +
                                " value=" + (m.value == null ? "null" : HexUtil.encodeHexString(m.value)));
                    }
                }
                assertArrayEquals(refResult.rootHash(), v2Result.rootHash(),
                        "root mismatch at version " + version);

                // Verify latest root hash
                assertArrayEquals(refResult.rootHash(), backingStore.latestRoot().get().rootHash(),
                        "latest root mismatch at version " + version);

                // Validate random lookups and proofs
                validateRandomLookups(random, version, reference, v2, state, keyBytes, refResult.rootHash());
            }
        } catch (Exception e) {
            fail("Unexpected exception during property test execution", e);
        }
    }

    @Test
    void sequential_inserts_and_updates_maintain_consistency() {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);
            JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

            // Insert 50 keys sequentially
            for (int i = 0; i < 50; i++) {
                Map<byte[], byte[]> updates = new LinkedHashMap<>();
                byte[] key = ("key" + i).getBytes();
                byte[] value = ("value" + i).getBytes();
                updates.put(key, value);

                JellyfishMerkleTree.CommitResult refResult = reference.commit(i, updates);
                JellyfishMerkleTreeV2.CommitResult v2Result = tree.put(i, updates);

                assertArrayEquals(refResult.rootHash(), v2Result.rootHash(),
                        "Root mismatch at version " + i);
            }

            // Update half of the keys
            for (int i = 0; i < 25; i++) {
                Map<byte[], byte[]> updates = new LinkedHashMap<>();
                byte[] key = ("key" + i).getBytes();
                byte[] value = ("updated_value" + i).getBytes();
                updates.put(key, value);

                int version = 50 + i;
                JellyfishMerkleTree.CommitResult refResult = reference.commit(version, updates);
                JellyfishMerkleTreeV2.CommitResult v2Result = tree.put(version, updates);

                assertArrayEquals(refResult.rootHash(), v2Result.rootHash(),
                        "Root mismatch at version " + version);
            }
        } catch (Exception e) {
            fail("Unexpected exception", e);
        }
    }

    @Test
    void batch_operations_match_reference() {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);
            JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

            // Large batch insert
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            for (int i = 0; i < 200; i++) {
                updates.put(("key" + i).getBytes(), ("value" + i).getBytes());
            }

            JellyfishMerkleTree.CommitResult refResult = reference.commit(0, updates);
            JellyfishMerkleTreeV2.CommitResult v2Result = tree.put(0, updates);

            assertArrayEquals(refResult.rootHash(), v2Result.rootHash(),
                    "Root hash mismatch for large batch");

            // Verify a few random proofs
            for (int i = 0; i < 10; i++) {
                byte[] key = ("key" + (i * 20)).getBytes();
                Optional<JmtProof> refProof = reference.getProof(key, 0);
                Optional<JmtProof> v2Proof = tree.getProof(key, 0);

                assertTrue(refProof.isPresent());
                assertTrue(v2Proof.isPresent());
                assertEquals(refProof.get().type(), v2Proof.get().type());
                assertArrayEquals(refProof.get().valueHash(), v2Proof.get().valueHash());
            }
        } catch (Exception e) {
            fail("Unexpected exception", e);
        }
    }

    // ===== Helper Methods =====

    private static class Mutation {
        final byte[] key;
        final byte[] value; // null means delete

        Mutation(byte[] key, byte[] value) {
            this.key = key;
            this.value = value;
        }
    }

    private List<Mutation> buildRandomMutations(Random random, Map<String, byte[]> state, Map<String, byte[]> keyBytes) {
        int numMutations = 1 + random.nextInt(10);
        List<Mutation> mutations = new ArrayList<>();

        for (int i = 0; i < numMutations; i++) {
            // TODO: Delete operations not yet implemented in V2 - skip for now
            // Full delete support with node collapse is planned for Phase 5
            boolean isInsert = true; // Always insert/update for now
            // boolean isInsert = state.isEmpty() || random.nextBoolean();

            if (isInsert) {
                // Insert or update
                byte[] key = randomKey(random, keyBytes);
                byte[] value = randomValue(random);
                String keyHex = HexUtil.encodeHexString(key);
                state.put(keyHex, value);
                mutations.add(new Mutation(key, value));
            } else {
                // Delete existing key
                List<String> existingKeys = new ArrayList<>(state.keySet());
                if (!existingKeys.isEmpty()) {
                    String keyHex = existingKeys.get(random.nextInt(existingKeys.size()));
                    byte[] key = keyBytes.get(keyHex);
                    state.put(keyHex, null);
                    mutations.add(new Mutation(key, null));
                }
            }
        }

        return mutations;
    }

    private byte[] randomKey(Random random, Map<String, byte[]> keyBytes) {
        // 50% chance reuse existing key, 50% new key
        if (!keyBytes.isEmpty() && random.nextBoolean()) {
            List<byte[]> keys = new ArrayList<>(keyBytes.values());
            return keys.get(random.nextInt(keys.size()));
        }

        byte[] key = new byte[20 + random.nextInt(20)];
        random.nextBytes(key);
        keyBytes.put(HexUtil.encodeHexString(key), key);
        return key;
    }

    private byte[] randomValue(Random random) {
        byte[] value = new byte[32 + random.nextInt(32)];
        random.nextBytes(value);
        return value;
    }

    private Map<byte[], byte[]> toUpdateMap(List<Mutation> mutations) {
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        for (Mutation m : mutations) {
            updates.put(m.key, m.value);
        }
        return updates;
    }

    private void validateRandomLookups(Random random, long version,
                                       JellyfishMerkleTree reference,
                                       JellyfishMerkleTreeV2 v2,
                                       Map<String, byte[]> state,
                                       Map<String, byte[]> keyBytes,
                                       byte[] expectedRoot) {
        // Sample a few keys for proof validation
        List<String> keysToCheck = new ArrayList<>(state.keySet());
        if (keysToCheck.size() > 5) {
            Collections.shuffle(keysToCheck, random);
            keysToCheck = keysToCheck.subList(0, 5);
        }

        for (String keyHex : keysToCheck) {
            byte[] key = keyBytes.get(keyHex);

            Optional<JmtProof> refProof = reference.getProof(key, version);
            Optional<JmtProof> v2Proof = v2.getProof(key, version);

            assertEquals(refProof.isPresent(), v2Proof.isPresent(),
                    "Proof presence mismatch for key " + keyHex + " at version " + version);

            if (refProof.isPresent()) {
                assertEquals(refProof.get().type(), v2Proof.get().type(),
                        "Proof type mismatch for key " + keyHex + " at version " + version);

                // Verify value hashes match
                if (refProof.get().valueHash() != null && v2Proof.get().valueHash() != null) {
                    assertArrayEquals(refProof.get().valueHash(), v2Proof.get().valueHash(),
                            "Value hash mismatch for key " + keyHex + " at version " + version);
                }
            }
        }
    }
}

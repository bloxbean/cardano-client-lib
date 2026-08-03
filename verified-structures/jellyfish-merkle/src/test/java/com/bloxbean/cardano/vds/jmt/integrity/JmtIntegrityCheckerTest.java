package com.bloxbean.cardano.vds.jmt.integrity;

import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtInternalNode;
import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.NodeKey;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmtIntegrityCheckerTest {

    @Test
    void quickAndFullChecksAcceptHealthyTree() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
        tree.put(0, Map.of(bytes("alice"), bytes("100"), bytes("bob"), bytes("200")));
        tree.put(1, Map.of(bytes("alice"), bytes("150")));

        JmtIntegrityChecker checker = new JmtIntegrityChecker(
                store, JmtProfile.classicBlake2b256V1());
        assertTrue(checker.check(JmtIntegrityMode.QUICK).healthy());
        assertTrue(checker.check(JmtIntegrityMode.FULL).healthy());
        assertTrue(checker.check(JmtIntegrityMode.FULL,
                JmtIntegrityChecker.Options.builder().allVersions(true).build()).healthy());
    }

    @Test
    void fullCheckDetectsValueCorruption() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
        byte[] key = bytes("alice");
        JellyfishMerkleTree.CommitResult result = tree.put(0, Map.of(key, bytes("100")));

        // Create a deliberately inconsistent NEW version. Reusing version 0 would now be an
        // immutable whole-batch replay and correctly leave the committed value untouched.
        try (JmtStore.CommitBatch batch = store.beginCommit(1, JmtStore.CommitConfig.defaults())) {
            batch.putValue(Blake2b256.digest(key), bytes("tampered"));
            batch.setRootHash(result.rootHash());
            batch.commit();
        }

        JmtIntegrityReport report = new JmtIntegrityChecker(
                store, JmtProfile.classicBlake2b256V1()).check(JmtIntegrityMode.FULL);
        assertFalse(report.healthy());
        assertTrue(report.issues().stream()
                .anyMatch(issue -> issue.code().equals("LEAF_VALUE_HASH_MISMATCH")));
    }

    @Test
    void fullCheckDetectsRootWithoutTree() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        new JellyfishMerkleTree(store);
        try (JmtStore.CommitBatch batch = store.beginCommit(0, JmtStore.CommitConfig.defaults())) {
            batch.setRootHash(Blake2b256.digest(bytes("fake-root")));
            batch.commit();
        }

        JmtIntegrityReport report = new JmtIntegrityChecker(
                store, JmtProfile.classicBlake2b256V1()).check(JmtIntegrityMode.FULL);
        assertFalse(report.healthy());
        assertTrue(report.issues().stream()
                .anyMatch(issue -> issue.code().equals("ROOT_COMMITMENT_MISMATCH")));
    }

    @Test
    void boundedAndCancelledChecksAreNotReportedHealthy() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
        tree.put(0, Map.of(bytes("alice"), bytes("100")));
        JmtIntegrityChecker checker = new JmtIntegrityChecker(
                store, JmtProfile.classicBlake2b256V1());

        JmtIntegrityReport truncated = checker.check(JmtIntegrityMode.FULL,
                JmtIntegrityChecker.Options.builder().maxRecords(1).build());
        assertTrue(truncated.truncated());
        assertFalse(truncated.healthy());

        AtomicBoolean cancel = new AtomicBoolean(true);
        JmtIntegrityReport cancelled = checker.check(JmtIntegrityMode.FULL,
                JmtIntegrityChecker.Options.builder().cancellation(cancel::get).build());
        assertTrue(cancelled.cancelled());
        assertFalse(cancelled.healthy());
    }

    @Test
    void stableProfileRejectsUncommittedCompressedPathMetadata() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JmtProfile profile = JmtProfile.classicBlake2b256V1();
        new JellyfishMerkleTree(store, profile);
        JmtInternalNode malformed = JmtInternalNode.of(0, new byte[0][], new byte[]{0x00});
        byte[] root = profile.commitmentScheme().commitBranch(
                NibblePath.EMPTY, new byte[16][]);
        try (JmtStore.CommitBatch batch = store.beginCommit(0, JmtStore.CommitConfig.defaults())) {
            batch.putNode(NodeKey.of(NibblePath.EMPTY, 0), malformed);
            batch.setRootHash(root);
            batch.commit();
        }

        JmtIntegrityReport report = new JmtIntegrityChecker(store, profile)
                .check(JmtIntegrityMode.FULL);
        assertFalse(report.healthy());
        assertTrue(report.issues().stream()
                .anyMatch(issue -> issue.code().equals("UNSUPPORTED_COMPRESSED_PATH")));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}

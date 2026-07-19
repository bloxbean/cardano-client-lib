package com.bloxbean.cardano.vds.jmt.rocksdb;

import com.bloxbean.cardano.client.test.ByteArrayWrapper;
import com.bloxbean.cardano.client.test.vds.MpfArbitraries;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProof;
import com.bloxbean.cardano.vds.jmt.JmtProofVerifier;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test asserting the RocksDB backend agrees with the in-memory reference on both
 * root hashes and proof verification, for random workloads. Guards against backend divergence
 * introduced by the storage fixes (NodeKey ordering, iterator scans, etc.).
 */
class RocksDbJmtPropertyTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    private Path dbDir;

    @BeforeTry
    void setUp() throws Exception {
        dbDir = Files.createTempDirectory("jmt-prop-rocksdb");
    }

    @AfterTry
    void tearDown() throws Exception {
        if (dbDir != null) {
            try (java.util.stream.Stream<Path> paths = Files.walk(dbDir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Provide
    Arbitrary<List<Map.Entry<byte[], byte[]>>> entries() {
        return MpfArbitraries.trieKeyValues(1, 40);
    }

    @Property(tries = 40)
    void rocksDbAgreesWithInMemory(@ForAll("entries") List<Map.Entry<byte[], byte[]>> raw) {
        Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(raw);
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
            updates.put(e.getKey().getData(), e.getValue());
        }

        byte[] memRoot;
        try (InMemoryJmtStore mem = new InMemoryJmtStore()) {
            JellyfishMerkleTree memTree = new JellyfishMerkleTree(mem);
            memRoot = memTree.put(1L, updates).rootHash();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try (RocksDbJmtStore rocks = new RocksDbJmtStore(dbDir.resolve("db").toString())) {
            JellyfishMerkleTree rocksTree = new JellyfishMerkleTree(rocks);
            byte[] rocksRoot = rocksTree.put(1L, updates).rootHash();

            assertArrayEquals(memRoot, rocksRoot, "RocksDB root must match in-memory root");

            for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
                byte[] key = e.getKey().getData();
                byte[] value = e.getValue();
                assertArrayEquals(value, rocksTree.get(key).orElse(null));

                JmtProof proof = rocksTree.getProof(key, 1L).orElseThrow();
                assertEquals(JmtProof.ProofType.INCLUSION, proof.type());
                assertTrue(JmtProofVerifier.verify(rocksRoot, key, value, proof, HASH, COMMITMENTS),
                        "RocksDB-generated inclusion proof must verify");
            }
        }
    }
}

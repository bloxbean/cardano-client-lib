package com.bloxbean.cardano.statetrees.rocksdb;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.SparseMerkleProof;
import com.bloxbean.cardano.statetrees.api.SparseMerkleVerifier;
import com.bloxbean.cardano.statetrees.api.SparseMerkleTree;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.rocksdb.resources.RocksDbInitializer;
import org.junit.jupiter.api.Test;
import org.rocksdb.*;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SmtRocksDbIntegrationTest {

  private final HashFunction blake2b = Blake2b256::digest;

  @Test
  void smtBatchCommit_persistsNodesAndRoot() throws Exception {
    Path tmp = Files.createTempDirectory("smt-rocksdb-test");

    try (RocksDbInitializer.Result result = RocksDbInitializer.builder(tmp.toString())
        .withRequiredColumnFamily(RocksDbNodeStore.CF_NODES)
        .withRequiredColumnFamily(RocksDbRootsIndex.CF_ROOTS)
        .initialize()) {

      RocksDB db = result.getDatabase();
      ColumnFamilyHandle cfNodes = result.getColumnFamily(RocksDbNodeStore.CF_NODES);
      ColumnFamilyHandle cfRoots = result.getColumnFamily(RocksDbRootsIndex.CF_ROOTS);

      RocksDbNodeStore nodeStore = new RocksDbNodeStore(db, cfNodes);
      RocksDbRootsIndex rootsIndex = new RocksDbRootsIndex(db, cfRoots);
      SparseMerkleTree smt = new SparseMerkleTree(nodeStore, blake2b);

      try (WriteBatch batch = new WriteBatch(); WriteOptions wopts = new WriteOptions()) {
        // Nest both withBatch to stage writes into the same batch
        nodeStore.withBatch(batch, () ->
          rootsIndex.withBatch(batch, () -> {
            byte[] k1 = "alice".getBytes(); byte[] v1 = "100".getBytes();
            byte[] k2 = "bob".getBytes();   byte[] v2 = "200".getBytes();

            smt.put(k1, v1);
            smt.put(k2, v2);

            // read-your-writes should work inside batch
            assertThat(smt.get(k1)).isEqualTo(v1);
            assertThat(smt.get(k2)).isEqualTo(v2);

            long next = rootsIndex.nextVersion();
            rootsIndex.put(next, smt.getRootHash());
            return null;
          })
        );

        // Commit the batch atomically
        db.write(wopts, batch);
      }

      // Verify persisted state outside batch
      byte[] latest = rootsIndex.latest();
      assertThat(latest).isNotNull();

      SparseMerkleTree reloaded = new SparseMerkleTree(nodeStore, blake2b, latest);
      byte[] k1 = "alice".getBytes();
      byte[] v1 = "100".getBytes();
      byte[] k2 = "bob".getBytes();
      byte[] v2 = "200".getBytes();

      assertThat(reloaded.get(k1)).isEqualTo(v1);
      assertThat(reloaded.get(k2)).isEqualTo(v2);

      // Inclusion proofs for present keys
      SparseMerkleProof p1 = reloaded.getProof(k1);
      SparseMerkleProof p2 = reloaded.getProof(k2);
      assertThat(SparseMerkleVerifier.verifyInclusion(latest, blake2b, k1, v1, p1.getSiblings())).isTrue();
      assertThat(SparseMerkleVerifier.verifyInclusion(latest, blake2b, k2, v2, p2.getSiblings())).isTrue();

      // Non-inclusion proof for an absent key
      byte[] absent = "carol".getBytes();
      SparseMerkleProof p3 = reloaded.getProof(absent);
      assertThat(SparseMerkleVerifier.verifyNonInclusion(latest, blake2b, absent, p3.getSiblings())).isTrue();
    }
  }

  @Test
  void smtProofsAcrossReopen_verifyWithFreshInstance() throws Exception {
    Path tmp = Files.createTempDirectory("smt-rocksdb-test2");

    byte[] latest;

    // Phase 1: write and persist root
    try (RocksDbInitializer.Result result = RocksDbInitializer.builder(tmp.toString())
        .withRequiredColumnFamily(RocksDbNodeStore.CF_NODES)
        .withRequiredColumnFamily(RocksDbRootsIndex.CF_ROOTS)
        .initialize()) {

      RocksDB db = result.getDatabase();
      ColumnFamilyHandle cfNodes = result.getColumnFamily(RocksDbNodeStore.CF_NODES);
      ColumnFamilyHandle cfRoots = result.getColumnFamily(RocksDbRootsIndex.CF_ROOTS);

      RocksDbNodeStore nodeStore = new RocksDbNodeStore(db, cfNodes);
      RocksDbRootsIndex rootsIndex = new RocksDbRootsIndex(db, cfRoots);
      SparseMerkleTree smt = new SparseMerkleTree(nodeStore, blake2b);

      try (WriteBatch batch = new WriteBatch(); WriteOptions wopts = new WriteOptions()) {
        nodeStore.withBatch(batch, () ->
          rootsIndex.withBatch(batch, () -> {
            smt.put("alice".getBytes(), "100".getBytes());
            smt.put("bob".getBytes(),   "200".getBytes());
            long next = rootsIndex.nextVersion();
            rootsIndex.put(next, smt.getRootHash());
            return null;
          })
        );
        db.write(wopts, batch);
      }

      latest = rootsIndex.latest();
      assertThat(latest).isNotNull();
    }

    // Phase 2: reopen DB fresh and verify proofs
    try (RocksDbInitializer.Result result = RocksDbInitializer.builder(tmp.toString())
        .withRequiredColumnFamily(RocksDbNodeStore.CF_NODES)
        .withRequiredColumnFamily(RocksDbRootsIndex.CF_ROOTS)
        .initialize()) {

      RocksDB db = result.getDatabase();
      ColumnFamilyHandle cfNodes = result.getColumnFamily(RocksDbNodeStore.CF_NODES);
      ColumnFamilyHandle cfRoots = result.getColumnFamily(RocksDbRootsIndex.CF_ROOTS);

      RocksDbNodeStore nodeStore = new RocksDbNodeStore(db, cfNodes);
      RocksDbRootsIndex rootsIndex = new RocksDbRootsIndex(db, cfRoots);

      // Recreate SMT view at persisted root
      SparseMerkleTree smt = new SparseMerkleTree(nodeStore, blake2b, latest);

      byte[] k1 = "alice".getBytes();
      byte[] v1 = "100".getBytes();
      byte[] k2 = "bob".getBytes();
      byte[] v2 = "200".getBytes();

      // Values visible after reopen
      assertThat(smt.get(k1)).isEqualTo(v1);
      assertThat(smt.get(k2)).isEqualTo(v2);

      // Generate and verify proofs in fresh instance
      SparseMerkleProof p1 = smt.getProof(k1);
      SparseMerkleProof p2 = smt.getProof(k2);
      assertThat(SparseMerkleVerifier.verifyInclusion(latest, blake2b, k1, v1, p1.getSiblings())).isTrue();
      assertThat(SparseMerkleVerifier.verifyInclusion(latest, blake2b, k2, v2, p2.getSiblings())).isTrue();

      byte[] absent = "carol".getBytes();
      SparseMerkleProof p3 = smt.getProof(absent);
      assertThat(SparseMerkleVerifier.verifyNonInclusion(latest, blake2b, absent, p3.getSiblings())).isTrue();
    }
  }
}

package com.bloxbean.cardano.statetrees.jmt.bench;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.JmtProofVerifier;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.mpf.MpfProof;
import com.bloxbean.cardano.statetrees.jmt.mpf.MpfProofDecoder;
import com.bloxbean.cardano.statetrees.jmt.mpf.MpfProofVerifier;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@Fork(1)
public class JmtBenchmark {

  @Param({"1000"})
  public int initialSize;

  private final HashFunction hash = Blake2b256::digest;
  private final CommitmentScheme commitments = new MpfCommitmentScheme(hash);

  private JellyfishMerkleTree tree;
  private List<byte[]> rawKeys;
  private List<byte[]> rawValues;
  private byte[] root;
  private int cursor;
  private long version;

  @Setup(Level.Trial)
  public void setUp() {
    tree = new JellyfishMerkleTree(commitments, hash);
    rawKeys = new ArrayList<>(initialSize);
    rawValues = new ArrayList<>(initialSize);
    Map<byte[], byte[]> updates = new LinkedHashMap<>();
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < initialSize; i++) {
      byte[] key = new byte[32];
      random.nextBytes(key);
      byte[] value = new byte[64];
      random.nextBytes(value);
      rawKeys.add(key);
      rawValues.add(value);
      updates.put(key, value);
    }
    tree.commit(1L, updates);
    root = tree.latestRootHash();
    version = 1L;
    cursor = 0;
  }

  @Benchmark
  public void get(Blackhole bh) {
    byte[] key = rawKeys.get(nextIndex());
    bh.consume(tree.get(key));
  }

  @Benchmark
  public void getProof(Blackhole bh) {
    byte[] key = rawKeys.get(nextIndex());
    Optional<JmtProof> proof = tree.getProof(key, version);
    bh.consume(proof.orElseThrow(() -> new IllegalStateException("Missing proof")));
  }

  @Benchmark
  public void getMpfProof(Blackhole bh) {
    byte[] key = rawKeys.get(nextIndex());
    Optional<byte[]> proof = tree.getMpfProofCbor(key, version);
    bh.consume(proof.orElseThrow(() -> new IllegalStateException("Missing MPF proof")));
  }

  @Benchmark
  public void verifyProof(Blackhole bh) {
    int idx = nextIndex();
    byte[] key = rawKeys.get(idx);
    byte[] value = rawValues.get(idx);
    Optional<JmtProof> proof = tree.getProof(key, version);
    boolean ok = JmtProofVerifier.verify(root, key, value, proof.orElseThrow(() -> new IllegalStateException("Missing proof")), hash, commitments);
    bh.consume(ok);
  }

  @Benchmark
  public void verifyMpfProof(Blackhole bh) {
    int idx = nextIndex();
    byte[] key = rawKeys.get(idx);
    byte[] value = rawValues.get(idx);
    byte[] proof = tree.getMpfProofCbor(key, version).orElseThrow(() -> new IllegalStateException("Missing MPF proof"));
    boolean ok = MpfProofVerifier.verify(root, key, value, true, proof, hash, commitments);
    bh.consume(ok);
  }

  @Benchmark
  public void verifyMpfProofDecoded(Blackhole bh) {
    int idx = nextIndex();
    byte[] key = rawKeys.get(idx);
    byte[] value = rawValues.get(idx);
    byte[] proof = tree.getMpfProofCbor(key, version).orElseThrow(() -> new IllegalStateException("Missing MPF proof"));
    MpfProof decoded = MpfProofDecoder.decode(proof);
    bh.consume(decoded.computeRoot(key, value, true, hash, commitments));
  }

  @Benchmark
  public void commitSingleUpdate(Blackhole bh) {
    int idx = nextIndex();
    byte[] key = rawKeys.get(idx);
    byte[] newValue = new byte[64];
    ThreadLocalRandom.current().nextBytes(newValue);
    Map<byte[], byte[]> updates = new LinkedHashMap<>(1);
    updates.put(key, newValue);
    JellyfishMerkleTree.CommitResult result = tree.commit(++version, updates);
    rawValues.set(idx, newValue);
    root = result.rootHash();
    bh.consume(root);
  }

  private int nextIndex() {
    if (rawKeys.isEmpty()) {
      throw new IllegalStateException("Benchmark keys not initialised");
    }
    int idx = cursor;
    cursor = (cursor + 1) % rawKeys.size();
    return idx;
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}
